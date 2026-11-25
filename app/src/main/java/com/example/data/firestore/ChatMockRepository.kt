package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.ChatDataRepository
import com.example.data.model.AvatarPresets
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.FakeCallRecord
import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.GroupMembersCodec
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.data.model.TickStatus
import com.example.data.model.UserProfile
import com.example.data.model.WallpaperTheme
import com.example.media.MediaImportHelper
import com.example.media.ParsedWhatsAppChat
import com.example.media.ParsedWhatsAppMessage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMockRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ChatDataRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a la nube.")
    }

    private fun currentTimeString(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    // --- USER PROFILE ---

    override suspend fun ensureUserProfile(displayName: String, email: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val docPath = "users/$uid"
        return try {
            val ref = db.collection("users").document(uid)
            val snap = ref.get().await()
            val safeDisplayName = displayName.trim().ifBlank { "Usuario" }.take(80)
            val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            if (!snap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeDisplayName,
                    "defaultMyName" to "Yo",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (safeEmail != null) {
                    payload["email"] = safeEmail
                }
                ref.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to safeDisplayName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (safeEmail != null) {
                    updates["email"] = safeEmail
                }
                ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    handleStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    override suspend fun updateDefaultMyName(userId: String, defaultMyName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val clean = defaultMyName.trim().ifBlank { "Yo" }.take(40)
            db.collection("users").document(uid).update(
                mapOf(
                    "defaultMyName" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- FAKE CHATS ---

    override fun observeChats(userId: String): Flow<List<FakeChat>> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "users/$userId/chats"
        emitAll(
            db.collection("users").document(userId).collection("chats")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FakeChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<FakeChat> { it.pinned }
                                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    override fun observeChat(userId: String, chatId: String): Flow<FakeChat?> = flow {
        require(userId.isNotBlank() && chatId.isNotBlank()) { "IDs inválidos" }
        val path = "users/$userId/chats/$chatId"
        emitAll(
            db.collection("users").document(userId).collection("chats").document(chatId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(FakeChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    handleStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

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
        groupMembersJson: String?,
    ): Result<String> {
        var docPath = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val safeName = contactName.trim().take(60)
            require(safeName.isNotEmpty()) { "El nombre del contacto no puede estar vacío" }
            val docRef = db.collection("users").document(uid).collection("chats").document()
            docPath = docRef.path
            val safeStatus = statusText.trim().take(80)
            val safeEmoji = avatarEmoji.trim().ifBlank { "💬" }.take(16)
            val safeColor = avatarColorHex.trim().ifBlank { "#00796B" }.take(12)
            val safePhoto = MediaImportHelper.normalizeAvatarBase64(avatarImageBase64, maxChars = 48_000)
            val safeMembers = if (groupChat) {
                groupMembersJson?.trim()?.takeIf { it.isNotEmpty() && it.length <= 32000 }
            } else {
                null
            }
            val nowTime = currentTimeString()

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "contactName" to safeName,
                "groupChat" to groupChat,
                "statusText" to safeStatus,
                "online" to online,
                "verified" to verified,
                "avatarEmoji" to safeEmoji,
                "avatarColorHex" to safeColor,
                "avatarImageBase64" to safePhoto,
                "wallpaperTheme" to wallpaperTheme.name,
                "unreadCount" to unreadCount.coerceIn(0L, 9999L),
                "pinned" to pinned,
                "groupMembersJson" to safeMembers,
                "lastMessagePreview" to "Conversación lista para editar",
                "lastMessageTime" to nowTime,
                "lastMessageSender" to SenderRole.SYSTEM.name,
                "lastMessageTick" to TickStatus.READ.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun createSampleConversation(userId: String): Result<String> {
        var docPath = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val chatRef = db.collection("users").document(uid).collection("chats").document()
            docPath = chatRef.path
            val chatId = chatRef.id

            val batch = db.batch()
            val chatPayload = mapOf(
                "userId" to uid,
                "contactName" to "Sofía ☕",
                "groupChat" to false,
                "statusText" to "en línea",
                "online" to true,
                "verified" to true,
                "avatarEmoji" to "☕",
                "avatarColorHex" to "#4F46E5",
                "wallpaperTheme" to WallpaperTheme.MINT.name,
                "unreadCount" to 0L,
                "pinned" to true,
                "lastMessagePreview" to "¡Jajaja qué buena broma! 😂",
                "lastMessageTime" to "19:44",
                "lastMessageSender" to SenderRole.OTHER.name,
                "lastMessageTick" to TickStatus.READ.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            batch.set(chatRef, chatPayload)

            val sampleMessages = listOf(
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to SenderRole.OTHER.name,
                    "senderName" to "Sofía ☕",
                    "text" to "¿Te puedes creer lo que acaba de pasar en la oficina? 👀",
                    "messageType" to MessageType.TEXT.name,
                    "displayTime" to "19:40",
                    "dateLabel" to "HOY",
                    "tickStatus" to TickStatus.READ.name,
                    "orderIndex" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to SenderRole.ME.name,
                    "senderName" to "Yo",
                    "text" to "¡Cuenta ya! 😱",
                    "messageType" to MessageType.TEXT.name,
                    "displayTime" to "19:41",
                    "dateLabel" to "HOY",
                    "tickStatus" to TickStatus.READ.name,
                    "reactionEmoji" to "🔥",
                    "orderIndex" to 2L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to SenderRole.OTHER.name,
                    "senderName" to "Sofía ☕",
                    "text" to "Nota de voz simulada",
                    "messageType" to MessageType.VOICE.name,
                    "displayTime" to "19:42",
                    "dateLabel" to "HOY",
                    "tickStatus" to TickStatus.READ.name,
                    "voiceDuration" to "0:18",
                    "orderIndex" to 3L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to SenderRole.OTHER.name,
                    "senderName" to "Sofía ☕",
                    "text" to "¡Jajaja qué buena broma! 😂",
                    "messageType" to MessageType.TEXT.name,
                    "displayTime" to "19:44",
                    "dateLabel" to "HOY",
                    "tickStatus" to TickStatus.READ.name,
                    "replyToText" to "¡Cuenta ya! 😱",
                    "replyToSender" to "Yo",
                    "orderIndex" to 4L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            sampleMessages.forEach { msg ->
                val msgRef = chatRef.collection("messages").document()
                batch.set(msgRef, msg)
            }
            batch.commit().await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
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
        groupMembersJson: String?,
        clearGroupMembers: Boolean,
    ): Result<Unit> {
        val docPath = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            require(chatId.isNotBlank()) { "chatId requerido" }
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            contactName?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["contactName"] = it.take(60) }
            groupChat?.let { updates["groupChat"] = it }
            statusText?.let { updates["statusText"] = it.trim().take(80) }
            online?.let { updates["online"] = it }
            verified?.let { updates["verified"] = it }
            avatarEmoji?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["avatarEmoji"] = it.take(16) }
            avatarColorHex?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["avatarColorHex"] = it.take(12) }
            if (clearAvatarPhoto) {
                updates["avatarImageBase64"] = FieldValue.delete()
            } else {
                MediaImportHelper.normalizeAvatarBase64(avatarImageBase64, maxChars = 48_000)?.let {
                    updates["avatarImageBase64"] = it
                }
            }
            wallpaperTheme?.let { updates["wallpaperTheme"] = it.name }
            unreadCount?.let { updates["unreadCount"] = it.coerceIn(0L, 9999L) }
            pinned?.let { updates["pinned"] = it }
            if (clearGroupMembers || groupChat == false) {
                updates["groupMembersJson"] = FieldValue.delete()
            } else {
                groupMembersJson?.trim()?.takeIf { it.isNotEmpty() && it.length <= 32000 }?.let {
                    updates["groupMembersJson"] = it
                }
            }

            db.collection("users").document(uid).collection("chats").document(chatId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(userId: String, chatId: String): Result<Unit> {
        val docPath = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgs = chatRef.collection("messages").get().await()
            msgs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- FAKE MESSAGES ---

    override fun observeMessages(userId: String, chatId: String): Flow<List<FakeMessage>> = flow {
        require(userId.isNotBlank() && chatId.isNotBlank()) { "IDs inválidos" }
        val path = "users/$userId/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("chats").document(chatId)
                .collection("messages")
                .orderBy("orderIndex")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FakeMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

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
        var docPath = "users/$userId/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val safeText = text.trim().take(2000)
            require(safeText.isNotEmpty()) { "El mensaje no puede estar vacío" }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            docPath = msgRef.path

            val safeTime = displayTime.trim().ifBlank { currentTimeString() }.take(24)
            val safeDate = dateLabel.trim().ifBlank { "HOY" }.take(40)
            val safeSenderName = senderName.trim().take(60)

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "chatId" to chatId,
                "senderRole" to senderRole.name,
                "senderName" to safeSenderName,
                "text" to safeText,
                "messageType" to messageType.name,
                "displayTime" to safeTime,
                "dateLabel" to safeDate,
                "tickStatus" to tickStatus.name,
                "voiceDuration" to voiceDuration?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                "mediaPreviewBase64" to mediaPreviewBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120000 },
                "localMediaPath" to localMediaPath?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "mediaFileName" to mediaFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
                "mediaDuration" to mediaDuration?.trim()?.takeIf { it.isNotEmpty() }?.take(16),
                "replyToText" to replyToText?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "replyToSender" to replyToSender?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                "orderIndex" to orderIndex.coerceIn(0L, 1_000_000_000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>

            val preview = when (messageType) {
                MessageType.VOICE -> "🎤 Nota de voz (${voiceDuration ?: "0:12"})"
                MessageType.IMAGE -> if (safeText == "📷 Foto") "📷 Foto" else "📷 $safeText"
                MessageType.VIDEO -> if (safeText == "🎬 Vídeo") "🎬 Vídeo (${mediaDuration ?: "0:05"})" else "🎬 $safeText"
                MessageType.CALL_EVENT -> "📞 $safeText"
                else -> safeText.take(300)
            }

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessagePreview" to preview.take(300),
                    "lastMessageTime" to safeTime,
                    "lastMessageSender" to senderRole.name,
                    "lastMessageTick" to tickStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
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
        val docPath = "users/$userId/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val safeText = text.trim().take(2000)
            require(safeText.isNotEmpty()) { "El texto del mensaje no puede estar vacío" }
            val msgRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)

            val updates = mutableMapOf<String, Any>(
                "text" to safeText,
                "displayTime" to displayTime.trim().ifBlank { currentTimeString() }.take(24),
                "dateLabel" to dateLabel.trim().ifBlank { "HOY" }.take(40),
                "senderRole" to senderRole.name,
                "senderName" to senderName.trim().take(60),
                "tickStatus" to tickStatus.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            val cleanReaction = reactionEmoji?.trim()?.takeIf { it.isNotEmpty() }?.take(16)
            if (cleanReaction != null) {
                updates["reactionEmoji"] = cleanReaction
            } else {
                updates["reactionEmoji"] = FieldValue.delete()
            }
            if (clearMedia) {
                updates["messageType"] = MessageType.TEXT.name
                updates["voiceDuration"] = FieldValue.delete()
                updates["mediaPreviewBase64"] = FieldValue.delete()
                updates["localMediaPath"] = FieldValue.delete()
                updates["mediaFileName"] = FieldValue.delete()
                updates["mediaDuration"] = FieldValue.delete()
            } else if (messageType != null) {
                updates["messageType"] = messageType.name
                updates["voiceDuration"] = FieldValue.delete()
                mediaPreviewBase64?.takeIf { it.isNotBlank() && it.length <= 120000 }?.let {
                    updates["mediaPreviewBase64"] = it
                } ?: run { updates["mediaPreviewBase64"] = FieldValue.delete() }
                localMediaPath?.takeIf { it.isNotBlank() }?.let {
                    updates["localMediaPath"] = it.take(300)
                } ?: run { updates["localMediaPath"] = FieldValue.delete() }
                mediaFileName?.takeIf { it.isNotBlank() }?.let {
                    updates["mediaFileName"] = it.take(120)
                } ?: run { updates["mediaFileName"] = FieldValue.delete() }
                mediaDuration?.takeIf { it.isNotBlank() }?.let {
                    updates["mediaDuration"] = it.take(16)
                } ?: run { updates["mediaDuration"] = FieldValue.delete() }
            }
            msgRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(userId: String, chatId: String, messageId: String): Result<Unit> {
        val docPath = "users/$userId/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- FAKE CALLS ---

    override fun observeFakeCalls(userId: String): Flow<List<FakeCallRecord>> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "users/$userId/fakeCalls"
        emitAll(
            db.collection("users").document(userId).collection("fakeCalls")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FakeCallRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

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
        var docPath = "users/$userId/fakeCalls"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val safeName = contactName.trim().take(60)
            require(safeName.isNotEmpty()) { "Nombre requerido" }
            val docRef = db.collection("users").document(uid).collection("fakeCalls").document()
            docPath = docRef.path
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "contactName" to safeName,
                "avatarEmoji" to avatarEmoji.trim().ifBlank { "📞" }.take(16),
                "avatarColorHex" to avatarColorHex.trim().ifBlank { "#00796B" }.take(12),
                "avatarImageBase64" to avatarImageBase64?.trim()?.takeIf { it.isNotEmpty() && it.length <= 48000 },
                "callType" to callType.name,
                "direction" to direction.name,
                "durationText" to durationText.trim().take(40),
                "displayTime" to displayTime.trim().ifBlank { "Hoy, ${currentTimeString()}" }.take(32),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteFakeCallRecord(userId: String, callId: String): Result<Unit> {
        val docPath = "users/$userId/fakeCalls/$callId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            db.collection("users").document(uid).collection("fakeCalls").document(callId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- PORTABLE JSON BACKUP (DRIVE / PC / FILE EXPORT & IMPORT) ---

    override suspend fun exportWorkspaceToJson(userId: String): Result<String> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val chatsSnap = db.collection("users").document(uid).collection("chats").get().await()
            val chatsArray = JSONArray()
            for (chatDoc in chatsSnap.documents) {
                val chat = chatDoc.toObject(FakeChat::class.java) ?: continue
                val msgsSnap = chatDoc.reference.collection("messages").orderBy("orderIndex").get().await()
                val msgsArray = JSONArray()
                for (msgDoc in msgsSnap.documents) {
                    val m = msgDoc.toObject(FakeMessage::class.java) ?: continue
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
                        chat.groupMembersJson?.let { put("groupMembersJson", it) }
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
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    override suspend fun importWorkspaceFromJson(userId: String, jsonString: String): Result<Int> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val root = JSONObject(jsonString)
            val chatsArray = root.optJSONArray("chats")
                ?: return Result.failure(IllegalArgumentException("Archivo JSON sin lista de chats"))

            var importedCount = 0
            for (i in 0 until chatsArray.length()) {
                val chatObj = chatsArray.getJSONObject(i)
                val contactName = chatObj.optString("contactName", "").trim().take(60)
                if (contactName.isEmpty()) continue

                val chatRef = db.collection("users").document(uid).collection("chats").document()
                val chatId = chatRef.id
                val batch = db.batch()

                val chatPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "contactName" to contactName,
                    "groupChat" to chatObj.optBoolean("groupChat", false),
                    "statusText" to chatObj.optString("statusText", "en línea").take(80),
                    "online" to chatObj.optBoolean("online", true),
                    "verified" to chatObj.optBoolean("verified", false),
                    "avatarEmoji" to chatObj.optString("avatarEmoji", "💬").ifBlank { "💬" }.take(16),
                    "avatarColorHex" to chatObj.optString("avatarColorHex", "#00796B").ifBlank { "#00796B" }.take(12),
                    "avatarImageBase64" to chatObj.optString("avatarImageBase64", "").takeIf { it.isNotBlank() && it.length <= 48000 },
                    "wallpaperTheme" to WallpaperTheme.fromRaw(chatObj.optString("wallpaperTheme")).name,
                    "unreadCount" to chatObj.optLong("unreadCount", 0L).coerceIn(0L, 9999L),
                    "pinned" to chatObj.optBoolean("pinned", false),
                    "groupMembersJson" to chatObj.optString("groupMembersJson", "").takeIf { it.isNotBlank() && it.length <= 32000 },
                    "lastMessagePreview" to chatObj.optString("lastMessagePreview", "Chat importado").take(300),
                    "lastMessageTime" to chatObj.optString("lastMessageTime", currentTimeString()).take(24),
                    "lastMessageSender" to SenderRole.fromRaw(chatObj.optString("lastMessageSender")).name,
                    "lastMessageTick" to TickStatus.fromRaw(chatObj.optString("lastMessageTick")).name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                batch.set(chatRef, chatPayload)

                val msgsArray = chatObj.optJSONArray("messages") ?: JSONArray()
                val limit = minOf(msgsArray.length(), 400)
                for (j in 0 until limit) {
                    val mObj = msgsArray.getJSONObject(j)
                    val text = mObj.optString("text", "").trim().take(2000)
                    if (text.isEmpty()) continue
                    val msgRef = chatRef.collection("messages").document()
                    val msgPayload = mutableMapOf<String, Any?>(
                        "userId" to uid,
                        "chatId" to chatId,
                        "senderRole" to SenderRole.fromRaw(mObj.optString("senderRole")).name,
                        "senderName" to mObj.optString("senderName", "").take(60),
                        "text" to text,
                        "messageType" to MessageType.fromRaw(mObj.optString("messageType")).name,
                        "displayTime" to mObj.optString("displayTime", "12:00").ifBlank { "12:00" }.take(24),
                        "dateLabel" to mObj.optString("dateLabel", "HOY").take(40),
                        "tickStatus" to TickStatus.fromRaw(mObj.optString("tickStatus")).name,
                        "voiceDuration" to mObj.optString("voiceDuration", "").takeIf { it.isNotBlank() }?.take(16),
                        "mediaPreviewBase64" to mObj.optString("mediaPreviewBase64", "").takeIf { it.isNotBlank() && it.length <= 120000 },
                        "mediaFileName" to mObj.optString("mediaFileName", "").takeIf { it.isNotBlank() }?.take(120),
                        "mediaDuration" to mObj.optString("mediaDuration", "").takeIf { it.isNotBlank() }?.take(16),
                        "reactionEmoji" to mObj.optString("reactionEmoji", "").takeIf { it.isNotBlank() }?.take(16),
                        "replyToText" to mObj.optString("replyToText", "").takeIf { it.isNotBlank() }?.take(300),
                        "replyToSender" to mObj.optString("replyToSender", "").takeIf { it.isNotBlank() }?.take(60),
                        "orderIndex" to mObj.optLong("orderIndex", (j + 1).toLong()).coerceIn(0L, 1_000_000_000L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).filterValues { it != null } as Map<String, Any>
                    batch.set(msgRef, msgPayload)
                }

                batch.commit().await()
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
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
        var docPath = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            val safeTitle = chatTitle.trim().ifBlank { "Chat importado" }.take(60)
            val chatRef = db.collection("users").document(uid).collection("chats").document()
            docPath = chatRef.path
            val chatId = chatRef.id

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

            val groupMembersEncoded = if (isGroup) {
                GroupMembersCodec.encode(
                    GroupMembersCodec.fromWhatsAppParticipants(parsed.participants, myParticipantName)
                )
            } else {
                null
            }

            val chatPayload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "contactName" to safeTitle,
                "groupChat" to isGroup,
                "statusText" to if (isGroup) {
                    parsed.participants.take(4).joinToString(", ").take(80).ifBlank { "Grupo importado" }
                } else {
                    "en línea"
                },
                "online" to !isGroup,
                "verified" to false,
                "avatarEmoji" to initials,
                "avatarColorHex" to colorHex,
                "wallpaperTheme" to WallpaperTheme.CLASSIC.name,
                "unreadCount" to 0L,
                "pinned" to false,
                "groupMembersJson" to groupMembersEncoded,
                "lastMessagePreview" to lastPreview,
                "lastMessageTime" to lastTime,
                "lastMessageSender" to lastSenderRole.name,
                "lastMessageTick" to TickStatus.READ.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>

            val messageEntries = parsed.messages.mapIndexed { idx, item ->
                val role = when {
                    item.isSystem -> SenderRole.SYSTEM
                    myParticipantName != null && item.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                    else -> SenderRole.OTHER
                }
                val msgType = if (item.isSystem) MessageType.SYSTEM else MessageType.TEXT
                val safeText = item.text.trim().ifBlank { " " }.take(2000)
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to role.name,
                    "senderName" to item.senderName.trim().take(60),
                    "text" to safeText,
                    "messageType" to msgType.name,
                    "displayTime" to item.timeLabel.trim().ifBlank { "12:00" }.take(24),
                    "dateLabel" to item.dateLabel.trim().ifBlank { "HOY" }.take(40),
                    "tickStatus" to TickStatus.READ.name,
                    "orderIndex" to (idx + 1).toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }

            val firstChunkSize = 400
            val firstBatch = db.batch()
            firstBatch.set(chatRef, chatPayload)
            messageEntries.take(firstChunkSize).forEach { msgMap ->
                val msgRef = chatRef.collection("messages").document()
                firstBatch.set(msgRef, msgMap)
            }
            firstBatch.commit().await()

            if (messageEntries.size > firstChunkSize) {
                messageEntries.drop(firstChunkSize).chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { msgMap ->
                        val msgRef = chatRef.collection("messages").document()
                        batch.set(msgRef, msgMap)
                    }
                    batch.commit().await()
                }
            }

            Result.success(chatId to messageEntries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
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
        var docPath = "users/$userId/chats/$chatId"
        return try {
            if (parsedMessages.isEmpty()) {
                return Result.failure(IllegalArgumentException("No hay mensajes en los archivos .txt seleccionados"))
            }
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada" }
            require(chatId.isNotBlank()) { "chatId requerido" }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = chatRef.path

            if (replaceExisting) {
                val existingSnap = chatRef.collection("messages").get().await()
                if (existingSnap.documents.isNotEmpty()) {
                    existingSnap.documents.chunked(400).forEach { chunk ->
                        val deleteBatch = db.batch()
                        chunk.forEach { doc -> deleteBatch.delete(doc.reference) }
                        deleteBatch.commit().await()
                    }
                }
            }

            val startOrder = if (replaceExisting) {
                0L
            } else {
                val lastSnap = chatRef.collection("messages")
                    .orderBy("orderIndex", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                lastSnap.documents.firstOrNull()?.getLong("orderIndex") ?: 0L
            }

            val messageEntries = parsedMessages.mapIndexed { idx, item ->
                val role = when {
                    item.isSystem -> SenderRole.SYSTEM
                    myParticipantName != null && item.senderName.equals(myParticipantName, ignoreCase = true) -> SenderRole.ME
                    else -> SenderRole.OTHER
                }
                val msgType = if (item.isSystem) MessageType.SYSTEM else MessageType.TEXT
                val safeText = item.text.trim().ifBlank { " " }.take(2000)
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "senderRole" to role.name,
                    "senderName" to item.senderName.trim().take(60),
                    "text" to safeText,
                    "messageType" to msgType.name,
                    "displayTime" to item.timeLabel.trim().ifBlank { "12:00" }.take(24),
                    "dateLabel" to item.dateLabel.trim().ifBlank { "HOY" }.take(40),
                    "tickStatus" to TickStatus.READ.name,
                    "orderIndex" to (startOrder + idx + 1L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
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

            val firstChunk = messageEntries.take(400)
            val firstBatch = db.batch()
            firstBatch.update(
                chatRef,
                mapOf(
                    "lastMessagePreview" to lastPreview,
                    "lastMessageTime" to lastTime,
                    "lastMessageSender" to lastSenderRole.name,
                    "lastMessageTick" to TickStatus.READ.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            firstChunk.forEach { msgMap ->
                val msgRef = chatRef.collection("messages").document()
                firstBatch.set(msgRef, msgMap)
            }
            firstBatch.commit().await()

            if (messageEntries.size > 400) {
                messageEntries.drop(400).chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { msgMap ->
                        val msgRef = chatRef.collection("messages").document()
                        batch.set(msgRef, msgMap)
                    }
                    batch.commit().await()
                }
            }

            Result.success(messageEntries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun handleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
