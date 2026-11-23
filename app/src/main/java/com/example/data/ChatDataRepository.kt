package com.example.data

import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.FakeCallRecord
import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.data.model.TickStatus
import com.example.data.model.UserProfile
import com.example.data.model.WallpaperTheme
import com.example.media.ParsedWhatsAppChat
import com.example.media.ParsedWhatsAppMessage
import kotlinx.coroutines.flow.Flow

interface ChatDataRepository {
    suspend fun ensureUserProfile(displayName: String, email: String?): Result<Unit>
    fun observeUserProfile(userId: String): Flow<UserProfile?>
    suspend fun updateDefaultMyName(userId: String, defaultMyName: String): Result<Unit>

    fun observeChats(userId: String): Flow<List<FakeChat>>
    fun observeChat(userId: String, chatId: String): Flow<FakeChat?>

    suspend fun createChat(
        userId: String,
        contactName: String,
        groupChat: Boolean,
        statusText: String,
        online: Boolean,
        verified: Boolean,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        wallpaperTheme: WallpaperTheme,
        unreadCount: Long = 0L,
        pinned: Boolean = false,
    ): Result<String>

    suspend fun createSampleConversation(userId: String): Result<String>

    suspend fun updateChatMeta(
        userId: String,
        chatId: String,
        contactName: String? = null,
        groupChat: Boolean? = null,
        statusText: String? = null,
        online: Boolean? = null,
        verified: Boolean? = null,
        avatarEmoji: String? = null,
        avatarColorHex: String? = null,
        avatarImageBase64: String? = null,
        clearAvatarPhoto: Boolean = false,
        wallpaperTheme: WallpaperTheme? = null,
        unreadCount: Long? = null,
        pinned: Boolean? = null,
    ): Result<Unit>

    suspend fun deleteChat(userId: String, chatId: String): Result<Unit>

    fun observeMessages(userId: String, chatId: String): Flow<List<FakeMessage>>

    suspend fun sendMessage(
        userId: String,
        chatId: String,
        senderRole: SenderRole,
        senderName: String,
        text: String,
        messageType: MessageType,
        displayTime: String,
        dateLabel: String,
        tickStatus: TickStatus,
        orderIndex: Long,
        voiceDuration: String? = null,
        mediaPreviewBase64: String? = null,
        localMediaPath: String? = null,
        mediaFileName: String? = null,
        mediaDuration: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null,
    ): Result<Unit>

    suspend fun updateMessage(
        userId: String,
        chatId: String,
        messageId: String,
        text: String,
        displayTime: String,
        dateLabel: String,
        senderRole: SenderRole,
        senderName: String,
        tickStatus: TickStatus,
        reactionEmoji: String?,
        messageType: MessageType? = null,
        mediaPreviewBase64: String? = null,
        localMediaPath: String? = null,
        mediaFileName: String? = null,
        mediaDuration: String? = null,
        clearMedia: Boolean = false,
    ): Result<Unit>

    suspend fun deleteMessage(userId: String, chatId: String, messageId: String): Result<Unit>

    fun observeFakeCalls(userId: String): Flow<List<FakeCallRecord>>

    suspend fun addFakeCallRecord(
        userId: String,
        contactName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        callType: CallType,
        direction: CallDirection,
        durationText: String,
        displayTime: String,
    ): Result<Unit>

    suspend fun deleteFakeCallRecord(userId: String, callId: String): Result<Unit>

    suspend fun exportWorkspaceToJson(userId: String): Result<String>
    suspend fun importWorkspaceFromJson(userId: String, jsonString: String): Result<Int>
    suspend fun importWhatsAppChat(
        userId: String,
        parsed: ParsedWhatsAppChat,
        chatTitle: String,
        myParticipantName: String?,
        isGroup: Boolean
    ): Result<Pair<String, Int>>

    suspend fun importWhatsAppMessagesIntoChat(
        userId: String,
        chatId: String,
        parsedMessages: List<ParsedWhatsAppMessage>,
        myParticipantName: String?,
        replaceExisting: Boolean = true
    ): Result<Int>
}
