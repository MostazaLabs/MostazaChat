package com.example

import com.example.safety.SensitiveContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFilterAndReportTest {

    @Test
    fun normalize_stripsSpanishDiacriticsAndLowercases() {
        assertEquals("pornografia erotica", SensitiveContentFilter.normalize("Pornografía Erótica"))
        assertEquals("autolesion y violacion", SensitiveContentFilter.normalize("AUTOLESIÓN y Violación"))
    }

    @Test
    fun containsSensitiveContent_detectsAccentedAndPlainKeywords() {
        assertTrue(SensitiveContentFilter.containsSensitiveContent("Esto es pornografía explícita"))
        assertTrue(SensitiveContentFilter.containsSensitiveContent("Roleplay NSFW sin censura"))
        assertFalse(SensitiveContentFilter.containsSensitiveContent("Hola, ¿quedamos hoy a tomar un café?"))
        assertFalse(SensitiveContentFilter.containsSensitiveContent("Te mando los apuntes del examen"))
    }

    @Test
    fun maskIfFiltered_masksOnlyWhenFilterIsEnabled() {
        val sensitive = "Texto con contenido erótico fuerte"
        val masked = SensitiveContentFilter.maskIfFiltered(sensitive, filterEnabled = true)
        assertTrue(masked.contains("oculto por filtro de edad"))

        val unmasked = SensitiveContentFilter.maskIfFiltered(sensitive, filterEnabled = false)
        assertEquals(sensitive, unmasked)

        val safe = "Nos vemos a las 8 en la plaza"
        assertEquals(safe, SensitiveContentFilter.maskIfFiltered(safe, filterEnabled = true))
    }
}
