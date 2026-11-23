package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalChatDatabase
import com.example.data.local.LocalChatMockRepository
import com.example.data.model.SenderRole
import com.example.media.WhatsAppTxtParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealWhatsAppImportTest {

    private lateinit var context: Context
    private lateinit var db: LocalChatDatabase
    private lateinit var repo: LocalChatMockRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LocalChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LocalChatMockRepository(db.chatDao(), LocalChatMockRepository.DEFAULT_GUEST_USER_ID)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun parsesAndImportsWhatsAppExportEndToEnd() = runTest {
        val sampleExport = """
            31/12/2025, 23:55 - Cambió tu código de seguridad con Lucía. Toca para más información.
            31/12/2025, 23:58 - Pablo De Los Ríos: ¿Todo listo para mañana?
            1/1/2026, 00:44 - Lucía Gómez: ¡Sí! Nos vemos allí
            Un abrazo grande!
        """.trimIndent()

        val parsed = WhatsAppTxtParser.parseText(
            rawText = sampleExport,
            fileName = "Chat de WhatsApp con Lucía Gómez.txt"
        ).getOrThrow()

        assertEquals("Lucía Gómez", parsed.suggestedTitle)
        assertFalse(parsed.isLikelyGroup)
        assertFalse(parsed.wasTruncated)
        assertEquals(3, parsed.messages.size)
        assertEquals(2, parsed.participants.size)
        assertTrue(parsed.participants.contains("Lucía Gómez"))
        assertTrue(parsed.participants.contains("Pablo De Los Ríos"))

        val last = parsed.messages.last()
        assertEquals("1/1/2026", last.dateLabel)
        assertEquals("00:44", last.timeLabel)
        assertEquals("Lucía Gómez", last.senderName)
        assertTrue(last.text.contains("Un abrazo grande!"))

        assertTrue(
            parsed.messages.any {
                it.isSystem && it.text.contains("Cambió tu código de seguridad")
            }
        )

        val (chatId, importedCount) = repo.importWhatsAppChat(
            userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
            parsed = parsed,
            chatTitle = parsed.suggestedTitle,
            myParticipantName = "Pablo De Los Ríos",
            isGroup = parsed.isLikelyGroup
        ).getOrThrow()

        assertEquals(3, importedCount)

        val chats = repo.observeChats(LocalChatMockRepository.DEFAULT_GUEST_USER_ID).first()
        assertEquals(1, chats.size)
        assertEquals("Lucía Gómez", chats.first().contactName)
        assertEquals("00:44", chats.first().lastMessageTime)
        assertEquals(SenderRole.OTHER, chats.first().parsedLastSender)

        val storedMessages = repo.observeMessages(
            userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
            chatId = chatId
        ).first()
        assertEquals(3, storedMessages.size)
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.ME && it.senderName == "Pablo De Los Ríos" })
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.OTHER && it.senderName == "Lucía Gómez" })
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.SYSTEM })
    }

    @Test
    fun parsesWhatsAppZipArchiveFromUri() = runTest {
        val tempZip = File(context.cacheDir, "Chat_de_WhatsApp_con_Carlos.zip")
        ZipOutputStream(tempZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("_chat.txt"))
            zos.write(
                """
                [23/11/26, 10:15:02] Carlos: Hola, te paso el zip
                [23/11/26, 10:16:10] Pablo: Recibido!
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }
        try {
            val parsed = WhatsAppTxtParser.parseFromUri(context, Uri.fromFile(tempZip)).getOrThrow()
            assertEquals(2, parsed.messages.size)
            assertTrue(parsed.participants.contains("Carlos"))
            assertTrue(parsed.participants.contains("Pablo"))
        } finally {
            tempZip.delete()
        }
    }

    @Test
    fun parsesTelegramAndSharedTextConversations() = runTest {
        val telegramCopy = """
            Marta, [23/11/2026 14:20]
            ¿Quedamos esta tarde?

            Pablo, [23/11/2026 14:22]
            Claro, a las 6.
        """.trimIndent()

        val parsed = WhatsAppTxtParser.parseConversationText(telegramCopy).getOrThrow()
        assertEquals(2, parsed.messages.size)
        assertEquals("Marta", parsed.participants.first())

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Chat compartido")
            putExtra(
                Intent.EXTRA_TEXT,
                """
                Ana: Hola!
                Pablo: Buenas tardes
                """.trimIndent()
            )
        }
        val fromShare = WhatsAppTxtParser.parseSharedIntent(context, shareIntent)?.getOrThrow()
        assertNotNull(fromShare)
        assertEquals(2, fromShare!!.messages.size)
    }
}
