package ir.tafakoritech.tpcall.call

import ir.tafakoritech.tpcall.data.model.IceServerDto

/**
 * Audio + ringing + vibration gateway used by [CallController].
 * Android-free interface; the production implementation is [AudioRouteManager].
 */
interface CallAudio {
    fun startRinging()
    fun stopRinging()
    fun startCallAudio()
    fun stopCallAudio()
    fun setSpeakerOn(on: Boolean)
    fun release()

    /**
     * Informs the audio layer whether a Core-Telecom call is managing the
     * system audio route for the current call. When true, the audio layer must
     * not fight Telecom with AudioManager.mode / audio focus / speakerphone.
     */
    fun setTelecomManaged(managed: Boolean)
}

/**
 * Incoming-call / foreground notification gateway used by [CallController].
 * Android-free interface; the production implementation is CallNotificationManager.
 */
interface CallNotifier {
    fun showIncoming(callId: String, name: String, extension: String)
    fun hideIncoming(callId: String)
}

/**
 * WebRTC gateway used by [CallController]. The production implementation is
 * [WebRtcManager]. One negotiation (beginCall..close) happens per call.
 */
interface RtcGateway {
    var listener: RtcListener?

    fun beginCall(iceServers: List<IceServerDto>)
    fun createOffer()
    fun applyRemoteOffer(sdp: String)
    fun setRemoteAnswer(sdp: String)
    fun addRemoteIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun setMuted(muted: Boolean)
    fun close()
}

interface RtcListener {
    fun onOfferReady(sdp: String)
    fun onAnswerReady(sdp: String)
    fun onLocalIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun onConnected()
    fun onFailed(error: String)
}

/**
 * Signaling gateway used by [CallController]. Production implementation is
 * [ir.tafakoritech.tpcall.data.signaling.SignalingClient].
 *
 * The concrete client owns reconnection; [status] and [connectionState] are the
 * authoritative connectivity signals consumed by the UI and the controller.
 */
interface SignalingGateway {
    val messages: kotlinx.coroutines.flow.SharedFlow<ir.tafakoritech.tpcall.data.signaling.WsEnvelope>
    val status: kotlinx.coroutines.flow.StateFlow<ir.tafakoritech.tpcall.data.signaling.SignalingStatus>
    val connectionState: kotlinx.coroutines.flow.StateFlow<ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState>
    fun start(token: String)
    fun stop()
    fun sendMessage(type: String, requestId: String?, payload: kotlinx.serialization.json.JsonObject)
}
