package com.example

import com.example.media.WhatsAppTxtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppTxtParserTest {

    @Test
    fun parsesAndroidSpanishExportWithMultilineAndSystemMessages() {
        val sample = """
            21/11/24, 14:30 - Los mensajes y las llamadas están cifrados de extremo a extremo.
            21/11/24, 14:32 - Juan Pérez: Hola!
            ¿Cómo estás hoy?
            21/11/24, 14:33 - Pablo: Todo genial 😎
        """.trimIndent()

        val result = WhatsAppTxtParser.parseText(sample, "Chat de WhatsApp con Juan Pérez.txt")
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Juan Pérez", parsed.suggestedTitle)
        assertFalse(parsed.isLikelyGroup)
        assertEquals(listOf("Juan Pérez", "Pablo"), parsed.participants)
        assertEquals(3, parsed.messages.size)
        assertTrue(parsed.messages[0].isSystem)
        assertEquals("Hola!\n¿Cómo estás hoy?", parsed.messages[1].text)
        assertEquals("14:33", parsed.messages[2].timeLabel)
    }

    @Test
    fun parsesIosBracketFormatWithSecondsAndGroup() {
        val sample = """
            [21/11/2024, 9:05:12 AM] Ana: Buenos días equipo
            [21/11/2024, 9:06:45 AM] Carlos: Buen día!
            [21/11/2024, 9:07:01 AM] Lucía: ¡Vamos allá!
        """.trimIndent()

        val result = WhatsAppTxtParser.parseText(sample, "Grupo Viaje.txt")
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Grupo Viaje", parsed.suggestedTitle)
        assertTrue(parsed.isLikelyGroup)
        assertEquals(3, parsed.participants.size)
        assertEquals("9:05 AM", parsed.messages[0].timeLabel)
    }
}
