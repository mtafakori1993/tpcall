package ir.tafakoritech.tpcall.call

import android.util.Log
import ir.tafakoritech.tpcall.config.AppConfig
import ir.tafakoritech.tpcall.data.api.ApiErrorKind
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.model.IceServerDto
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.data.session.SessionStorage
import ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import ir.tafakoritech.tpcall.data.signaling.WsEnvelope
import ir.tafakoritech.tpcall.data.signaling.WsMessage
import ir.tafakoritech.tpcall.data.signaling.toWsMessage
import ir.tafakoritech.tpcall.telecom.TelecomEvents
import ir.tafakoritech.tpcall.telecom.TelecomGateway
import ir.tafakoritech.tpcall.telecom.TpCallInfo
import ir.tafakoritech.tpcall.telecom.TpDisconnectCause
import ir.tafakoritech.tpcall.telecom.TpEndpointType
import ir.tafakoritech.tpcall.util.ErrorMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Application-wide session state. */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class Authenticated(val user: UserDto) : SessionState
}

data class UiFlash(val id: Long, val text: String)

/**
 * The single authoritative orchestrator for the whole app:
 *  - session (login/register/logout/validation)
 *  - signaling connection lifecycle
 *  - call state machine + WebRTC negotiation
 *  - Core-Telecom integration (every TP Call call is also a Telecom call)
 *
 * All state mutations happen on the injected [scope] (main dispatcher in
 * production) so WebRTC/WebSocket/Telecom callbacks stay serialized with UI
 * actions.
 *
 * There is exactly one application-level entry point per action:
 *  - [startCall]   (keypad / contacts / history / system)
 *  - [acceptCurrentCall]
 *  - [rejectCurrentCall]
 *  - [endCurrentCall]
 *
 * These are idempotent: calling them twice never sends a duplicate WebSocket
 * command, never tears down WebRTC/Telecom twice, and never crashes.
 */
class CallController(
    private val scope: CoroutineScope,
    private val sessionStore: SessionStorage,
    private val authApi: AuthGateway,
    private val signalingFactory: (String) -> SignalingGateway,
    private val rtc: RtcGateway,
    private val audio: CallAudio,
    private val notifier: CallNotifier,
    private val telecom: TelecomGateway,
    private val micPermissionProvider: () -> Boolean = { true },
) {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _signalingStatus = MutableStateFlow(SignalingStatus.DISCONNECTED)
    val signalingStatus: StateFlow<SignalingStatus> = _signalingStatus.asStateFlow()

    private val _connectionState = MutableStateFlow<SignalingConnectionState>(
        SignalingConnectionState.Disconnected,
    )
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _flash = MutableStateFlow<UiFlash?>(null)
    val flash: StateFlow<UiFlash?> = _flash.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private var signaling: SignalingGateway? = null
    private var messageJob: Job? = null
    private var statusJob: Job? = null
    private var connectionStateJob: Job? = null
    private var negotiationTimeoutJob: Job? = null
    private var signalingRecoveryJob: Job? = null
    private var flashJob: Job? = null
    private var iceServers: List<IceServerDto> = emptyList()
    private var iceServersFetchedAtMs: Long = 0L
    private var lastCallRequestId: String? = null

    val myUser: UserDto?
        get() = (_sessionState.value as? SessionState.Authenticated)?.user

    private val rtcListener = object : RtcListener {
        override fun onOfferReady(sdp: String) {
            val state = _callState.value
            if (state !is CallState.Connecting) return
            signaling?.sendMessage("offer", null, buildJsonObject {
                put("call_id", state.callId)
                put("sdp", sdp)
            })
            Log.i(TAG, "webrtc_offer_created")
        }

        override fun onAnswerReady(sdp: String) {
            val state = _callState.value
            if (state !is CallState.Connecting) return
            signaling?.sendMessage("answer", null, buildJsonObject {
                put("call_id", state.callId)
                put("sdp", sdp)
            })
            Log.i(TAG, "webrtc_answer_created")
        }

        override fun onLocalIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
            val callId = currentCallId() ?: return
            signaling?.sendMessage("ice_candidate", null, buildJsonObject {
                put("call_id", callId)
                put("candidate", buildJsonObject {
                    put("sdpMid", sdpMid ?: "")
                    put("sdpMLineIndex", sdpMLineIndex)
                    put("candidate", candidate)
                })
            })
        }

        override fun onConnected() {
            val state = _callState.value
            if (state !is CallState.Connecting) return
            Log.i(TAG, "webrtc_connected call_id=${state.callId}")
            negotiationTimeoutJob?.cancel()
            _callState.value = CallState.InCall(
                callId = state.callId,
                peerExtension = state.peerExtension,
                peerName = state.peerName,
                startedAt = System.currentTimeMillis(),
            )
            // Media is up: tell Telecom the call is active.
            telecom.setActive(state.callId)
            audio.setTelecomManaged(telecom.hasCall(state.callId))
        }

        override fun onFailed(error: String) {
            val state = _callState.value
            if (!state.isActive) return
            Log.w(TAG, "webrtc_failed $error")
            val callId = state.currentCallId()
            if (callId != null) {
                signaling?.sendMessage("hangup", null, buildJsonObject { put("call_id", callId) })
            }
            flash("برقراری تماس ناموفق بود")
            terminateWithCause(TpDisconnectCause.LOCAL)
        }
    }

    private val telecomEvents = object : TelecomEvents {
        override fun onSystemAnswer(callId: String) {
            // System surface (headset / watch / notification / Telecom UI)
            // answered the incoming call. Route through the single accept path.
            val state = _callState.value
            if (state !is CallState.IncomingRinging || state.callId != callId) return
            Log.i(TAG, "telecom_system_answer call_id=$callId")
            acceptCurrentCall()
        }

        override fun onSystemDisconnect(callId: String, cause: TpDisconnectCause) {
            val state = _callState.value
            if (state.currentCallId() != callId) return
            Log.i(TAG, "telecom_system_disconnect call_id=$callId cause=$cause")
            if (state is CallState.IncomingRinging) {
                rejectCurrentCall()
            } else {
                endCurrentCall()
            }
        }

        override fun onSystemSetActive(callId: String) {
            val state = _callState.value
            if (state.currentCallId() != callId) return
            // Telecom un-held the call (e.g. a SIM call ended). Resume media if
            // we were inactive; do not fabricate InCall before WebRTC is ready.
            Log.i(TAG, "telecom_system_set_active call_id=$callId")
            audio.setTelecomManaged(telecom.hasCall(callId))
            if (state is CallState.InCall || state is CallState.Connecting) {
                audio.startCallAudio()
            }
        }

        override fun onSystemSetInactive(callId: String) {
            val state = _callState.value
            if (state.currentCallId() != callId) return
            // Telecom put the call on hold (e.g. a SIM call was answered).
            // Stop using the microphone/media until the call is active again.
            Log.i(TAG, "telecom_system_set_inactive call_id=$callId")
            audio.stopCallAudio()
        }

        override fun onEndpointChanged(callId: String, endpoint: TpEndpointType) {
            val state = _callState.value
            if (state.currentCallId() != callId) return
            // Reflect the Telecom-chosen route in the Speaker button.
            _speakerOn.value = endpoint == TpEndpointType.SPEAKER
            Log.i(TAG, "telecom_endpoint_changed call_id=$callId endpoint=$endpoint")
        }

        override fun onMuteChanged(callId: String, muted: Boolean) {
            val state = _callState.value
            if (state.currentCallId() != callId) return
            _muted.value = muted
            rtc.setMuted(muted)
            Log.i(TAG, "telecom_mute_changed call_id=$callId muted=$muted")
        }

        override fun onCallRemoved(callId: String) {
            // Safety net: if Telecom dropped the session but the app still
            // believes the call is active, terminate cleanly.
            val state = _callState.value
            if (state.currentCallId() == callId && state.isActive) {
                Log.i(TAG, "telecom_call_removed_cleanup call_id=$callId")
                cleanupCurrentCall()
            }
        }
    }

    init {
        rtc.listener = rtcListener
        telecom.setListener(telecomEvents)
    }

    // ---------------------------------------------------------------------
    // Session
    // ---------------------------------------------------------------------

    /** Reads the stored session and validates the token against GET /me. */
    fun initialize() {
        scope.launch {
            val session = sessionStore.load()
            if (session == null) {
                _sessionState.value = SessionState.LoggedOut
                return@launch
            }
            val result = runCatching { authApi.me(session.token) }
            result.onSuccess { user ->
                sessionStore.save(session.token, user)
                _sessionState.value = SessionState.Authenticated(user)
                Log.i(TAG, "auth_me_valid")
            }.onFailure { e ->
                if (e is ApiException && e.kind == ApiErrorKind.UNAUTHORIZED) {
                    sessionStore.clear()
                    _sessionState.value = SessionState.LoggedOut
                    Log.i(TAG, "auth_session_invalid_cleared")
                } else {
                    // Transient failure: keep the session; the WebSocket will
                    // re-establish and the backend will reject bad tokens.
                    _sessionState.value = SessionState.Authenticated(session.user)
                }
            }
        }
    }

    fun login(extension: String, password: String) {
        if (_authBusy.value) return
        _authBusy.value = true
        _authError.value = null
        scope.launch {
            val result = runCatching { authApi.login(extension.trim(), password) }
            _authBusy.value = false
            result.onSuccess { response ->
                sessionStore.save(response.accessToken, response.user)
                _sessionState.value = SessionState.Authenticated(response.user)
                Log.i(TAG, "auth_login_success extension=${response.user.extension}")
            }.onFailure { e ->
                _authError.value = userMessageFor(e)
                Log.i(TAG, "auth_login_failed")
            }
        }
    }

    fun register(name: String, extension: String, password: String) {
        if (_authBusy.value) return
        _authBusy.value = true
        _authError.value = null
        scope.launch {
            val result = runCatching { authApi.register(name.trim(), extension, password) }
            _authBusy.value = false
            result.onSuccess { response ->
                sessionStore.save(response.accessToken, response.user)
                _sessionState.value = SessionState.Authenticated(response.user)
                Log.i(TAG, "auth_register_success extension=${response.user.extension}")
            }.onFailure { e ->
                _authError.value = userMessageFor(e)
                Log.i(TAG, "auth_register_failed")
            }
        }
    }

    fun logout() {
        Log.i(TAG, "auth_logout")
        terminateWithCause(TpDisconnectCause.LOCAL)
        stopSignaling()
        sessionStore.clear()
        _sessionState.value = SessionState.LoggedOut
    }

    /** Ends everything and returns to the login screen, showing [message]. */
    private fun forceLogout(message: String?) {
        if (message != null) flash(message)
        terminateWithCause(TpDisconnectCause.LOCAL)
        stopSignaling()
        sessionStore.clear()
        _sessionState.value = SessionState.LoggedOut
    }

    private fun userMessageFor(e: Throwable): String {
        return (e as? ApiException)?.let { ex ->
            ErrorMapper.mapError(ex.code) ?: ex.userMessage
        } ?: "ارتباط با سرور برقرار نیست"
    }

    // ---------------------------------------------------------------------
    // Signaling lifecycle
    // ---------------------------------------------------------------------

    /** Connects the authenticated WebSocket. Idempotent; one socket per session. */
    fun startSignaling() {
        val session = sessionStore.load() ?: return
        if (signaling != null) return
        val client = signalingFactory(session.token)
        signaling = client
        _signalingStatus.value = SignalingStatus.CONNECTING
        _connectionState.value = SignalingConnectionState.Connecting
        client.start(session.token)

        messageJob = scope.launch {
            client.messages.collect { handleEnvelope(it) }
        }
        statusJob = scope.launch {
            client.status.collect { status ->
                _signalingStatus.value = status
                if (status == SignalingStatus.AUTH_FAILED) {
                    forceLogout(ErrorMapper.mapError(ErrorMapper.SESSION_REPLACED))
                }
            }
        }
        connectionStateJob = scope.launch {
            client.connectionState.collect { state ->
                _connectionState.value = state
                handleConnectionRecovery(state)
            }
        }
        scope.launch { refreshIceServers(session.token) }
    }

    private suspend fun refreshIceServers(token: String) {
        runCatching { authApi.webRtcConfig(token) }
            .onSuccess {
                iceServers = it.iceServers
                iceServersFetchedAtMs = System.currentTimeMillis()
            }
            .onFailure { e -> Log.w(TAG, "webrtc_config_fetch_failed ${e.message}") }
    }

    /**
     * TURN credentials are short-lived (the backend issues them for 24h), so a config fetched
     * when signaling started can expire in a long-running session. Refetch when it is stale
     * or was never fetched; on failure the cached servers are kept.
     */
    private suspend fun refreshIceServersIfStale() {
        val age = System.currentTimeMillis() - iceServersFetchedAtMs
        if (iceServersFetchedAtMs != 0L && age < ICE_CONFIG_MAX_AGE_MS) return
        val token = sessionStore.load()?.token ?: return
        refreshIceServers(token)
    }

    fun stopSignaling() {
        messageJob?.cancel()
        messageJob = null
        statusJob?.cancel()
        statusJob = null
        connectionStateJob?.cancel()
        connectionStateJob = null
        signalingRecoveryJob?.cancel()
        signalingRecoveryJob = null
        signaling?.stop()
        signaling = null
        _signalingStatus.value = SignalingStatus.DISCONNECTED
        _connectionState.value = SignalingConnectionState.Disconnected
    }

    /**
     * Bounded grace period while an active call's signaling is temporarily
     * lost. WebRTC media may survive a brief signaling drop, so we do not kill
     * the call instantly; if signaling does not reconnect within the grace
     * window we end the call locally to avoid an unknown state.
     */
    private fun handleConnectionRecovery(state: SignalingConnectionState) {
        when (state) {
            SignalingConnectionState.Connected -> {
                signalingRecoveryJob?.cancel()
                signalingRecoveryJob = null
            }
            is SignalingConnectionState.Reconnecting,
            SignalingConnectionState.Connecting,
            SignalingConnectionState.Disconnected,
            -> {
                val call = _callState.value
                if (call.isActive && signalingRecoveryJob?.isActive != true) {
                    Log.i(TAG, "signaling_recovery_grace_started call_id=${call.currentCallId()}")
                    signalingRecoveryJob = scope.launch {
                        delay(AppConfig.SIGNALING_RECOVERY_GRACE_MS)
                        val current = _callState.value
                        if (current.isActive &&
                            _connectionState.value !is SignalingConnectionState.Connected
                        ) {
                            Log.i(
                                TAG,
                                "signaling_recovery_grace_expired call_id=${current.currentCallId()}",
                            )
                            flash("ارتباط با سرور برقرار نشد")
                            terminateWithCause(TpDisconnectCause.LOCAL)
                        }
                        signalingRecoveryJob = null
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Call actions (UI entry points; main thread)
    // ---------------------------------------------------------------------

    /**
     * The single application-level call starter. Used by the keypad, corporate
     * contacts, call history, and any system callback that starts a call.
     * Validates locally, then asks the backend to ring [extension].
     */
    fun startCall(extension: String) {
        if (_callState.value.isActive) return
        if (_signalingStatus.value != SignalingStatus.CONNECTED) {
            flash("در حال اتصال به سرور…")
            return
        }
        if (!micPermissionProvider()) {
            flash("برای مکالمه دسترسی میکروفون لازم است")
            return
        }
        val me = myUser ?: return
        if (extension == me.extension) {
            flash("نمی‌توانید با داخلی خودتان تماس بگیرید")
            return
        }
        _callState.value = CallState.OutgoingRinging(callId = null, extension = extension, name = null)
        lastCallRequestId = UUID.randomUUID().toString()
        signaling?.sendMessage(
            type = "call",
            requestId = lastCallRequestId,
            payload = buildJsonObject { put("to", extension) },
        )
        Log.i(TAG, "outgoing_call_started to=$extension")
    }

    /**
     * The single answer path. Both the TP Call UI (پاسخ) and the Telecom
     * system surfaces (notification / headset / watch) converge here.
     *
     * Idempotent: only acts while the current call is an incoming ringing call.
     */
    fun acceptCurrentCall() {
        val state = _callState.value
        if (state !is CallState.IncomingRinging) return
        if (!micPermissionProvider()) {
            // Never create a broken call without the microphone; stay ringing so
            // the user can grant permission and tap Accept again.
            flash("برای مکالمه دسترسی میکروفون لازم است")
            return
        }
        Log.i(TAG, "call_accept pressed call_id=${state.callId}")
        audio.stopRinging()
        notifier.hideIncoming(state.callId)
        audio.startCallAudio()
        audio.setTelecomManaged(telecom.hasCall(state.callId))
        _callState.value = CallState.Connecting(state.callId, state.extension, state.name)
        signaling?.sendMessage(
            type = "accept",
            requestId = null,
            payload = buildJsonObject { put("call_id", state.callId) },
        )
        telecom.answer(state.callId)
    }

    /**
     * The single reject path for a ringing incoming call.
     * Idempotent: only acts while the current call is an incoming ringing call.
     */
    fun rejectCurrentCall() {
        val state = _callState.value
        if (state !is CallState.IncomingRinging) return
        Log.i(TAG, "call_reject pressed call_id=${state.callId}")
        audio.stopRinging()
        notifier.hideIncoming(state.callId)
        signaling?.sendMessage(
            type = "reject",
            requestId = null,
            payload = buildJsonObject { put("call_id", state.callId) },
        )
        telecom.disconnect(state.callId, TpDisconnectCause.REJECTED)
        cleanupCurrentCall()
    }

    /**
     * The single hangup path. For a ringing incoming call this rejects; for an
     * active call it hangs up. Exactly one WebSocket command is sent per call
     * (never both), and the action is idempotent.
     */
    fun endCurrentCall() {
        val state = _callState.value
        if (!state.isActive) return
        val callId = state.currentCallId()
        when (state) {
            is CallState.IncomingRinging -> {
                audio.stopRinging()
                notifier.hideIncoming(state.callId)
                signaling?.sendMessage(
                    type = "reject",
                    requestId = null,
                    payload = buildJsonObject { put("call_id", state.callId) },
                )
                telecom.disconnect(state.callId, TpDisconnectCause.REJECTED)
            }
            else -> {
                if (callId != null) {
                    signaling?.sendMessage(
                        type = "hangup",
                        requestId = null,
                        payload = buildJsonObject { put("call_id", callId) },
                    )
                    telecom.disconnect(callId, TpDisconnectCause.LOCAL)
                }
            }
        }
        cleanupCurrentCall()
    }

    /**
     * Terminates the current call as if the user hung up, regardless of its
     * state, sending the appropriate WebSocket command and disconnecting the
     * Telecom session. Used by WebRTC failure and logout.
     */
    private fun terminateWithCause(cause: TpDisconnectCause) {
        val state = _callState.value
        if (!state.isActive) return
        val callId = state.currentCallId()
        when (state) {
            is CallState.IncomingRinging -> {
                audio.stopRinging()
                notifier.hideIncoming(state.callId)
                signaling?.sendMessage(
                    type = "reject",
                    requestId = null,
                    payload = buildJsonObject { put("call_id", state.callId) },
                )
                telecom.disconnect(state.callId, cause)
            }
            else -> {
                if (callId != null) {
                    signaling?.sendMessage(
                        type = "hangup",
                        requestId = null,
                        payload = buildJsonObject { put("call_id", callId) },
                    )
                    telecom.disconnect(callId, cause)
                }
            }
        }
        cleanupCurrentCall()
    }

    fun toggleMute() {
        val next = !_muted.value
        _muted.value = next
        rtc.setMuted(next)
        Log.i(TAG, "mute_toggled muted=$next")
    }

    /**
     * Speaker routing: when Core-Telecom manages the call, request the Telecom
     * audio endpoint instead of forcing AudioManager. Falls back to the audio
     * layer only when no Telecom session exists for the call.
     */
    fun toggleSpeaker() {
        val next = !_speakerOn.value
        _speakerOn.value = next
        val callId = currentCallId()
        if (callId != null && telecom.hasCall(callId)) {
            telecom.requestEndpoint(
                callId,
                if (next) TpEndpointType.SPEAKER else TpEndpointType.EARPIECE,
            )
        } else {
            audio.setSpeakerOn(next)
        }
        Log.i(TAG, "speaker_toggled on=$next")
    }

    // ---------------------------------------------------------------------
    // Incoming signaling
    // ---------------------------------------------------------------------

    private fun handleEnvelope(envelope: WsEnvelope) {
        when (val msg = envelope.toWsMessage()) {
            WsMessage.Ping -> signaling?.sendMessage("pong", null, buildJsonObject {})
            WsMessage.SessionReplaced -> forceLogout(ErrorMapper.mapError(ErrorMapper.SESSION_REPLACED))
            WsMessage.Unknown -> Log.i(TAG, "signaling_unknown_type_ignored")
            is WsMessage.CallResponse -> handleCallResponse(msg)
            is WsMessage.IncomingCall -> handleIncomingCall(msg)
            is WsMessage.Accepted -> handleAccepted(msg)
            is WsMessage.Rejected -> handleRejected(msg)
            is WsMessage.Busy -> handleBusy(msg)
            is WsMessage.Unavailable -> handleUnavailable(msg)
            is WsMessage.Offer -> handleOffer(msg)
            is WsMessage.Answer -> handleAnswer(msg)
            is WsMessage.IceCandidate -> handleIceCandidate(msg)
            is WsMessage.CallEnded -> handleCallEnded(msg)
            is WsMessage.WsError -> handleWsError(msg)
        }
    }

    private fun handleIncomingCall(msg: WsMessage.IncomingCall) {
        if (msg.callId.isBlank()) return
        val current = _callState.value
        if (current is CallState.IncomingRinging && current.callId == msg.callId) return
        if (current.isActive) {
            Log.i(TAG, "incoming_call_ignored_busy call_id=${msg.callId}")
            return
        }
        _callState.value = CallState.IncomingRinging(msg.callId, msg.fromExtension, msg.fromName)
        // Register the incoming call with Android Telecom before surfacing it.
        telecom.addIncomingCall(
            TpCallInfo(msg.callId, msg.fromName, msg.fromExtension),
        )
        audio.startRinging()
        notifier.showIncoming(msg.callId, msg.fromName, msg.fromExtension)
        Log.i(TAG, "incoming_call_received call_id=${msg.callId} peer_extension=${msg.fromExtension}")
    }

    private fun handleCallResponse(msg: WsMessage.CallResponse) {
        val state = _callState.value
        if (state !is CallState.OutgoingRinging) return
        if (lastCallRequestId != null && msg.requestId != null && msg.requestId != lastCallRequestId) {
            Log.i(TAG, "call_response_stale_request_ignored")
            return
        }
        if (msg.code != null) {
            handleCallStartError(msg.code, state.extension)
            return
        }
        val callId = msg.callId
        if (msg.status == "ringing" && callId != null) {
            _callState.value = state.copy(callId = callId, name = msg.toName ?: state.name)
            // Backend confirmed ringing with an authoritative call_id: register
            // the outgoing call with Android Telecom now.
            telecom.addOutgoingCall(
                TpCallInfo(callId, state.name ?: state.extension, state.extension),
            )
            Log.i(TAG, "outgoing_call_ringing call_id=$callId peer_extension=${state.extension}")
        }
    }

    private fun handleCallStartError(code: String, extension: String) {
        Log.w(TAG, "call_start_error code=$code")
        val text = when (code) {
            "busy" -> "داخلی $extension مشغول است"
            "unavailable" -> "داخلی $extension در دسترس نیست"
            else -> ErrorMapper.mapError(code) ?: "تماس برقرار نشد"
        }
        flash(text)
        val callId = currentCallId()
        if (callId != null) telecom.disconnect(callId, TpDisconnectCause.LOCAL)
        cleanupCurrentCall()
    }

    private fun handleAccepted(msg: WsMessage.Accepted) {
        val state = _callState.value
        if (state !is CallState.OutgoingRinging) return
        if (msg.callId != null && state.callId != null && msg.callId != state.callId) return
        val callId = state.callId ?: msg.callId ?: return
        Log.i(TAG, "call_accepted call_id=$callId")
        audio.startCallAudio()
        audio.setTelecomManaged(telecom.hasCall(callId))
        _callState.value = CallState.Connecting(callId, state.extension, state.name)
        startNegotiationTimeout(callId)
        scope.launch {
            refreshIceServersIfStale()
            rtc.beginCall(iceServers)
            rtc.createOffer()
        }
    }

    private fun handleOffer(msg: WsMessage.Offer) {
        val state = _callState.value
        if (state !is CallState.Connecting) return
        if (msg.callId != state.callId) {
            Log.i(TAG, "offer_stale_call_ignored call_id=${msg.callId}")
            return
        }
        val sdp = msg.sdp ?: return
        Log.i(TAG, "webrtc_offer_received call_id=${msg.callId}")
        startNegotiationTimeout(state.callId)
        scope.launch {
            refreshIceServersIfStale()
            rtc.beginCall(iceServers)
            rtc.applyRemoteOffer(sdp)
        }
    }

    private fun handleAnswer(msg: WsMessage.Answer) {
        val state = _callState.value
        if (state !is CallState.Connecting) return
        if (msg.callId != state.callId) {
            Log.i(TAG, "answer_stale_call_ignored call_id=${msg.callId}")
            return
        }
        val sdp = msg.sdp ?: return
        Log.i(TAG, "webrtc_answer_received call_id=${msg.callId}")
        rtc.setRemoteAnswer(sdp)
    }

    private fun handleIceCandidate(msg: WsMessage.IceCandidate) {
        val callId = currentCallId() ?: return
        if (msg.callId != callId) {
            Log.i(TAG, "ice_candidate_stale_call_ignored call_id=${msg.callId}")
            return
        }
        val candidate = msg.candidate ?: return
        Log.i(TAG, "ice_candidate_remote call_id=$callId")
        rtc.addRemoteIce(msg.sdpMid, msg.sdpMLineIndex, candidate)
    }

    private fun handleRejected(msg: WsMessage.Rejected) {
        val state = _callState.value
        if (state !is CallState.OutgoingRinging) return
        if (msg.callId != null && state.callId != null && msg.callId != state.callId) return
        flash("تماس رد شد")
        val callId = state.currentCallId()
        if (callId != null) telecom.disconnect(callId, TpDisconnectCause.REJECTED)
        cleanupCurrentCall()
    }

    private fun handleBusy(msg: WsMessage.Busy) {
        val state = _callState.value
        if (state !is CallState.OutgoingRinging) return
        val extension = msg.extension ?: state.extension
        flash("داخلی $extension مشغول است")
        val callId = state.currentCallId()
        if (callId != null) telecom.disconnect(callId, TpDisconnectCause.LOCAL)
        cleanupCurrentCall()
    }

    private fun handleUnavailable(msg: WsMessage.Unavailable) {
        val state = _callState.value
        if (state !is CallState.OutgoingRinging) return
        val extension = msg.extension ?: state.extension
        flash("داخلی $extension در دسترس نیست")
        val callId = state.currentCallId()
        if (callId != null) telecom.disconnect(callId, TpDisconnectCause.LOCAL)
        cleanupCurrentCall()
    }

    private fun handleCallEnded(msg: WsMessage.CallEnded) {
        val state = _callState.value
        if (!state.isActive) return
        val currentId = state.currentCallId()
        if (msg.callId != null && currentId != null && msg.callId != currentId) return
        Log.i(TAG, "call_ended reason=${msg.reason} call_id=$currentId")
        val message = ErrorMapper.callEndedMessage(msg.reason)
        if (message != null) flash(message)
        if (currentId != null) telecom.disconnect(currentId, TpDisconnectCause.REMOTE)
        cleanupCurrentCall()
    }

    private fun handleWsError(msg: WsMessage.WsError) {
        when (msg.code) {
            ErrorMapper.SESSION_REPLACED -> {
                // Backend delivers session replacement as an error message, then
                // closes the socket. Force logout immediately.
                forceLogout(ErrorMapper.mapError(ErrorMapper.SESSION_REPLACED))
            }
            "user_not_found", "cannot_call_self", "busy", "unavailable" -> {
                flash(ErrorMapper.mapError(msg.code) ?: "تماس برقرار نشد")
                val callId = currentCallId()
                if (callId != null) telecom.disconnect(callId, TpDisconnectCause.LOCAL)
                cleanupCurrentCall()
            }
            else -> {
                val mapped = ErrorMapper.mapError(msg.code)
                if (mapped != null) {
                    flash(mapped)
                } else {
                    Log.w(TAG, "unmapped_ws_error code=${msg.code} message=${msg.message}")
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Cleanup and helpers
    // ---------------------------------------------------------------------

    /**
     * Single idempotent local cleanup for the current call. Safe to call
     * multiple times (hangup racing remote end, late call_ended, etc.).
     * Sends no WebSocket command; use [endCurrentCall]/[rejectCurrentCall]
     * for user-initiated termination.
     */
    fun cleanupCurrentCall() {
        val state = _callState.value
        if (state is CallState.IncomingRinging) {
            notifier.hideIncoming(state.callId)
        }
        audio.stopRinging()
        audio.stopCallAudio()
        audio.setSpeakerOn(false)
        audio.setTelecomManaged(false)
        _speakerOn.value = false
        _muted.value = false
        rtc.close()
        negotiationTimeoutJob?.cancel()
        if (state.isActive || _callState.value.isActive) {
            _callState.value = CallState.Idle
        }
        Log.i(TAG, "call_ended_local_cleanup")
    }

    private fun currentCallId(): String? = _callState.value.currentCallId()

    private fun startNegotiationTimeout(callId: String) {
        negotiationTimeoutJob?.cancel()
        negotiationTimeoutJob = scope.launch {
            delay(AppConfig.NEGOTIATION_TIMEOUT_MS)
            val state = _callState.value
            if (state is CallState.Connecting && state.callId == callId) {
                Log.i(TAG, "negotiation_timeout call_id=$callId")
                flash("برقراری تماس ناموفق بود")
                terminateWithCause(TpDisconnectCause.LOCAL)
            }
        }
    }

    private fun flash(text: String) {
        val id = System.nanoTime()
        _flash.value = UiFlash(id, text)
        flashJob?.cancel()
        flashJob = scope.launch {
            delay(AppConfig.FLASH_MESSAGE_DURATION_MS)
            if (_flash.value?.id == id) _flash.value = null
        }
    }

    private companion object {
        const val TAG = "CallController"
        /** Refetch /webrtc/config before a call when the cached TURN credentials are older than this. */
        const val ICE_CONFIG_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }
}
