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
