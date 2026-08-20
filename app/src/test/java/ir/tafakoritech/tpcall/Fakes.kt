package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallAudio
import ir.tafakoritech.tpcall.call.CallNotifier
import ir.tafakoritech.tpcall.call.RtcGateway
import ir.tafakoritech.tpcall.call.RtcListener
import ir.tafakoritech.tpcall.call.SignalingGateway
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.model.AuthResponse
import ir.tafakoritech.tpcall.data.model.CallHistoryResponseDto
import ir.tafakoritech.tpcall.data.model.IceConfigDto
import ir.tafakoritech.tpcall.data.model.IceServerDto
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.data.session.Session
import ir.tafakoritech.tpcall.data.session.SessionStorage
import ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import ir.tafakoritech.tpcall.data.signaling.WsEnvelope
import ir.tafakoritech.tpcall.telecom.TelecomEvents
import ir.tafakoritech.tpcall.telecom.TelecomGateway
import ir.tafakoritech.tpcall.telecom.TpCallInfo
import ir.tafakoritech.tpcall.telecom.TpDisconnectCause
import ir.tafakoritech.tpcall.telecom.TpEndpointType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

class FakeSessionStorage : SessionStorage {
    var session: Session? = null
    override fun save(token: String, user: UserDto) {
        session = Session(token, user)
    }

    override fun load(): Session? = session

    override fun clear() {
        session = null
    }
}

class FakeAuthGateway : AuthGateway {
    var loginResult: Result<AuthResponse> = Result.success(
        AuthResponse(accessToken = "token-1", user = UserDto(name = "User", extension = "101")),
    )
    var registerResult: Result<AuthResponse> = Result.success(
        AuthResponse(accessToken = "token-1", user = UserDto(name = "User", extension = "101")),
    )
    var meResult: Result<UserDto> = Result.success(UserDto(name = "User", extension = "101"))
    var configResult: IceConfigDto = IceConfigDto()
    var userResultOverride: ((String) -> Result<UserDto>)? = null

    val historyRequests = mutableListOf<Pair<Int, Int>>()
    var historyResult: Result<CallHistoryResponseDto> = Result.success(CallHistoryResponseDto())

    var directoryResult: Result<List<UserDto>> = Result.success(emptyList())
    var directoryRequests = 0

    override suspend fun login(extension: String, password: String): AuthResponse =
        loginResult.getOrThrow()

    override suspend fun register(name: String, extension: String, password: String): AuthResponse =
        registerResult.getOrThrow()

    override suspend fun me(token: String): UserDto = meResult.getOrThrow()

    override suspend fun user(extension: String, token: String): UserDto =
        userResultOverride?.invoke(extension)?.getOrThrow()
            ?: UserDto(name = "Name", extension = extension)

    override suspend fun directory(token: String): List<UserDto> {
        directoryRequests++
        return directoryResult.getOrThrow()
    }

    override suspend fun webRtcConfig(token: String): IceConfigDto = configResult

    override suspend fun callHistory(token: String, limit: Int, offset: Int): CallHistoryResponseDto {
        historyRequests.add(limit to offset)
        return historyResult.getOrThrow()
    }
}

class FakeSignalingGateway : SignalingGateway {
    data class Sent(val type: String, val requestId: String?, val payload: JsonObject)

    val sent = mutableListOf<Sent>()
    private val _messages = MutableSharedFlow<WsEnvelope>(extraBufferCapacity = 64)
    override val messages: SharedFlow<WsEnvelope> = _messages
    private val _status = MutableStateFlow(SignalingStatus.DISCONNECTED)
    override val status: StateFlow<SignalingStatus> = _status
    private val _connectionState = MutableStateFlow<SignalingConnectionState>(
        SignalingConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<SignalingConnectionState> = _connectionState
    var started = false
    var stopped = false

    override fun start(token: String) {
        started = true
    }

    override fun stop() {
        stopped = true
    }

    override fun sendMessage(type: String, requestId: String?, payload: JsonObject) {
        sent.add(Sent(type, requestId, payload))
    }

    fun emit(type: String, requestId: String? = null, payload: JsonObject = JsonObject(emptyMap())) {
        _messages.tryEmit(WsEnvelope(type = type, requestId = requestId, payload = payload))
    }

    fun setConnected(connected: Boolean = true) {
        _status.value = if (connected) SignalingStatus.CONNECTED else SignalingStatus.DISCONNECTED
        _connectionState.value =
            if (connected) SignalingConnectionState.Connected else SignalingConnectionState.Disconnected
    }

    fun setAuthFailed() {
        _status.value = SignalingStatus.AUTH_FAILED
        _connectionState.value = SignalingConnectionState.Disconnected
    }

    fun setReconnecting(attempt: Int = 1) {
        _status.value = SignalingStatus.RECONNECTING
        _connectionState.value = SignalingConnectionState.Reconnecting(attempt)
    }

    fun sentOfType(type: String): List<Sent> = sent.filter { it.type == type }
}

class FakeRtcGateway : RtcGateway {
    override var listener: RtcListener? = null
    val beginCalls = mutableListOf<List<IceServerDto>>()
    var createOfferCount = 0
    var applyRemoteOfferSdp: String? = null
    var setRemoteAnswerSdp: String? = null
    val remoteIce = mutableListOf<Triple<String?, Int, String>>()
    val muteStates = mutableListOf<Boolean>()
    var closeCount = 0

    override fun beginCall(iceServers: List<IceServerDto>) {
        beginCalls.add(iceServers)
    }

    override fun createOffer() {
        createOfferCount++
    }

    override fun applyRemoteOffer(sdp: String) {
        applyRemoteOfferSdp = sdp
    }

    override fun setRemoteAnswer(sdp: String) {
        setRemoteAnswerSdp = sdp
    }

    override fun addRemoteIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        remoteIce.add(Triple(sdpMid, sdpMLineIndex, candidate))
    }

    override fun setMuted(muted: Boolean) {
        muteStates.add(muted)
    }

    override fun close() {
        closeCount++
    }
}

class FakeCallAudio : CallAudio {
    var startRingingCount = 0
    var stopRingingCount = 0
    var startCallAudioCount = 0
    var stopCallAudioCount = 0
    var speakerStates = mutableListOf<Boolean>()
    var telecomManagedStates = mutableListOf<Boolean>()
    var released = false

    override fun startRinging() {
        startRingingCount++
    }

    override fun stopRinging() {
        stopRingingCount++
    }

    override fun startCallAudio() {
        startCallAudioCount++
    }

    override fun stopCallAudio() {
        stopCallAudioCount++
    }

    override fun setSpeakerOn(on: Boolean) {
        speakerStates.add(on)
    }

    override fun setTelecomManaged(managed: Boolean) {
        telecomManagedStates.add(managed)
    }

    override fun release() {
        released = true
    }
}

class FakeCallNotifier : CallNotifier {
    data class Incoming(val callId: String, val name: String, val extension: String)

    val shownIncoming = mutableListOf<Incoming>()
    val hiddenIncoming = mutableListOf<String>()

    override fun showIncoming(callId: String, name: String, extension: String) {
        shownIncoming.add(Incoming(callId, name, extension))
    }

    override fun hideIncoming(callId: String) {
        hiddenIncoming.add(callId)
    }
}

class FakeTelecomGateway : TelecomGateway {
    var registerCount = 0
    val incomingCalls = mutableListOf<TpCallInfo>()
    val outgoingCalls = mutableListOf<TpCallInfo>()
    val answered = mutableListOf<String>()
    val setActiveCalls = mutableListOf<String>()
    val setInactiveCalls = mutableListOf<String>()
    val disconnects = mutableListOf<Pair<String, TpDisconnectCause>>()
    val endpointRequests = mutableListOf<Pair<String, TpEndpointType>>()
    var released = false

    private val calls = mutableSetOf<String>()
    private var listener: TelecomEvents? = null

    override fun register() {
        registerCount++
    }

    override fun addIncomingCall(call: TpCallInfo) {
        if (calls.add(call.callId)) {
            incomingCalls.add(call)
        }
    }

    override fun addOutgoingCall(call: TpCallInfo) {
        if (calls.add(call.callId)) {
            outgoingCalls.add(call)
        }
    }

    override fun answer(callId: String) {
        answered.add(callId)
    }

    override fun setActive(callId: String) {
        setActiveCalls.add(callId)
    }

    override fun setInactive(callId: String) {
        setInactiveCalls.add(callId)
    }

    override fun disconnect(callId: String, cause: TpDisconnectCause) {
        disconnects.add(callId to cause)
        calls.remove(callId)
    }

    override fun requestEndpoint(callId: String, endpoint: TpEndpointType) {
        endpointRequests.add(callId to endpoint)
    }

    override fun hasCall(callId: String): Boolean = calls.contains(callId)

    override fun release() {
        released = true
        calls.clear()
    }

    override fun setListener(listener: TelecomEvents?) {
        this.listener = listener
    }

    fun fireSystemAnswer(callId: String) {
        listener?.onSystemAnswer(callId)
    }

    fun fireSystemDisconnect(callId: String, cause: TpDisconnectCause = TpDisconnectCause.LOCAL) {
        listener?.onSystemDisconnect(callId, cause)
    }

    fun fireSystemSetActive(callId: String) {
        listener?.onSystemSetActive(callId)
    }

    fun fireSystemSetInactive(callId: String) {
        listener?.onSystemSetInactive(callId)
    }

    fun fireEndpointChanged(callId: String, endpoint: TpEndpointType) {
        listener?.onEndpointChanged(callId, endpoint)
    }

    fun fireMuteChanged(callId: String, muted: Boolean) {
        listener?.onMuteChanged(callId, muted)
    }

    fun fireCallRemoved(callId: String) {
        listener?.onCallRemoved(callId)
    }

    fun disconnectsFor(callId: String): List<TpDisconnectCause> =
        disconnects.filter { it.first == callId }.map { it.second }
}
