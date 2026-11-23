package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatDataRepository
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.FakeCallRecord
import com.example.data.model.UiState
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

data class ActiveFakeCall(
    val contactName: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val avatarImageBase64: String? = null,
    val callType: CallType,
    val isAnswered: Boolean = false
)

class CallsViewModel(
    private val repository: ChatDataRepository,
    private val currentUserId: String
) : ViewModel() {

    val callsState: StateFlow<UiState<List<FakeCallRecord>>> = repository.observeFakeCalls(currentUserId)
        .map<List<FakeCallRecord>, UiState<List<FakeCallRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing fake calls", rootError)
            emit(UiState.Error(rootError.message ?: "Error al cargar llamadas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _activeCall = MutableStateFlow<ActiveFakeCall?>(null)
    val activeCall = _activeCall.asStateFlow()

    private val _scheduledSecondsLeft = MutableStateFlow<Int?>(null)
    val scheduledSecondsLeft = _scheduledSecondsLeft.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private var timerJob: Job? = null

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun emitStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun launchFakeCall(
        contactName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        callType: CallType,
        delaySeconds: Int = 0
    ) {
        timerJob?.cancel()
        if (delaySeconds <= 0) {
            _scheduledSecondsLeft.value = null
            _activeCall.value = ActiveFakeCall(
                contactName = contactName,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                avatarImageBase64 = avatarImageBase64,
                callType = callType
            )
        } else {
            timerJob = viewModelScope.launch {
                for (sec in delaySeconds downTo 1) {
                    _scheduledSecondsLeft.value = sec
                    delay(1000L)
                }
                _scheduledSecondsLeft.value = null
                _activeCall.value = ActiveFakeCall(
                    contactName = contactName,
                    avatarEmoji = avatarEmoji,
                    avatarColorHex = avatarColorHex,
                    avatarImageBase64 = avatarImageBase64,
                    callType = callType
                )
            }
        }
    }

    fun cancelScheduledCall() {
        timerJob?.cancel()
        _scheduledSecondsLeft.value = null
    }

    fun answerActiveCall() {
        _activeCall.value = _activeCall.value?.copy(isAnswered = true)
    }

    fun endActiveCall(durationSeconds: Int) {
        val current = _activeCall.value ?: return
        _activeCall.value = null
        val formattedDuration = if (current.isAnswered) {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            String.format(Locale.US, "%02d:%02d", mins, secs)
        } else {
            "Sin respuesta"
        }
        val direction = if (current.isAnswered) CallDirection.INCOMING else CallDirection.MISSED
        val timeText = "Hoy, ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
        viewModelScope.launch {
            repository.addFakeCallRecord(
                userId = currentUserId,
                contactName = current.contactName,
                avatarEmoji = current.avatarEmoji,
                avatarColorHex = current.avatarColorHex,
                avatarImageBase64 = current.avatarImageBase64,
                callType = current.callType,
                direction = direction,
                durationText = formattedDuration,
                displayTime = timeText
            )
        }
    }

    fun addManualCallLog(
        contactName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImageBase64: String? = null,
        callType: CallType,
        direction: CallDirection,
        durationText: String,
        displayTime: String
    ) {
        viewModelScope.launch {
            repository.addFakeCallRecord(
                userId = currentUserId,
                contactName = contactName,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                avatarImageBase64 = avatarImageBase64,
                callType = callType,
                direction = direction,
                durationText = durationText,
                displayTime = displayTime
            ).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Error al guardar llamada"
            }
        }
    }

    fun deleteCallRecord(callId: String) {
        viewModelScope.launch {
            repository.deleteFakeCallRecord(currentUserId, callId)
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Error al eliminar llamada"
                }
        }
    }

    private companion object {
        const val TAG = "CallsVM"
    }
}
