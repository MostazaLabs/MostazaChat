package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalChatDatabase
import com.example.data.local.LocalChatMockRepository
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.data.model.TickStatus
import com.example.data.model.WallpaperTheme
import com.example.media.WhatsAppTxtParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalChatMockRepositoryTest {

    private lateinit var db: LocalChatDatabase
    private lateinit var repo: LocalChatMockRepository
    private val userId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LocalChatMockRepository(db.chatDao(), userId)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createsChatsMessagesCallsAndExportsImportsJsonLocally() = runTest {
        repo.ensureUserProfile("Invitado local", null).getOrThrow()
        repo.updateDefaultMyName(userId, "Alex").getOrThrow()
        val profile = repo.observeUserProfile(userId).first()
        assertEquals("Alex", profile?.defaultMyName)

        val chatId = repo.createChat(
            userId = userId,
            contactName = "María",
            groupChat = false,
            statusText = "en línea",
            online = true,
            verified = true,
            avatarEmoji = "☕",
            avatarColorHex = "#00796B",
            avatarImageBase64 = null,
            wallpaperTheme = WallpaperTheme.MINT,
            unreadCount = 2L,
            pinned = true
        ).getOrThrow()

        repo.sendMessage(
            userId = userId,
            chatId = chatId,
            senderRole = SenderRole.ME,
            senderName = "Alex",
            text = "Hola María, guardado en local",
            messageType = MessageType.TEXT,
            displayTime = "10:15",
            dateLabel = "HOY",
            tickStatus = TickStatus.READ,
            orderIndex = 1L,
            voiceDuration = null,
            mediaPreviewBase64 = null,
            localMediaPath = null,
            mediaFileName = null,
            mediaDuration = null,
            replyToText = null,
            replyToSender = null
        ).getOrThrow()

        val chats = repo.observeChats(userId).first()
        assertEquals(1, chats.size)
        assertEquals("Hola María, guardado en local", chats.first().lastMessagePreview)

        val messages = repo.observeMessages(userId, chatId).first()
        assertEquals(1, messages.size)
        assertEquals("Alex", messages.first().senderName)

        repo.addFakeCallRecord(
            userId = userId,
            contactName = "María",
            avatarEmoji = "☕",
            avatarColorHex = "#00796B",
            avatarImageBase64 = null,
            callType = CallType.VIDEO,
            direction = CallDirection.INCOMING,
            durationText = "03:20",
            displayTime = "Hoy, 10:20"
        ).getOrThrow()
        assertEquals(1, repo.observeFakeCalls(userId).first().size)

        val exportedJson = repo.exportWorkspaceToJson(userId).getOrThrow()
        repo.deleteChat(userId, chatId).getOrThrow()
        assertTrue(repo.observeChats(userId).first().isEmpty())

        val restoredCount = repo.importWorkspaceFromJson(userId, exportedJson).getOrThrow()
        assertEquals(1, restoredCount)
        val restoredChats = repo.observeChats(userId).first()
        assertEquals(1, restoredChats.size)
        assertEquals(1, repo.observeMessages(userId, restoredChats.first().id).first().size)
    }

    @Test
    fun importsWhatsAppChatLocally() = runTest {
        val sample = """
            21/11/24, 14:32 - Juan: Qué pasa!
            21/11/24, 14:33 - Yo: Probando modo invitado local
        """.trimIndent()
        val parsed = WhatsAppTxtParser.parseText(sample, "Chat con Juan.txt").getOrThrow()
        val (chatId, count) = repo.importWhatsAppChat(
            userId = userId,
            parsed = parsed,
            chatTitle = "Juan",
            myParticipantName = "Yo",
            isGroup = false
        ).getOrThrow()

        assertEquals(2, count)
        val messages = repo.observeMessages(userId, chatId).first()
        assertEquals(SenderRole.OTHER, messages[0].parsedSenderRole)
        assertEquals(SenderRole.ME, messages[1].parsedSenderRole)
    }
}
