package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatDataRepository
import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.data.model.TickStatus
import com.example.data.model.UiState
import com.example.data.model.WallpaperTheme
import com.example.llm.ChatAiConfig
import com.example.llm.ExternalLlmClient
import com.example.llm.LlmGlobalSettings
import com.example.media.MediaImportHelper
import com.example.media.PendingTxtImport
import com.example.safety.SensitiveContentFilter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRoomViewModel(
    private val repository: ChatDataRepository,
    private val currentUserId: String,
    private val chatId: String
) : ViewModel() {

    val chatState: StateFlow<UiState<FakeChat?>> = repository.observeChat(currentUserId, chatId)
        .map<FakeChat?, UiState<FakeChat?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat", rootError)
            emit(UiState.Error(rootError.message ?: "Error al cargar el chat"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<FakeMessage>>> = repository.observeMessages(currentUserId, chatId)
        .map<List<FakeMessage>, UiState<List<FakeMessage>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing messages", rootError)
            emit(UiState.Error(rootError.message ?: "Error al cargar mensajes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _composerRole = MutableStateFlow(SenderRole.ME)
    val composerRole = _composerRole.asStateFlow()

    private val _composerTick = MutableStateFlow(TickStatus.READ)
    val composerTick = _composerTick.asStateFlow()

    private val _customTime = MutableStateFlow("")
    val customTime = _customTime.asStateFlow()

    private val _customDateLabel = MutableStateFlow("HOY")
    val customDateLabel = _customDateLabel.asStateFlow()

    private val _customGroupSenderName = MutableStateFlow("")
    val customGroupSenderName = _customGroupSenderName.asStateFlow()

    private val _replyTarget = MutableStateFlow<FakeMessage?>(null)
    val replyTarget = _replyTarget.asStateFlow()

    private val _screenshotMode = MutableStateFlow(false)
    val screenshotMode = _screenshotMode.asStateFlow()

    private val _isSimulatingTyping = MutableStateFlow(false)
    val isSimulatingTyping = _isSimulatingTyping.asStateFlow()

    private val _isGeneratingAiReply = MutableStateFlow(false)
    val isGeneratingAiReply = _isGeneratingAiReply.asStateFlow()

    private val _isImportingMedia = MutableStateFlow(false)
    val isImportingMedia = _isImportingMedia.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner = _errorBanner.asStateFlow()

    private var typingJob: Job? = null
    private var aiReplyJob: Job? = null

    fun setComposerRole(role: SenderRole) {
        _composerRole.value = role
    }

    fun setComposerTick(tick: TickStatus) {
        _composerTick.value = tick
    }

    fun setCustomTime(time: String) {
        _customTime.value = time
    }

    fun setCustomDateLabel(dateLabel: String) {
        _customDateLabel.value = dateLabel
    }

    fun setCustomGroupSenderName(name: String) {
        _customGroupSenderName.value = name
    }

    fun setReplyTarget(message: FakeMessage?) {
        _replyTarget.value = message
    }

    fun toggleScreenshotMode() {
        _screenshotMode.value = !_screenshotMode.value
    }

    fun clearErrorBanner() {
        _errorBanner.value = null
    }

    fun emitError(message: String) {
        _errorBanner.value = message
    }

    private fun currentClockTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    private fun nextOrderIndex(): Long {
        val current = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        return (current.maxOfOrNull { it.orderIndex } ?: 0L) + 1L
    }

    fun sendMessage(
        text: String,
        messageType: MessageType = MessageType.TEXT,
        voiceDuration: String? = null,
        myDefaultName: String = "Yo",
        contactName: String = "Contacto",
        isGroup: Boolean = false
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val role = _composerRole.value
        val timeLabel = _customTime.value.trim().ifBlank { currentClockTime() }
        val dateLabel = _customDateLabel.value.trim().ifBlank { "HOY" }
        val tick = _composerTick.value
        val senderName = when (role) {
            SenderRole.ME -> myDefaultName
            SenderRole.OTHER -> if (isGroup) {
                _customGroupSenderName.value.trim().ifBlank { contactName }
            } else contactName
            SenderRole.SYSTEM -> ""
        }
        val quote = _replyTarget.value
        val replySender = quote?.let {
            when (it.parsedSenderRole) {
                SenderRole.ME -> myDefaultName
                SenderRole.OTHER -> it.senderName.ifBlank { contactName }
                SenderRole.SYSTEM -> "Sistema"
            }
        }
        val orderIdx = nextOrderIndex()

        _replyTarget.value = null

        viewModelScope.launch {
            val result = repository.sendMessage(
                userId = currentUserId,
                chatId = chatId,
                senderRole = role,
                senderName = senderName,
                text = trimmed,
                messageType = messageType,
                displayTime = timeLabel,
                dateLabel = dateLabel,
                tickStatus = tick,
                orderIndex = orderIdx,
                voiceDuration = voiceDuration,
                replyToText = quote?.text,
                replyToSender = replySender
            )
            result.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "No se pudo enviar el mensaje"
            }
        }
    }

    fun sendMediaMessage(
        context: Context,
        uri: Uri,
        caption: String,
        roleOverride: SenderRole? = null,
        myDefaultName: String = "Yo",
        contactName: String = "Contacto",
        isGroup: Boolean = false
    ) {
        val role = roleOverride ?: _composerRole.value
        val timeLabel = _customTime.value.trim().ifBlank { currentClockTime() }
        val dateLabel = _customDateLabel.value.trim().ifBlank { "HOY" }
        val tick = _composerTick.value
        val senderName = when (role) {
            SenderRole.ME -> myDefaultName
            SenderRole.OTHER -> if (isGroup) {
                _customGroupSenderName.value.trim().ifBlank { contactName }
            } else contactName
            SenderRole.SYSTEM -> ""
        }
        val quote = _replyTarget.value
        val replySender = quote?.let {
            when (it.parsedSenderRole) {
                SenderRole.ME -> myDefaultName
                SenderRole.OTHER -> it.senderName.ifBlank { contactName }
                SenderRole.SYSTEM -> "Sistema"
            }
        }
        _replyTarget.value = null

        viewModelScope.launch {
            _isImportingMedia.value = true
            val imported = MediaImportHelper.importChatMediaFromGallery(context, uri)
            if (imported == null) {
                _isImportingMedia.value = false
                _errorBanner.value = "No se pudo procesar el archivo seleccionado de la galería"
                return@launch
            }
            val finalText = caption.trim().ifBlank {
                when {
                    imported.messageType == MessageType.VIDEO -> "🎬 Vídeo"
                    MediaImportHelper.isGifFile(null, imported.fileName, imported.localPath) -> "🎞️ GIF"
                    else -> "📷 Foto"
                }
            }
            val orderIdx = nextOrderIndex()
            val result = repository.sendMessage(
                userId = currentUserId,
                chatId = chatId,
                senderRole = role,
                senderName = senderName,
                text = finalText,
                messageType = imported.messageType,
                displayTime = timeLabel,
                dateLabel = dateLabel,
                tickStatus = tick,
                orderIndex = orderIdx,
                mediaPreviewBase64 = imported.previewBase64,
                localMediaPath = imported.localPath,
                mediaFileName = imported.fileName,
                mediaDuration = imported.durationText,
                replyToText = quote?.text,
                replyToSender = replySender
            )
            _isImportingMedia.value = false
            result.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "No se pudo enviar el archivo multimedia"
            }
        }
    }

    fun scheduleDelayedContactReply(
        replyText: String,
        delaySeconds: Int = 3,
        contactName: String
    ) {
        val trimmed = replyText.trim()
        if (trimmed.isEmpty()) return
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            _isSimulatingTyping.value = true
            delay(delaySeconds * 1000L)
            _isSimulatingTyping.value = false
            val timeLabel = _customTime.value.trim().ifBlank { currentClockTime() }
            val dateLabel = _customDateLabel.value.trim().ifBlank { "HOY" }
            val orderIdx = nextOrderIndex()
            repository.sendMessage(
                userId = currentUserId,
                chatId = chatId,
                senderRole = SenderRole.OTHER,
                senderName = contactName,
                text = trimmed,
                messageType = MessageType.TEXT,
                displayTime = timeLabel,
                dateLabel = dateLabel,
                tickStatus = TickStatus.READ,
                orderIndex = orderIdx
            ).onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "No se pudo simular la respuesta"
            }
        }
    }

    fun triggerTypingPulse(durationMs: Long = 3500L) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            _isSimulatingTyping.value = true
            delay(durationMs)
            _isSimulatingTyping.value = false
        }
    }

    fun generateAiReply(
        globalSettings: LlmGlobalSettings,
        chatAiConfig: ChatAiConfig,
        myDefaultName: String,
        sensitiveFilterEnabled: Boolean,
        extraPendingUserMessage: String? = null
    ) {
        val chat = (chatState.value as? UiState.Success)?.data ?: return
        if (!globalSettings.isConfiguredAndEnabled || !chatAiConfig.enabled) {
            _errorBanner.value = "Activa primero un proveedor LLM en el menú derecho y en la ficha de este chat."
            return
        }
        val currentMessages = (messagesState.value as? UiState.Success)?.data.orEmpty().let { list ->
            val cleanExtra = extraPendingUserMessage?.trim().orEmpty()
            if (cleanExtra.isNotEmpty()) {
                list + FakeMessage(
                    id = "pending_local",
                    senderRole = SenderRole.ME.name,
                    senderName = myDefaultName,
                    text = cleanExtra,
                    messageType = MessageType.TEXT.name
                )
            } else {
                list
            }
        }
        val preferredSpeaker = if (chat.groupChat) {
            _customGroupSenderName.value.trim().takeIf { it.isNotEmpty() }
        } else {
            chat.contactName
        }

        aiReplyJob?.cancel()
        aiReplyJob = viewModelScope.launch {
            _isGeneratingAiReply.value = true
            val result = ExternalLlmClient.generateCharacterReply(
                settings = globalSettings,
                chat = chat,
                chatConfig = chatAiConfig,
                recentMessages = currentMessages,
                myDefaultName = myDefaultName,
                preferredGroupSpeaker = preferredSpeaker,
                sensitiveFilterEnabled = sensitiveFilterEnabled
            )
            _isGeneratingAiReply.value = false
            result.onSuccess { reply ->
                if (sensitiveFilterEnabled && SensitiveContentFilter.containsSensitiveContent(reply.text)) {
                    _errorBanner.value = "El filtro de edad (+18) bloqueó la respuesta del LLM porque contenía texto sensible."
                    return@onSuccess
                }
                val timeLabel = _customTime.value.trim().ifBlank { currentClockTime() }
                val dateLabel = _customDateLabel.value.trim().ifBlank { "HOY" }
                val orderIdx = nextOrderIndex()
                repository.sendMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    senderRole = SenderRole.OTHER,
                    senderName = reply.speakerName,
                    text = reply.text,
                    messageType = MessageType.TEXT,
                    displayTime = timeLabel,
                    dateLabel = dateLabel,
                    tickStatus = TickStatus.READ,
                    orderIndex = orderIdx
                ).onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "No se pudo guardar la respuesta del LLM"
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Error al conectar con el LLM externo"
            }
        }
    }

    fun updateMessage(
        message: FakeMessage,
        newText: String,
        newTime: String,
        newDateLabel: String,
        newRole: SenderRole,
        newSenderName: String,
        newTick: TickStatus,
        newReaction: String?,
        context: Context? = null,
        replacementMediaUri: Uri? = null,
        clearMedia: Boolean = false
    ) {
        viewModelScope.launch {
            if (replacementMediaUri != null && context != null) {
                _isImportingMedia.value = true
                val imported = MediaImportHelper.importChatMediaFromGallery(context, replacementMediaUri)
                if (imported == null) {
                    _isImportingMedia.value = false
                    _errorBanner.value = "No se pudo procesar la foto, GIF o vídeo seleccionado"
                    return@launch
                }
                val finalText = newText.trim().ifBlank {
                    when {
                        imported.messageType == MessageType.VIDEO -> "🎬 Vídeo"
                        MediaImportHelper.isGifFile(null, imported.fileName, imported.localPath) -> "🎞️ GIF"
                        else -> "📷 Foto"
                    }
                }
                repository.updateMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    messageId = message.id,
                    text = finalText,
                    displayTime = newTime,
                    dateLabel = newDateLabel,
                    senderRole = newRole,
                    senderName = newSenderName,
                    tickStatus = newTick,
                    reactionEmoji = newReaction,
                    messageType = imported.messageType,
                    mediaPreviewBase64 = imported.previewBase64,
                    localMediaPath = imported.localPath,
                    mediaFileName = imported.fileName,
                    mediaDuration = imported.durationText,
                    clearMedia = false
                ).onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Error al reemplazar con multimedia"
                }
                _isImportingMedia.value = false
            } else {
                val finalText = if (clearMedia) {
                    newText.trim().ifBlank { "Mensaje" }
                } else {
                    newText
                }
                repository.updateMessage(
                    userId = currentUserId,
                    chatId = chatId,
                    messageId = message.id,
                    text = finalText,
                    displayTime = newTime,
                    dateLabel = newDateLabel,
                    senderRole = newRole,
                    senderName = newSenderName,
                    tickStatus = newTick,
                    reactionEmoji = newReaction,
                    clearMedia = clearMedia
                ).onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Error al actualizar mensaje"
                }
            }
        }
    }

    fun toggleMessageReaction(message: FakeMessage, emoji: String) {
        val nextReaction = if (message.reactionEmoji == emoji) null else emoji
        updateMessage(
            message = message,
            newText = message.text,
            newTime = message.displayTime,
            newDateLabel = message.dateLabel,
            newRole = message.parsedSenderRole,
            newSenderName = message.senderName,
            newTick = message.parsedTickStatus,
            newReaction = nextReaction
        )
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(currentUserId, chatId, messageId)
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "No se pudo eliminar el mensaje"
                }
        }
    }

    fun updateChatSettings(
        contactName: String,
        groupChat: Boolean,
        statusText: String,
        online: Boolean,
        verified: Boolean,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        clearAvatarPhoto: Boolean = false,
        wallpaperTheme: WallpaperTheme,
        unreadCount: Long,
        pinned: Boolean,
        groupMembersJson: String? = null,
        clearGroupMembers: Boolean = false,
        pendingTxtImport: PendingTxtImport? = null
    ) {
        viewModelScope.launch {
            if (pendingTxtImport != null && pendingTxtImport.messages.isNotEmpty()) {
                _isImportingMedia.value = true
            }
            val metaResult = repository.updateChatMeta(
                userId = currentUserId,
                chatId = chatId,
                contactName = contactName,
                groupChat = groupChat,
                statusText = statusText,
                online = online,
                verified = verified,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                avatarImageBase64 = avatarImageBase64,
                clearAvatarPhoto = clearAvatarPhoto,
                wallpaperTheme = wallpaperTheme,
                unreadCount = unreadCount,
                pinned = pinned,
                groupMembersJson = groupMembersJson,
                clearGroupMembers = clearGroupMembers
            )
            metaResult.onSuccess {
                if (pendingTxtImport != null && pendingTxtImport.messages.isNotEmpty()) {
                    repository.importWhatsAppMessagesIntoChat(
                        userId = currentUserId,
                        chatId = chatId,
                        parsedMessages = pendingTxtImport.messages,
                        myParticipantName = pendingTxtImport.selectedMe,
                        replaceExisting = pendingTxtImport.replaceExisting
                    ).onFailure { err ->
                        _errorBanner.value = err.localizedMessage ?: "Opciones guardadas, pero no se pudieron importar los archivos .txt"
                    }
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "No se pudo guardar el chat"
            }
            _isImportingMedia.value = false
        }
    }

    private companion object {
        const val TAG = "ChatRoomVM"
    }
}
