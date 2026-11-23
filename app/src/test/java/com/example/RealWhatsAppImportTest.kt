package com.example

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
    fun parsesAndImportsRealAttachedCayetanaWhatsAppChatEndToEnd() = runTest {
        val parsed = WhatsAppTxtParser.parseFromRawResource(
            context = context,
            rawResId = R.raw.chat_whatsapp_cayetana,
            fileName = "Chat de WhatsApp con Cayetana Oteyza.txt"
        ).getOrThrow()

        assertEquals("Cayetana Oteyza", parsed.suggestedTitle)
        assertFalse(parsed.isLikelyGroup)
        assertTrue(parsed.wasTruncated)
        assertEquals(2000, parsed.messages.size)
        assertEquals(2, parsed.participants.size)
        assertTrue(parsed.participants.contains("Cayetana Oteyza"))
        assertTrue(parsed.participants.contains("Pablo De Los Ríos"))

        val last = parsed.messages.last()
        assertEquals("1/1/2026", last.dateLabel)
        assertEquals("00:44", last.timeLabel)
        assertEquals("Cayetana Oteyza", last.senderName)
        assertTrue(last.text.contains("Un abrazo grande!"))

        assertTrue(
            parsed.messages.any {
                it.isSystem && it.text.contains("Cambió tu código de seguridad con Cayetana Oteyza")
            }
        )

        val (chatId, importedCount) = repo.importWhatsAppChat(
            userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
            parsed = parsed,
            chatTitle = parsed.suggestedTitle,
            myParticipantName = "Pablo De Los Ríos",
            isGroup = parsed.isLikelyGroup
        ).getOrThrow()

        assertEquals(2000, importedCount)

        val chats = repo.observeChats(LocalChatMockRepository.DEFAULT_GUEST_USER_ID).first()
        assertEquals(1, chats.size)
        assertEquals("Cayetana Oteyza", chats.first().contactName)
        assertEquals("00:44", chats.first().lastMessageTime)
        assertEquals(SenderRole.OTHER, chats.first().parsedLastSender)

        val storedMessages = repo.observeMessages(
            userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
            chatId = chatId
        ).first()
        assertEquals(2000, storedMessages.size)
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.ME && it.senderName == "Pablo De Los Ríos" })
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.OTHER && it.senderName == "Cayetana Oteyza" })
        assertTrue(storedMessages.any { it.parsedSenderRole == SenderRole.SYSTEM })
    }

    @Test
    fun parsesFromUriOnIoThreadUsingCopiedTempFile() = runTest {
        val tempFile = File(context.cacheDir, "Chat_de_WhatsApp_con_Cayetana_Oteyza.txt")
        context.resources.openRawResource(R.raw.chat_whatsapp_cayetana).use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            val parsed = WhatsAppTxtParser.parseFromUri(context, Uri.fromFile(tempFile)).getOrThrow()
            assertEquals(2000, parsed.messages.size)
            assertTrue(parsed.wasTruncated)
        } finally {
            tempFile.delete()
        }
    }
}
