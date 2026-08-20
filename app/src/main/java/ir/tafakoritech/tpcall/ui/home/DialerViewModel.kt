package ir.tafakoritech.tpcall.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.ApiErrorKind
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import ir.tafakoritech.tpcall.util.ExtensionValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LookupState {
    IDLE,
    LOADING,
    FOUND,
    NOT_FOUND,
}

/**
 * Single source of truth for the dialer screen.
 *
 * Validation (3-digit limit, self-call) happens centrally here; lookup is
 * debounced and only runs for a stable complete extension.
 */
class DialerViewModel(
    private val controller: CallController,
    private val authApi: AuthGateway,
    private val token: String,
) : ViewModel() {

    data class DialerUiState(
        val extensionInput: String = "",
        val lookup: LookupState = LookupState.IDLE,
        val resolvedName: String? = null,
        val resolvedOnline: Boolean? = null,
        val signalingConnected: Boolean = false,
        val isSelfCall: Boolean = false,
    )

    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null

    init {
        viewModelScope.launch {
            controller.signalingStatus.collect { status ->
                _uiState.update { it.copy(signalingConnected = status == SignalingStatus.CONNECTED) }
            }
        }
        refreshSelfCall()
    }

    private fun refreshSelfCall() {
        _uiState.update {
            it.copy(isSelfCall = it.extensionInput.isNotEmpty() &&
                it.extensionInput == controller.myUser?.extension)
        }
    }

    fun onDigitPressed(digit: Char) {
        val current = _uiState.value.extensionInput
        if (current.length >= 3) return
        if (digit !in '0'..'9') return
        _uiState.update { it.copy(extensionInput = current + digit) }
        refreshSelfCall()
        scheduleLookup()
    }

    fun onBackspacePressed() {
        _uiState.update { it.copy(extensionInput = it.extensionInput.dropLast(1)) }
        refreshSelfCall()
        invalidateLookup()
    }

    fun onClearPressed() {
        _uiState.update {
            it.copy(
                extensionInput = "",
                lookup = LookupState.IDLE,
                resolvedName = null,
                resolvedOnline = null,
                isSelfCall = false,
            )
        }
    }

    /** Whether the Call action is available right now. */
    fun isCallEnabled(): Boolean {
        val s = _uiState.value
        return s.signalingConnected &&
            ExtensionValidator.isValidExtension(s.extensionInput) &&
            !s.isSelfCall
    }

    fun onCallPressed() {
        val ext = _uiState.value.extensionInput
        if (!isCallEnabled()) return
        controller.startCall(ext)
    }

    /**
     * Fills the keypad with a complete extension coming from call history
     * (row tap -> inspect/dial). Only accepts a valid 3-digit extension.
     */
    fun prefill(extension: String) {
        if (!ExtensionValidator.isValidExtension(extension)) return
        _uiState.update {
            it.copy(
                extensionInput = extension,
                lookup = LookupState.IDLE,
                resolvedName = null,
                resolvedOnline = null,
            )
        }
        refreshSelfCall()
        scheduleLookup()
    }

    private fun scheduleLookup() {
        lookupJob?.cancel()
        val ext = _uiState.value.extensionInput
        if (!ExtensionValidator.isValidExtension(ext)) {
            _uiState.update {
                it.copy(lookup = LookupState.IDLE, resolvedName = null, resolvedOnline = null)
            }
            return
        }
        _uiState.update { it.copy(lookup = LookupState.LOADING, resolvedName = null, resolvedOnline = null) }
        lookupJob = viewModelScope.launch {
            delay(300) // debounce; do not hit the backend for every keystroke
            if (_uiState.value.extensionInput != ext) return@launch // stale input
            runCatching { authApi.user(ext, token) }.fold(
                onSuccess = { user ->
                    if (_uiState.value.extensionInput != ext) return@fold
                    _uiState.update {
                        it.copy(
                            lookup = LookupState.FOUND,
                            resolvedName = user.name,
                            resolvedOnline = user.online,
                        )
                    }
                },
                onFailure = { e ->
                    if (_uiState.value.extensionInput != ext) return@fold
                    val notFound = e is ApiException && e.kind == ApiErrorKind.NOT_FOUND
                    _uiState.update {
                        it.copy(lookup = if (notFound) LookupState.NOT_FOUND else LookupState.IDLE)
                    }
                },
            )
        }
    }

    private fun invalidateLookup() {
        lookupJob?.cancel()
        _uiState.update {
            it.copy(lookup = LookupState.IDLE, resolvedName = null, resolvedOnline = null)
        }
    }

    companion object {
        fun factory(controller: CallController, authApi: AuthGateway, token: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DialerViewModel(controller, authApi, token) }
            }
    }
}
