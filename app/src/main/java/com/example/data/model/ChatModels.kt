package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SenderRole {
    ME, OTHER, SYSTEM;

    companion object {
        fun fromRaw(raw: String?): SenderRole =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class MessageType {
    TEXT, VOICE, IMAGE, VIDEO, CALL_EVENT, SYSTEM;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class TickStatus(val labelEs: String) {
    SENT("Enviado (✓)"),
    DELIVERED("Entregado (✓✓)"),
    READ("Leído (✓✓ azul)");

    companion object {
        fun fromRaw(raw: String?): TickStatus =
            entries.firstOrNull { it.name == raw } ?: READ
    }
}

enum class WallpaperTheme(val labelEs: String) {
    CLASSIC("Clásico"),
    MIDNIGHT("Nocturno"),
    MINT("Menta"),
    OCEAN("Océano"),
    CORAL("Coral");

    companion object {
        fun fromRaw(raw: String?): WallpaperTheme =
            entries.firstOrNull { it.name == raw } ?: CLASSIC
    }
}

enum class CallType(val labelEs: String) {
    VOICE("Llamada de voz"),
    VIDEO("Videollamada");

    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw } ?: VOICE
    }
}

enum class CallDirection(val labelEs: String) {
    INCOMING("Entrante"),
    OUTGOING("Saliente"),
    MISSED("Perdida");

    companion object {
        fun fromRaw(raw: String?): CallDirection =
            entries.firstOrNull { it.name == raw } ?: INCOMING
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val defaultMyName: String = "Yo",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
data class GroupMember(
    val chatId: String? = null,
    val name: String = "",
    val statusText: String = "en línea",
    val avatarEmoji: String = "💬",
    val avatarColorHex: String = "#00796B",
    val isAdmin: Boolean = false,
    val isMe: Boolean = false,
)

object GroupMembersCodec {
    private const val MAX_JSON_LENGTH = 32000

    fun encode(members: List<GroupMember>): String? {
        val cleaned = members.mapNotNull { member ->
            val safeName = member.name.trim().take(60)
            if (safeName.isEmpty()) return@mapNotNull null
            member.copy(
                chatId = member.chatId?.trim()?.takeIf { it.isNotEmpty() }?.take(128),
                name = safeName,
                statusText = member.statusText.trim().ifBlank { "en línea" }.take(80),
                avatarEmoji = member.avatarEmoji.trim().ifBlank { "💬" }.take(16),
                avatarColorHex = member.avatarColorHex.trim().ifBlank { "#00796B" }.take(12)
            )
        }
        if (cleaned.isEmpty()) return null

        val array = org.json.JSONArray()
        for (member in cleaned) {
            val obj = org.json.JSONObject().apply {
                member.chatId?.let { put("chatId", it) }
                put("name", member.name)
                put("statusText", member.statusText)
                put("avatarEmoji", member.avatarEmoji)
                put("avatarColorHex", member.avatarColorHex)
                put("isAdmin", member.isAdmin)
                put("isMe", member.isMe)
            }
            array.put(obj)
            if (array.toString().length > MAX_JSON_LENGTH) {
                array.remove(array.length() - 1)
                break
            }
        }
        return if (array.length() == 0) null else array.toString()
    }

    fun decode(rawJson: String?): List<GroupMember> {
        val trimmed = rawJson?.trim() ?: return emptyList()
        if (trimmed.isEmpty() || !trimmed.startsWith("[")) return emptyList()
        return try {
            val array = org.json.JSONArray(trimmed)
            val result = mutableListOf<GroupMember>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(60)
                if (name.isEmpty()) continue
                result += GroupMember(
                    chatId = obj.optString("chatId", "").trim().takeIf { it.isNotEmpty() }?.take(128),
                    name = name,
                    statusText = obj.optString("statusText", "en línea").trim().ifBlank { "en línea" }.take(80),
                    avatarEmoji = obj.optString("avatarEmoji", "💬").trim().ifBlank { "💬" }.take(16),
                    avatarColorHex = obj.optString("avatarColorHex", "#00796B").trim().ifBlank { "#00796B" }.take(12),
                    isAdmin = obj.optBoolean("isAdmin", false),
                    isMe = obj.optBoolean("isMe", false)
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildStatusSummary(members: List<GroupMember>, maxLength: Int = 80): String {
        val names = members.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return "Grupo"
        val joined = names.joinToString(", ")
        return if (joined.length <= maxLength) {
            joined
        } else {
            "${names.take(3).joinToString(", ")} y ${maxOf(1, names.size - 3)} más".take(maxLength)
        }
    }

    fun fromStatusTextFallback(statusText: String, myDefaultName: String = "Tú"): List<GroupMember> {
        val parts = statusText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("y ", ignoreCase = true) }
        if (parts.size < 2 && !parts.any { it.equals("Tú", ignoreCase = true) || it.equals(myDefaultName, ignoreCase = true) }) {
            return emptyList()
        }
        return parts.mapIndexed { idx, name ->
            val isMe = name.equals("Tú", ignoreCase = true) ||
                name.equals("Yo", ignoreCase = true) ||
                name.equals(myDefaultName, ignoreCase = true)
            val color = AvatarPresets.colors[kotlin.math.abs(name.hashCode()) % AvatarPresets.colors.size]
            val initials = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.let { words ->
                when {
                    words.isEmpty() -> "💬"
                    words.size == 1 -> words[0].take(2).uppercase()
                    else -> "${words.first().first().uppercaseChar()}${words.last().first().uppercaseChar()}"
                }
            }
            GroupMember(
                chatId = null,
                name = name.take(60),
                statusText = "en línea",
                avatarEmoji = initials,
                avatarColorHex = color,
                isAdmin = isMe || idx == 0,
                isMe = isMe
            )
        }
    }

    fun fromWhatsAppParticipants(participants: List<String>, myParticipantName: String?): List<GroupMember> {
        val result = mutableListOf<GroupMember>()
        val seen = mutableSetOf<String>()
        if (!myParticipantName.isNullOrBlank()) {
            seen += myParticipantName.trim().lowercase()
            result += GroupMember(
                chatId = null,
                name = "Tú",
                statusText = "en línea",
                avatarEmoji = "TÚ",
                avatarColorHex = "#00796B",
                isAdmin = true,
                isMe = true
            )
        }
        participants.forEach { raw ->
            val clean = raw.trim().take(60)
            if (clean.isEmpty()) return@forEach
            if (!seen.add(clean.lowercase())) return@forEach
            val isMe = clean.equals("Tú", ignoreCase = true) || clean.equals("Yo", ignoreCase = true)
            val color = AvatarPresets.colors[kotlin.math.abs(clean.hashCode()) % AvatarPresets.colors.size]
            val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val initials = when {
                words.isEmpty() -> "💬"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "${words.first().first().uppercaseChar()}${words.last().first().uppercaseChar()}"
            }.take(16)
            result += GroupMember(
                chatId = null,
                name = clean,
                statusText = "en línea",
                avatarEmoji = initials,
                avatarColorHex = color,
                isAdmin = isMe,
                isMe = isMe
            )
        }
        return result
    }
}

@Keep
@IgnoreExtraProperties
data class FakeChat(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contactName: String = "",
    val groupChat: Boolean = false,
    val statusText: String = "en línea",
    val online: Boolean = true,
    val verified: Boolean = false,
    val avatarEmoji: String = "💬",
    val avatarColorHex: String = "#00796B",
    val avatarImageBase64: String? = null,
    val wallpaperTheme: String = WallpaperTheme.CLASSIC.name,
    val unreadCount: Long = 0L,
    val pinned: Boolean = false,
    val groupMembersJson: String? = null,
    val lastMessagePreview: String = "",
    val lastMessageTime: String = "",
    val lastMessageSender: String = SenderRole.ME.name,
    val lastMessageTick: String = TickStatus.READ.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedWallpaper: WallpaperTheme get() = WallpaperTheme.fromRaw(wallpaperTheme)
    val parsedLastSender: SenderRole get() = SenderRole.fromRaw(lastMessageSender)
    val parsedLastTick: TickStatus get() = TickStatus.fromRaw(lastMessageTick)
    val parsedGroupMembers: List<GroupMember> get() = GroupMembersCodec.decode(groupMembersJson)

    fun resolvedGroupMembers(myFallbackName: String = "Tú"): List<GroupMember> {
        val explicit = parsedGroupMembers
        if (explicit.isNotEmpty()) return explicit
        return if (groupChat) GroupMembersCodec.fromStatusTextFallback(statusText, myFallbackName) else emptyList()
    }
}

@Keep
@IgnoreExtraProperties
data class FakeMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val senderRole: String = SenderRole.ME.name,
    val senderName: String = "",
    val text: String = "",
    val messageType: String = MessageType.TEXT.name,
    val displayTime: String = "12:00",
    val dateLabel: String = "HOY",
    val tickStatus: String = TickStatus.READ.name,
    val voiceDuration: String? = null,
    val mediaPreviewBase64: String? = null,
    val localMediaPath: String? = null,
    val mediaFileName: String? = null,
    val mediaDuration: String? = null,
    val reactionEmoji: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val orderIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedSenderRole: SenderRole get() = SenderRole.fromRaw(senderRole)
    val parsedMessageType: MessageType get() = MessageType.fromRaw(messageType)
    val parsedTickStatus: TickStatus get() = TickStatus.fromRaw(tickStatus)
}

@Keep
@IgnoreExtraProperties
data class FakeCallRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contactName: String = "",
    val avatarEmoji: String = "📞",
    val avatarColorHex: String = "#00796B",
    val avatarImageBase64: String? = null,
    val callType: String = CallType.VOICE.name,
    val direction: String = CallDirection.INCOMING.name,
    val durationText: String = "01:24",
    val displayTime: String = "Hoy, 14:20",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedCallType: CallType get() = CallType.fromRaw(callType)
    val parsedDirection: CallDirection get() = CallDirection.fromRaw(direction)
}

object AvatarPresets {
    val emojis = listOf(
        "😎", "👩🏻‍💻", "🧑🏽‍🚀", "❤️", "💼", "✈️",
        "🔥", "🎭", "👑", "🐶", "☕", "🎮"
    )
    val colors = listOf(
        "#00796B", "#4F46E5", "#E11D48", "#D97706",
        "#0284C7", "#7C3AED", "#059669", "#DC2626"
    )
    val quickReactions = listOf("❤️", "😂", "😮", "😢", "🙏", "👍", "🔥")
}
