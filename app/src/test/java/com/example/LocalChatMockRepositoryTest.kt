package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalChatDatabase
import com.example.data.local.LocalChatMockRepository
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.GroupMember
import com.example.data.model.GroupMembersCodec
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

    @Test
    fun importsTxtMessagesIntoExistingChatPreservingProfileAndSupportingReplaceOrAppend() = runTest {
        val chatId = repo.createChat(
            userId = userId,
            contactName = "Cayetana",
            groupChat = false,
            statusText = "Ocupada",
            online = false,
            verified = true,
            avatarEmoji = "CE",
            avatarColorHex = "#1565C0",
            avatarImageBase64 = "fakeAvatarBase64==",
            wallpaperTheme = WallpaperTheme.MIDNIGHT,
            unreadCount = 0L,
            pinned = true
        ).getOrThrow()

        repo.sendMessage(
            userId = userId,
            chatId = chatId,
            senderRole = SenderRole.ME,
            senderName = "Yo",
            text = "Mensaje antiguo",
            messageType = MessageType.TEXT,
            displayTime = "09:00",
            dateLabel = "AYER",
            tickStatus = TickStatus.READ,
            orderIndex = 1L
        ).getOrThrow()

        val firstTxt = WhatsAppTxtParser.parseText(
            """
            22/11/24, 18:10 - Cayetana: Hola, ¿vienes?
            22/11/24, 18:11 - Pablo: Sí, voy saliendo
            """.trimIndent()
        ).getOrThrow()

        val replacedCount = repo.importWhatsAppMessagesIntoChat(
            userId = userId,
            chatId = chatId,
            parsedMessages = firstTxt.messages,
            myParticipantName = "Pablo",
            replaceExisting = true
        ).getOrThrow()

        assertEquals(2, replacedCount)
        val afterReplace = repo.observeMessages(userId, chatId).first()
        assertEquals(2, afterReplace.size)
        assertEquals("Hola, ¿vienes?", afterReplace[0].text)
        assertEquals(SenderRole.OTHER, afterReplace[0].parsedSenderRole)
        assertEquals(SenderRole.ME, afterReplace[1].parsedSenderRole)

        val secondTxt = WhatsAppTxtParser.parseText(
            "22/11/24, 18:15 - Cayetana: Perfecto, aquí te espero"
        ).getOrThrow()

        val appendedCount = repo.importWhatsAppMessagesIntoChat(
            userId = userId,
            chatId = chatId,
            parsedMessages = secondTxt.messages,
            myParticipantName = "Pablo",
            replaceExisting = false
        ).getOrThrow()

        assertEquals(1, appendedCount)
        val afterAppend = repo.observeMessages(userId, chatId).first()
        assertEquals(3, afterAppend.size)
        assertEquals("Perfecto, aquí te espero", afterAppend[2].text)

        val preservedChat = repo.observeChat(userId, chatId).first()
        assertEquals("fakeAvatarBase64==", preservedChat?.avatarImageBase64)
        assertEquals("Ocupada", preservedChat?.statusText)
        assertEquals("Perfecto, aquí te espero", preservedChat?.lastMessagePreview)
    }

    @Test
    fun updatesOmittedMessageWithImageAndClearsMediaBackToText() = runTest {
        val chatId = repo.createChat(
            userId = userId,
            contactName = "Laura",
            groupChat = false,
            statusText = "en línea",
            online = true,
            verified = false,
            avatarEmoji = "L",
            avatarColorHex = "#00796B",
            avatarImageBase64 = null,
            wallpaperTheme = WallpaperTheme.CLASSIC,
            unreadCount = 0L,
            pinned = false
        ).getOrThrow()

        repo.sendMessage(
            userId = userId,
            chatId = chatId,
            senderRole = SenderRole.OTHER,
            senderName = "Laura",
            text = "<Multimedia omitido>",
            messageType = MessageType.TEXT,
            displayTime = "12:10",
            dateLabel = "HOY",
            tickStatus = TickStatus.READ,
            orderIndex = 1L
        ).getOrThrow()

        val initialMsg = repo.observeMessages(userId, chatId).first().single()
        assertEquals(MessageType.TEXT, initialMsg.parsedMessageType)

        repo.updateMessage(
            userId = userId,
            chatId = chatId,
            messageId = initialMsg.id,
            text = "🎞️ GIF",
            displayTime = initialMsg.displayTime,
            dateLabel = initialMsg.dateLabel,
            senderRole = SenderRole.OTHER,
            senderName = "Laura",
            tickStatus = TickStatus.READ,
            reactionEmoji = null,
            messageType = MessageType.IMAGE,
            mediaPreviewBase64 = "previewBase64==",
            localMediaPath = "/tmp/test.gif",
            mediaFileName = "funny.gif",
            mediaDuration = null,
            clearMedia = false
        ).getOrThrow()

        val replacedMsg = repo.observeMessages(userId, chatId).first().single()
        assertEquals(MessageType.IMAGE, replacedMsg.parsedMessageType)
        assertEquals("funny.gif", replacedMsg.mediaFileName)
        assertEquals("/tmp/test.gif", replacedMsg.localMediaPath)

        repo.updateMessage(
            userId = userId,
            chatId = chatId,
            messageId = initialMsg.id,
            text = "<Multimedia omitido>",
            displayTime = initialMsg.displayTime,
            dateLabel = initialMsg.dateLabel,
            senderRole = SenderRole.OTHER,
            senderName = "Laura",
            tickStatus = TickStatus.READ,
            reactionEmoji = null,
            clearMedia = true
        ).getOrThrow()

        val clearedMsg = repo.observeMessages(userId, chatId).first().single()
        assertEquals(MessageType.TEXT, clearedMsg.parsedMessageType)
        assertEquals(null, clearedMsg.localMediaPath)
        assertEquals(null, clearedMsg.mediaFileName)
    }

    @Test
    fun createsGroupFromChatsPersistsMembersAndSurvivesJsonBackup() = runTest {
        val sarahId = repo.createChat(
            userId = userId,
            contactName = "Sarah Aalvik",
            groupChat = false,
            statusText = "en línea",
            online = true,
            verified = false,
            avatarEmoji = "SA",
            avatarColorHex = "#4F46E5",
            avatarImageBase64 = null,
            wallpaperTheme = WallpaperTheme.CLASSIC,
            unreadCount = 0L,
            pinned = false
        ).getOrThrow()

        val members = listOf(
            GroupMember(name = "Tú", statusText = "9 hace minutos", avatarEmoji = "TÚ", avatarColorHex = "#00796B", isAdmin = true, isMe = true),
            GroupMember(chatId = sarahId, name = "Sarah Aalvik", statusText = "en línea", avatarEmoji = "SA", avatarColorHex = "#4F46E5", isAdmin = false, isMe = false),
            GroupMember(name = "Anna Andreiq", statusText = "20 hace minutos", avatarEmoji = "AA", avatarColorHex = "#E11D48", isAdmin = false, isMe = false)
        )
        val encoded = GroupMembersCodec.encode(members)
        val groupId = repo.createChat(
            userId = userId,
            contactName = "Wildxgs",
            groupChat = true,
            statusText = GroupMembersCodec.buildStatusSummary(members),
            online = false,
            verified = false,
            avatarEmoji = "W",
            avatarColorHex = "#00796B",
            avatarImageBase64 = null,
            wallpaperTheme = WallpaperTheme.MIDNIGHT,
            unreadCount = 0L,
            pinned = true,
            groupMembersJson = encoded
        ).getOrThrow()

        val loadedGroup = repo.observeChat(userId, groupId).first()
        assertEquals(true, loadedGroup?.groupChat)
        assertEquals(3, loadedGroup?.parsedGroupMembers?.size)
        assertEquals(true, loadedGroup?.parsedGroupMembers?.first()?.isAdmin)
        assertEquals(sarahId, loadedGroup?.parsedGroupMembers?.get(1)?.chatId)

        val backupJson = repo.exportWorkspaceToJson(userId).getOrThrow()
        repo.deleteChat(userId, groupId).getOrThrow()
        repo.deleteChat(userId, sarahId).getOrThrow()
        assertEquals(0, repo.observeChats(userId).first().size)

        val restoredCount = repo.importWorkspaceFromJson(userId, backupJson).getOrThrow()
        assertEquals(2, restoredCount)
        val restoredGroup = repo.observeChats(userId).first().first { it.groupChat }
        assertEquals(3, restoredGroup.parsedGroupMembers.size)
        assertEquals("Anna Andreiq", restoredGroup.parsedGroupMembers[2].name)
    }

    @Test
    fun whatsAppGroupImportPopulatesMembersAndLegacyStatusFallbackWorks() = runTest {
        val sample = """
            21/11/24, 14:32 - Carlos: Hola equipo
            21/11/24, 14:33 - Lucía: Todo listo
            21/11/24, 14:34 - Pablo: Genial!
        """.trimIndent()
        val parsed = WhatsAppTxtParser.parseText(sample, "Grupo Proyecto.txt").getOrThrow()
        val (chatId, _) = repo.importWhatsAppChat(
            userId = userId,
            parsed = parsed,
            chatTitle = "Proyecto",
            myParticipantName = "Pablo",
            isGroup = true
        ).getOrThrow()

        val imported = repo.observeChat(userId, chatId).first()
        val resolved = imported?.resolvedGroupMembers("Pablo").orEmpty()
        assertEquals(3, resolved.size)
        assertEquals("Tú", resolved.first().name)
        assertEquals(true, resolved.first().isAdmin)

        val legacyFallback = GroupMembersCodec.fromStatusTextFallback("Tú, Sofía, Carlos, Ana")
        assertEquals(4, legacyFallback.size)
        assertEquals(true, legacyFallback.first().isMe)
    }
}
