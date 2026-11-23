package com.example.data.local

import android.content.Context
import com.example.data.ChatDataRepository
import com.example.data.model.AvatarPresets
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
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LocalChatMockRepository(
    private val dao: LocalChatDao,
    private val localUserId: String = DEFAULT_GUEST_USER_ID
) : ChatDataRepository {

    constructor(context: Context) : this(
        dao = LocalChatDatabase.getInstance(context).chatDao(),
        localUserId = DEFAULT_GUEST_USER_ID
    )

    private fun currentTimeString(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

    // --- USER PROFILE ---

    override suspend fun ensureUserProfile(displayName: String, email: String?): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val safeName = displayName.trim().ifBlank { "Invitado local" }.take(80)
            val existing = dao.getProfileOnce(localUserId)
            if (existing == null) {
                dao.upsertProfile(
                    LocalProfileEntity(
                        userId = localUserId,
                        displayName = safeName,
                        email = null,
                        defaultMyName = "Yo",
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            } else {
                dao.upsertProfile(
                    existing.copy(
                        displayName = safeName,
                        updatedAtMillis = now
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> =
        dao.observeProfile(userId).map { it?.toModel() }

    override suspend fun updateDefaultMyName(userId: String, defaultMyName: String): Result<Unit> {
        return try {
            val clean = defaultMyName.trim().ifBlank { "Yo" }.take(40)
            val now = System.currentTimeMillis()
            val existing = dao.getProfileOnce(userId)
            if (existing == null) {
                dao.upsertProfile(
                    LocalProfileEntity(
                        userId = userId,
                        displayName = "Invitado local",
                        email = null,
                        defaultMyName = clean,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            } else {
                dao.updateDefaultMyName(userId, clean, now)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- FAKE CHATS ---

    override fun observeChats(userId: String): Flow<List<FakeChat>> =
        dao.observeChats(userId).map { list -> list.map { it.toModel() } }

    override fun observeChat(userId: String, chatId: String): Flow<FakeChat?> =
        dao.observeChat(userId, chatId).map { it?.toModel() }

    override suspend fun createChat(
        userId: String,
        contactName: String,
        groupChat: Boolean,
        statusText: String,
        online: Boolean,
        verified: Boolean,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String?,
        wallpaperTheme: WallpaperTheme,
        unreadCount: Long,
        pinned: Boolean,
    ): Result<String> {
        return try {
            val safeName = contactName.trim().take(60)
            require(safeName.isNotEmpty()) { "El nombre del contacto no puede estar vacío" }
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = LocalChatEntity(
                id = chatId,
                userId = userId,
                contactName = safeName,
                groupChat = groupChat,
                statusText = statusText.trim().take(80),
                online = online,
                verified = verified,
                avatarEmoji = avatarEmoji.trim().ifBlank { "💬" }.take(16),
                avatarColorHex = avatarColorHex.trim().ifBlank { "#00796B" }.take(12),
                avatarImageBase64 = avatarImageBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 48000 },
                wallpaperTheme = wallpaperTheme.name,
                unreadCount = unreadCount.coerceIn(0L, 9999L),
                pinned = pinned,
                lastMessagePreview = "Conversación lista para editar",
                lastMessageTime = currentTimeString(),
                lastMessageSender = SenderRole.SYSTEM.name,
                lastMessageTick = TickStatus.READ.name,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertChat(entity)
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createSampleConversation(userId: String): Result<String> {
        return try {
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val chat = LocalChatEntity(
                id = chatId,
                userId = userId,
                contactName = "Sofía ☕",
                groupChat = false,
                statusText = "en línea",
                online = true,
                verified = true,
                avatarEmoji = "☕",
                avatarColorHex = "#4F46E5",
                avatarImageBase64 = null,
                wallpaperTheme = WallpaperTheme.MINT.name,
                unreadCount = 0L,
                pinned = true,
                lastMessagePreview = "¡Jajaja qué buena broma! 😂",
                lastMessageTime = "19:44",
                lastMessageSender = SenderRole.OTHER.name,
                lastMessageTick = TickStatus.READ.name,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            val messages = listOf(
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = SenderRole.OTHER.name,
                    senderName = "Sofía ☕",
                    text = "¿Te puedes creer lo que acaba de pasar en la oficina? 👀",
                    messageType = MessageType.TEXT.name,
                    displayTime = "19:40",
                    dateLabel = "HOY",
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = null,
                    replyToText = null,
                    replyToSender = null,
                    orderIndex = 1L,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = SenderRole.ME.name,
                    senderName = "Yo",
                    text = "¡Cuenta ya! 😱",
                    messageType = MessageType.TEXT.name,
                    displayTime = "19:41",
                    dateLabel = "HOY",
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = "🔥",
                    replyToText = null,
                    replyToSender = null,
                    orderIndex = 2L,
                    createdAtMillis = now + 1,
                    updatedAtMillis = now + 1
                ),
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = SenderRole.OTHER.name,
                    senderName = "Sofía ☕",
                    text = "Nota de voz simulada",
                    messageType = MessageType.VOICE.name,
                    displayTime = "19:42",
                    dateLabel = "HOY",
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = "0:18",
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = null,
                    replyToText = null,
                    replyToSender = null,
                    orderIndex = 3L,
                    createdAtMillis = now + 2,
                    updatedAtMillis = now + 2
                ),
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = SenderRole.OTHER.name,
                    senderName = "Sofía ☕",
                    text = "¡Jajaja qué buena broma! 😂",
                    messageType = MessageType.TEXT.name,
                    displayTime = "19:44",
                    dateLabel = "HOY",
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = null,
                    replyToText = "¡Cuenta ya! 😱",
                    replyToSender = "Yo",
                    orderIndex = 4L,
                    createdAtMillis = now + 3,
                    updatedAtMillis = now + 3
                )
            )
            dao.insertChatWithMessages(chat, messages)
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateChatMeta(
        userId: String,
        chatId: String,
        contactName: String?,
        groupChat: Boolean?,
        statusText: String?,
        online: Boolean?,
        verified: Boolean?,
        avatarEmoji: String?,
        avatarColorHex: String?,
        avatarImageBase64: String?,
        clearAvatarPhoto: Boolean,
        wallpaperTheme: WallpaperTheme?,
        unreadCount: Long?,
        pinned: Boolean?,
    ): Result<Unit> {
        return try {
            val current = dao.getChatOnce(userId, chatId)
                ?: return Result.failure(IllegalStateException("Conversación no encontrada"))
            val updatedPhoto = if (clearAvatarPhoto) {
                null
            } else {
                avatarImageBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 48000 }
                    ?: current.avatarImageBase64
            }
            val updated = current.copy(
                contactName = contactName?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: current.contactName,
                groupChat = groupChat ?: current.groupChat,
                statusText = statusText?.trim()?.take(80) ?: current.statusText,
                online = online ?: current.online,
                verified = verified ?: current.verified,
                avatarEmoji = avatarEmoji?.trim()?.takeIf { it.isNotEmpty() }?.take(16) ?: current.avatarEmoji,
                avatarColorHex = avatarColorHex?.trim()?.takeIf { it.isNotEmpty() }?.take(12) ?: current.avatarColorHex,
                avatarImageBase64 = updatedPhoto,
                wallpaperTheme = wallpaperTheme?.name ?: current.wallpaperTheme,
                unreadCount = unreadCount?.coerceIn(0L, 9999L) ?: current.unreadCount,
                pinned = pinned ?: current.pinned,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.updateChat(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(userId: String, chatId: String): Result<Unit> {
        return try {
            dao.deleteChat(userId, chatId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- FAKE MESSAGES ---

    override fun observeMessages(userId: String, chatId: String): Flow<List<FakeMessage>> =
        dao.observeMessages(userId, chatId).map { list -> list.map { it.toModel() } }

    override suspend fun sendMessage(
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
        voiceDuration: String?,
        mediaPreviewBase64: String?,
        localMediaPath: String?,
        mediaFileName: String?,
        mediaDuration: String?,
        replyToText: String?,
        replyToSender: String?,
    ): Result<Unit> {
        return try {
            val safeText = text.trim().take(2000)
            require(safeText.isNotEmpty()) { "El mensaje no puede estar vacío" }
            val safeTime = displayTime.trim().ifBlank { currentTimeString() }.take(24)
            val safeDate = dateLabel.trim().ifBlank { "HOY" }.take(40)
            val safeSenderName = senderName.trim().take(60)
            val now = System.currentTimeMillis()

            val msg = LocalMessageEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                chatId = chatId,
                senderRole = senderRole.name,
                senderName = safeSenderName,
                text = safeText,
                messageType = messageType.name,
                displayTime = safeTime,
                dateLabel = safeDate,
                tickStatus = tickStatus.name,
                voiceDuration = voiceDuration?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                mediaPreviewBase64 = mediaPreviewBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120000 },
                localMediaPath = localMediaPath?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                mediaFileName = mediaFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
                mediaDuration = mediaDuration?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                reactionEmoji = null,
                replyToText = replyToText?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                replyToSender = replyToSender?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                orderIndex = orderIndex.coerceIn(0L, 1_000_000_000L),
                createdAtMillis = now,
                updatedAtMillis = now
            )

            val preview = when (messageType) {
                MessageType.VOICE -> "🎤 Nota de voz (${voiceDuration ?: "0:12"})"
                MessageType.IMAGE -> if (safeText == "📷 Foto") "📷 Foto" else "📷 $safeText"
                MessageType.VIDEO -> if (safeText == "🎬 Vídeo") "🎬 Vídeo (${mediaDuration ?: "0:05"})" else "🎬 $safeText"
                MessageType.CALL_EVENT -> "📞 $safeText"
                else -> safeText.take(300)
            }

            dao.insertMessageAndUpdateChat(
                message = msg,
                preview = preview.take(300),
                displayTime = safeTime,
                senderRole = senderRole.name,
                tickStatus = tickStatus.name,
                updatedAtMillis = now
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMessage(
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
        messageType: MessageType?,
        mediaPreviewBase64: String?,
        localMediaPath: String?,
        mediaFileName: String?,
        mediaDuration: String?,
        clearMedia: Boolean,
    ): Result<Unit> {
        return try {
            val safeText = text.trim().take(2000)
            require(safeText.isNotEmpty()) { "El texto del mensaje no puede estar vacío" }
            val current = dao.getMessageOnce(userId, chatId, messageId)
                ?: return Result.failure(IllegalStateException("Mensaje no encontrado"))
            val updated = when {
                clearMedia -> current.copy(
                    text = safeText,
                    messageType = MessageType.TEXT.name,
                    displayTime = displayTime.trim().ifBlank { currentTimeString() }.take(24),
                    dateLabel = dateLabel.trim().ifBlank { "HOY" }.take(40),
                    senderRole = senderRole.name,
                    senderName = senderName.trim().take(60),
                    tickStatus = tickStatus.name,
                    reactionEmoji = reactionEmoji?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    updatedAtMillis = System.currentTimeMillis()
                )
                messageType != null -> current.copy(
                    text = safeText,
                    messageType = messageType.name,
                    displayTime = displayTime.trim().ifBlank { currentTimeString() }.take(24),
                    dateLabel = dateLabel.trim().ifBlank { "HOY" }.take(40),
                    senderRole = senderRole.name,
                    senderName = senderName.trim().take(60),
                    tickStatus = tickStatus.name,
                    reactionEmoji = reactionEmoji?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                    voiceDuration = null,
                    mediaPreviewBase64 = mediaPreviewBase64?.takeIf { it.length <= 120000 },
                    localMediaPath = localMediaPath?.take(300),
                    mediaFileName = mediaFileName?.take(120),
                    mediaDuration = mediaDuration?.take(16),
                    updatedAtMillis = System.currentTimeMillis()
                )
                else -> current.copy(
                    text = safeText,
                    displayTime = displayTime.trim().ifBlank { currentTimeString() }.take(24),
                    dateLabel = dateLabel.trim().ifBlank { "HOY" }.take(40),
                    senderRole = senderRole.name,
                    senderName = senderName.trim().take(60),
                    tickStatus = tickStatus.name,
                    reactionEmoji = reactionEmoji?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
            dao.upsertMessage(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(userId: String, chatId: String, messageId: String): Result<Unit> {
        return try {
            dao.deleteMessage(userId, chatId, messageId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- FAKE CALLS ---

    override fun observeFakeCalls(userId: String): Flow<List<FakeCallRecord>> =
        dao.observeCalls(userId).map { list -> list.map { it.toModel() } }

    override suspend fun addFakeCallRecord(
        userId: String,
        contactName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String?,
        callType: CallType,
        direction: CallDirection,
        durationText: String,
        displayTime: String,
    ): Result<Unit> {
        return try {
            val safeName = contactName.trim().take(60)
            require(safeName.isNotEmpty()) { "Nombre requerido" }
            val now = System.currentTimeMillis()
            dao.upsertCall(
                LocalCallEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    contactName = safeName,
                    avatarEmoji = avatarEmoji.trim().ifBlank { "📞" }.take(16),
                    avatarColorHex = avatarColorHex.trim().ifBlank { "#00796B" }.take(12),
                    avatarImageBase64 = avatarImageBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 48000 },
                    callType = callType.name,
                    direction = direction.name,
                    durationText = durationText.trim().take(40),
                    displayTime = displayTime.trim().ifBlank { "Hoy, ${currentTimeString()}" }.take(32),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFakeCallRecord(userId: String, callId: String): Result<Unit> {
        return try {
            dao.deleteCall(userId, callId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- PORTABLE JSON BACKUP ---

    override suspend fun exportWorkspaceToJson(userId: String): Result<String> {
        return try {
            val chats = dao.getChatsOnce(userId)
            val chatsArray = JSONArray()
            for (chat in chats) {
                val messages = dao.getMessagesOnce(userId, chat.id)
                val msgsArray = JSONArray()
                for (m in messages) {
                    msgsArray.put(
                        JSONObject().apply {
                            put("senderRole", m.senderRole)
                            put("senderName", m.senderName)
                            put("text", m.text)
                            put("messageType", m.messageType)
                            put("displayTime", m.displayTime)
                            put("dateLabel", m.dateLabel)
                            put("tickStatus", m.tickStatus)
                            m.voiceDuration?.let { put("voiceDuration", it) }
                            m.mediaPreviewBase64?.let { put("mediaPreviewBase64", it) }
                            m.mediaFileName?.let { put("mediaFileName", it) }
                            m.mediaDuration?.let { put("mediaDuration", it) }
                            m.reactionEmoji?.let { put("reactionEmoji", it) }
                            m.replyToText?.let { put("replyToText", it) }
                            m.replyToSender?.let { put("replyToSender", it) }
                            put("orderIndex", m.orderIndex)
                        }
                    )
                }
                chatsArray.put(
                    JSONObject().apply {
                        put("contactName", chat.contactName)
                        put("groupChat", chat.groupChat)
                        put("statusText", chat.statusText)
                        put("online", chat.online)
                        put("verified", chat.verified)
                        put("avatarEmoji", chat.avatarEmoji)
                        put("avatarColorHex", chat.avatarColorHex)
                        chat.avatarImageBase64?.let { put("avatarImageBase64", it) }
                        put("wallpaperTheme", chat.wallpaperTheme)
                        put("unreadCount", chat.unreadCount)
                        put("pinned", chat.pinned)
                        put("lastMessagePreview", chat.lastMessagePreview)
                        put("lastMessageTime", chat.lastMessageTime)
                        put("lastMessageSender", chat.lastMessageSender)
                        put("lastMessageTick", chat.lastMessageTick)
                        put("messages", msgsArray)
                    }
                )
            }
            val root = JSONObject().apply {
                put("app", "MostazaChat")
                put("developer", "MostazaLabs")
                put("version", 1)
                put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                put("chats", chatsArray)
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importWorkspaceFromJson(userId: String, jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val chatsArray = root.optJSONArray("chats")
                ?: return Result.failure(IllegalArgumentException("Archivo JSON sin lista de chats"))
            var importedCount = 0
            val baseTime = System.currentTimeMillis()
            for (i in 0 until chatsArray.length()) {
                val chatObj = chatsArray.getJSONObject(i)
                val contactName = chatObj.optString("contactName", "").trim().take(60)
                if (contactName.isEmpty()) continue
                val chatId = UUID.randomUUID().toString()
                val chatTime = baseTime + i
                val chatEntity = LocalChatEntity(
                    id = chatId,
                    userId = userId,
                    contactName = contactName,
                    groupChat = chatObj.optBoolean("groupChat", false),
                    statusText = chatObj.optString("statusText", "en línea").take(80),
                    online = chatObj.optBoolean("online", true),
                    verified = chatObj.optBoolean("verified", false),
                    avatarEmoji = chatObj.optString("avatarEmoji", "💬").ifBlank { "💬" }.take(16),
                    avatarColorHex = chatObj.optString("avatarColorHex", "#00796B").ifBlank { "#00796B" }.take(12),
                    avatarImageBase64 = chatObj.optString("avatarImageBase64", "").takeIf { it.isNotBlank() && it.length <= 48000 },
                    wallpaperTheme = WallpaperTheme.fromRaw(chatObj.optString("wallpaperTheme")).name,
                    unreadCount = chatObj.optLong("unreadCount", 0L).coerceIn(0L, 9999L),
                    pinned = chatObj.optBoolean("pinned", false),
                    lastMessagePreview = chatObj.optString("lastMessagePreview", "Chat importado").take(300),
                    lastMessageTime = chatObj.optString("lastMessageTime", currentTimeString()).take(24),
                    lastMessageSender = SenderRole.fromRaw(chatObj.optString("lastMessageSender")).name,
                    lastMessageTick = TickStatus.fromRaw(chatObj.optString("lastMessageTick")).name,
                    createdAtMillis = chatTime,
                    updatedAtMillis = chatTime
                )
                val msgsArray = chatObj.optJSONArray("messages") ?: JSONArray()
                val limit = minOf(msgsArray.length(), 2000)
                val messageEntities = mutableListOf<LocalMessageEntity>()
                for (j in 0 until limit) {
                    val mObj = msgsArray.getJSONObject(j)
                    val text = mObj.optString("text", "").trim().take(2000)
                    if (text.isEmpty()) continue
                    messageEntities += LocalMessageEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        chatId = chatId,
                        senderRole = SenderRole.fromRaw(mObj.optString("senderRole")).name,
                        senderName = mObj.optString("senderName", "").take(60),
                        text = text,
                        messageType = MessageType.fromRaw(mObj.optString("messageType")).name,
                        displayTime = mObj.optString("displayTime", "12:00").ifBlank { "12:00" }.take(24),
                        dateLabel = mObj.optString("dateLabel", "HOY").take(40),
                        tickStatus = TickStatus.fromRaw(mObj.optString("tickStatus")).name,
                        voiceDuration = mObj.optString("voiceDuration", "").takeIf { it.isNotBlank() }?.take(16),
                        mediaPreviewBase64 = mObj.optString("mediaPreviewBase64", "").takeIf { it.isNotBlank() && it.length <= 120000 },
                        localMediaPath = null,
                        mediaFileName = mObj.optString("mediaFileName", "").takeIf { it.isNotBlank() }?.take(120),
                        mediaDuration = mObj.optString("mediaDuration", "").takeIf { it.isNotBlank() }?.take(16),
                        reactionEmoji = mObj.optString("reactionEmoji", "").takeIf { it.isNotBlank() }?.take(16),
                        replyToText = mObj.optString("replyToText", "").takeIf { it.isNotBlank() }?.take(300),
                        replyToSender = mObj.optString("replyToSender", "").takeIf { it.isNotBlank() }?.take(60),
                        orderIndex = mObj.optLong("orderIndex", (j + 1).toLong()).coerceIn(0L, 1_000_000_000L),
                        createdAtMillis = chatTime + j,
                        updatedAtMillis = chatTime + j
                    )
                }
                dao.insertChatWithMessages(chatEntity, messageEntities)
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importWhatsAppChat(
        userId: String,
        parsed: ParsedWhatsAppChat,
        chatTitle: String,
        myParticipantName: String?,
        isGroup: Boolean
    ): Result<Pair<String, Int>> {
        return try {
            val safeTitle = chatTitle.trim().ifBlank { "Chat importado" }.take(60)
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val words = safeTitle.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val initials = when {
                words.isEmpty() -> "💬"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "${words.first().first().uppercaseChar()}${words.last().first().uppercaseChar()}"
            }.take(16)
            val colorHex = AvatarPresets.colors[kotlin.math.abs(safeTitle.hashCode()) % AvatarPresets.colors.size]

            val lastParsed = parsed.messages.lastOrNull()
            val lastSenderRole = when {
                lastParsed == null || lastParsed.isSystem -> SenderRole.SYSTEM
                myParticipantName != null && lastParsed.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                else -> SenderRole.OTHER
            }
            val lastPreview = (lastParsed?.text ?: "Chat importado de WhatsApp").replace('\n', ' ').trim().take(300)
            val lastTime = (lastParsed?.timeLabel ?: currentTimeString()).trim().ifBlank { currentTimeString() }.take(24)

            val chatEntity = LocalChatEntity(
                id = chatId,
                userId = userId,
                contactName = safeTitle,
                groupChat = isGroup,
                statusText = if (isGroup) {
                    parsed.participants.take(4).joinToString(", ").take(80).ifBlank { "Grupo importado" }
                } else {
                    "en línea"
                },
                online = !isGroup,
                verified = false,
                avatarEmoji = initials,
                avatarColorHex = colorHex,
                avatarImageBase64 = null,
                wallpaperTheme = WallpaperTheme.CLASSIC.name,
                unreadCount = 0L,
                pinned = false,
                lastMessagePreview = lastPreview,
                lastMessageTime = lastTime,
                lastMessageSender = lastSenderRole.name,
                lastMessageTick = TickStatus.READ.name,
                createdAtMillis = now,
                updatedAtMillis = now
            )

            val messageEntities = parsed.messages.mapIndexed { idx, item ->
                val role = when {
                    item.isSystem -> SenderRole.SYSTEM
                    myParticipantName != null && item.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                    else -> SenderRole.OTHER
                }
                val msgType = if (item.isSystem) MessageType.SYSTEM else MessageType.TEXT
                val safeText = item.text.trim().ifBlank { " " }.take(2000)
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = role.name,
                    senderName = item.senderName.trim().take(60),
                    text = safeText,
                    messageType = msgType.name,
                    displayTime = item.timeLabel.trim().ifBlank { "12:00" }.take(24),
                    dateLabel = item.dateLabel.trim().ifBlank { "HOY" }.take(40),
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = null,
                    replyToText = null,
                    replyToSender = null,
                    orderIndex = (idx + 1).toLong(),
                    createdAtMillis = now + idx,
                    updatedAtMillis = now + idx
                )
            }

            dao.insertChatWithMessages(chatEntity, messageEntities)
            Result.success(chatId to messageEntities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importWhatsAppMessagesIntoChat(
        userId: String,
        chatId: String,
        parsedMessages: List<ParsedWhatsAppMessage>,
        myParticipantName: String?,
        replaceExisting: Boolean
    ): Result<Int> {
        return try {
            if (parsedMessages.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay mensajes en los archivos .txt seleccionados"))
            }
            val currentChat = dao.getChatOnce(userId, chatId)
                ?: return Result.failure(IllegalStateException("Conversación no encontrada"))
            val now = System.currentTimeMillis()

            val startOrder = if (replaceExisting) {
                0L
            } else {
                dao.getMessagesOnce(userId, chatId).maxOfOrNull { it.orderIndex } ?: 0L
            }

            val messageEntities = parsedMessages.mapIndexed { idx, item ->
                val role = when {
                    item.isSystem -> SenderRole.SYSTEM
                    myParticipantName != null && item.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                    else -> SenderRole.OTHER
                }
                val msgType = if (item.isSystem) MessageType.SYSTEM else MessageType.TEXT
                val safeText = item.text.trim().ifBlank { " " }.take(2000)
                LocalMessageEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    chatId = chatId,
                    senderRole = role.name,
                    senderName = item.senderName.trim().take(60),
                    text = safeText,
                    messageType = msgType.name,
                    displayTime = item.timeLabel.trim().ifBlank { "12:00" }.take(24),
                    dateLabel = item.dateLabel.trim().ifBlank { "HOY" }.take(40),
                    tickStatus = TickStatus.READ.name,
                    voiceDuration = null,
                    mediaPreviewBase64 = null,
                    localMediaPath = null,
                    mediaFileName = null,
                    mediaDuration = null,
                    reactionEmoji = null,
                    replyToText = null,
                    replyToSender = null,
                    orderIndex = startOrder + idx + 1L,
                    createdAtMillis = now + idx,
                    updatedAtMillis = now + idx
                )
            }

            val lastParsed = parsedMessages.last()
            val lastSenderRole = when {
                lastParsed.isSystem -> SenderRole.SYSTEM
                myParticipantName != null && lastParsed.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                else -> SenderRole.OTHER
            }
            val lastPreview = lastParsed.text.replace('\n', ' ').trim().ifBlank { "Chat importado de WhatsApp" }.take(300)
            val lastTime = lastParsed.timeLabel.trim().ifBlank { currentTimeString() }.take(24)

            val updatedChat = currentChat.copy(
                lastMessagePreview = lastPreview,
                lastMessageTime = lastTime,
                lastMessageSender = lastSenderRole.name,
                lastMessageTick = TickStatus.READ.name,
                updatedAtMillis = now
            )

            dao.replaceOrAppendChatMessages(
                chat = updatedChat,
                messages = messageEntities,
                replaceExisting = replaceExisting
            )
            Result.success(messageEntities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun LocalProfileEntity.toModel() = UserProfile(
        id = userId,
        userId = userId,
        displayName = displayName,
        email = email,
        defaultMyName = defaultMyName,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalChatEntity.toModel() = FakeChat(
        id = id,
        userId = userId,
        contactName = contactName,
        groupChat = groupChat,
        statusText = statusText,
        online = online,
        verified = verified,
        avatarEmoji = avatarEmoji,
        avatarColorHex = avatarColorHex,
        avatarImageBase64 = avatarImageBase64,
        wallpaperTheme = wallpaperTheme,
        unreadCount = unreadCount,
        pinned = pinned,
        lastMessagePreview = lastMessagePreview,
        lastMessageTime = lastMessageTime,
        lastMessageSender = lastMessageSender,
        lastMessageTick = lastMessageTick,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalMessageEntity.toModel() = FakeMessage(
        id = id,
        userId = userId,
        chatId = chatId,
        senderRole = senderRole,
        senderName = senderName,
        text = text,
        messageType = messageType,
        displayTime = displayTime,
        dateLabel = dateLabel,
        tickStatus = tickStatus,
        voiceDuration = voiceDuration,
        mediaPreviewBase64 = mediaPreviewBase64,
        localMediaPath = localMediaPath,
        mediaFileName = mediaFileName,
        mediaDuration = mediaDuration,
        reactionEmoji = reactionEmoji,
        replyToText = replyToText,
        replyToSender = replyToSender,
        orderIndex = orderIndex,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    private fun LocalCallEntity.toModel() = FakeCallRecord(
        id = id,
        userId = userId,
        contactName = contactName,
        avatarEmoji = avatarEmoji,
        avatarColorHex = avatarColorHex,
        avatarImageBase64 = avatarImageBase64,
        callType = callType,
        direction = direction,
        durationText = durationText,
        displayTime = displayTime,
        createdAt = createdAtMillis.toTimestamp(),
        updatedAt = updatedAtMillis.toTimestamp()
    )

    companion object {
        const val DEFAULT_GUEST_USER_ID = "local_guest"
    }
}
