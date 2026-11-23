package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.ExternalSessionConfig
import com.example.data.ChatDataRepository
import com.example.data.model.FakeChat
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WallpaperTheme
import com.example.media.ParsedWhatsAppChat
import com.example.media.RemoteBackupClient
import com.example.media.WhatsAppTxtParser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter(val labelEs: String) {
    ALL("Todos"),
    CHATS("Chats"),
    GROUPS("Grupos"),
    PINNED("Fijados")
}

class ChatsHomeViewModel(
    private val repository: ChatDataRepository,
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?
) : ViewModel() {

    val chatsState: StateFlow<UiState<List<FakeChat>>> = repository.observeChats(currentUserId)
        .map<List<FakeChat>, UiState<List<FakeChat>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chats", rootError)
            emit(UiState.Error(rootError.message ?: "Error al sincronizar chats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(ChatFilter.ALL)
    val activeFilter = _activeFilter.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ChatFilter) {
        _activeFilter.value = filter
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun emitStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun createChat(
        contactName: String,
        groupChat: Boolean,
        statusText: String,
        online: Boolean,
        verified: Boolean,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        wallpaperTheme: WallpaperTheme,
        unreadCount: Long,
        pinned: Boolean,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createChat(
                userId = currentUserId,
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
                pinned = pinned
            )
            _isBusy.value = false
            result.onSuccess { chatId ->
                onCreated(chatId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "No se pudo crear el chat"
            }
        }
    }

    fun createSampleChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createSampleConversation(currentUserId)
            _isBusy.value = false
            result.onSuccess { chatId ->
                onCreated(chatId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "No se pudo crear el chat de ejemplo"
            }
        }
    }

    fun togglePinned(chat: FakeChat) {
        viewModelScope.launch {
            repository.updateChatMeta(
                userId = currentUserId,
                chatId = chat.id,
                pinned = !chat.pinned
            ).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "No se pudo actualizar"
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(currentUserId, chatId)
                .onSuccess { _statusMessage.value = "Conversación eliminada" }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Error al eliminar el chat"
                }
        }
    }

    fun updateDefaultMyName(name: String) {
        viewModelScope.launch {
            repository.updateDefaultMyName(currentUserId, name)
                .onSuccess { _statusMessage.value = "Nombre predeterminado actualizado" }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "No se pudo guardar el nombre"
                }
        }
    }

    fun prepareExportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.exportWorkspaceToJson(currentUserId)
            _isBusy.value = false
            result.onSuccess(onReady)
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Error al preparar copia JSON"
                }
        }
    }

    fun importWorkspaceJson(jsonString: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importWorkspaceFromJson(currentUserId, jsonString)
            _isBusy.value = false
            result.onSuccess { count ->
                _statusMessage.value = "Se restauraron $count conversaciones"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Archivo de respaldo inválido"
            }
        }
    }

    fun importWhatsAppChat(
        parsed: ParsedWhatsAppChat,
        chatTitle: String,
        myParticipantName: String?,
        isGroup: Boolean,
        onImported: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importWhatsAppChat(
                userId = currentUserId,
                parsed = parsed,
                chatTitle = chatTitle,
                myParticipantName = myParticipantName,
                isGroup = isGroup
            )
            _isBusy.value = false
            result.onSuccess { (chatId, count) ->
                _statusMessage.value = "Chat de WhatsApp importado ($count mensajes)"
                onImported(chatId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "No se pudo importar el chat .txt"
            }
        }
    }

    fun importFromExternalSession(
        context: Context,
        config: ExternalSessionConfig,
        onImportedChat: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val contentResult = when {
                config.savedDocumentUri.isNotBlank() -> {
                    RemoteBackupClient.readTextFromContentUri(context, Uri.parse(config.savedDocumentUri))
                }
                config.hasHttpRoute -> {
                    RemoteBackupClient.fetchRemoteText(
                        routeUrl = config.remoteRoute,
                        username = config.authUsername,
                        secret = config.authSecret
                    )
                }
                else -> Result.failure(
                    IllegalArgumentException("Configura una URL https:// o selecciona un archivo desde ${config.provider.displayName}")
                )
            }

            contentResult.onSuccess { rawText ->
                val trimmed = rawText.trimStart()
                if (trimmed.startsWith("{")) {
                    val isBackupJson = try {
                        JSONObject(trimmed).has("chats")
                    } catch (_: Exception) {
                        false
                    }
                    if (isBackupJson) {
                        repository.importWorkspaceFromJson(currentUserId, rawText)
                            .onSuccess { count ->
                                _statusMessage.value = "Se restauraron $count chats desde ${config.provider.displayName}"
                            }
                            .onFailure { err ->
                                _statusMessage.value = err.localizedMessage ?: "El archivo JSON no es un respaldo válido"
                            }
                        _isBusy.value = false
                        return@launch
                    }
                }

                val parsedResult = WhatsAppTxtParser.parseText(
                    rawText = rawText,
                    fileName = config.remoteRoute.substringAfterLast('/')
                )
                parsedResult.onSuccess { parsed ->
                    val defaultTitle = parsed.suggestedTitle.ifBlank { config.provider.displayName }
                    val myParticipant = parsed.participants.firstOrNull {
                        it.equals(config.accountLabel, ignoreCase = true)
                    }
                    repository.importWhatsAppChat(
                        userId = currentUserId,
                        parsed = parsed,
                        chatTitle = defaultTitle,
                        myParticipantName = myParticipant,
                        isGroup = parsed.isLikelyGroup
                    ).onSuccess { (chatId, count) ->
                        _statusMessage.value = "Chat importado desde ${config.provider.displayName} ($count mensajes)"
                        onImportedChat(chatId)
                    }.onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "No se pudo guardar el chat importado"
                    }
                }.onFailure {
                    _statusMessage.value = "El archivo de ${config.provider.displayName} no es un respaldo .json ni un chat .txt válido"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "No se pudo acceder a la ruta de ${config.provider.displayName}"
            }
            _isBusy.value = false
        }
    }

    fun uploadBackupToExternalRoute(config: ExternalSessionConfig) {
        viewModelScope.launch {
            if (!config.hasHttpRoute) {
                _statusMessage.value = "Configura una URL https:// o WebDAV en ${config.provider.displayName} para subir directamente"
                return@launch
            }
            _isBusy.value = true
            val jsonResult = repository.exportWorkspaceToJson(currentUserId)
            jsonResult.onSuccess { json ->
                RemoteBackupClient.uploadRemoteBackup(
                    routeUrl = config.remoteRoute,
                    jsonPayload = json,
                    username = config.authUsername,
                    secret = config.authSecret
                ).onSuccess {
                    _statusMessage.value = "Copia subida correctamente a ${config.provider.displayName}"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Error al subir la copia"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Error al preparar el respaldo"
            }
            _isBusy.value = false
        }
    }

    private companion object {
        const val TAG = "ChatsHomeVM"
    }
}
