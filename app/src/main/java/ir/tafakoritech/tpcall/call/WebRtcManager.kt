package ir.tafakoritech.tpcall.call

import android.content.Context
import android.util.Log
import ir.tafakoritech.tpcall.data.model.IceServerDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Owns all WebRTC resources for the app:
 *  - a single reusable [PeerConnectionFactory]
 *  - one PeerConnection per call (created by [beginCall], released by [close])
 *  - the local microphone audio source/track
 *  - a buffer for remote ICE candidates that arrive before the remote SDP
 *
 * The implementation is audio-only; no video capturer/renderer is ever created.
 *
 * All native→Java observer callbacks are wrapped so that no exception can ever
 * escape into the JNI layer (WebRTC aborts the process when a Java callback
 * throws). Listener callbacks are delivered on [callbackScope] (the
 * controller's main-thread scope) so state updates stay serialized with the
 * controller.
 */
class WebRtcManager(
    private val appContext: Context,
    private val callbackScope: CoroutineScope,
    private val callbackDispatcher: CoroutineDispatcher,
) : RtcGateway {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private val pendingRemoteIce = IceCandidateQueue()
    private var remoteDescriptionSet = false
    private var closed = true

    override var listener: RtcListener? = null

    private fun ensureFactory() {
        if (factory != null) return
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        Log.i(TAG, "webrtc_factory_initialized")
    }

    override fun beginCall(iceServers: List<IceServerDto>) {
        safe {
            ensureFactory()
            close()
            Log.i(
                TAG,
                "webrtc_begin_call ice_servers=${iceServers.size} " +
                    "turn_servers=${iceServers.count { s -> s.urls.any { u -> u.startsWith("turn") } }}",
            )

            val rtcConfig = PeerConnection.RTCConfiguration(
                iceServers.map {
                    val builder = PeerConnection.IceServer.builder(it.urls)
                    // TURN servers require the short-lived credentials from /webrtc/config.
                    if (!it.username.isNullOrEmpty() && !it.credential.isNullOrEmpty()) {
                        builder.setUsername(it.username)
                        builder.setPassword(it.credential)
                    }
                    builder.createIceServer()
                },
            )
            rtcConfig.iceCandidatePoolSize = 0
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

            peerConnection = factory?.createPeerConnection(rtcConfig, peerObserver)
            val pc = peerConnection
            if (pc == null) {
                dispatch { listener?.onFailed("create_peer_connection_failed") }
                return@safe
            }

            val constraints = MediaConstraints()
            audioSource = factory?.createAudioSource(constraints)
            audioTrack = factory?.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)
            audioTrack?.let { pc.addTrack(it) }

            remoteDescriptionSet = false
            pendingRemoteIce.clear()
            closed = false
        }
    }

    override fun createOffer() {
        safe {
            val pc = peerConnection ?: return@safe
            if (closed) return@safe
            Log.i(TAG, "webrtc_create_offer")
            pc.createOffer(offerObserver, MediaConstraints())
        }
    }

    override fun applyRemoteOffer(sdp: String) {
        safe {
            val pc = peerConnection ?: return@safe
            if (closed) return@safe
            Log.i(TAG, "webrtc_offer_received sdp_len=${sdp.length}")
            val description = SessionDescription(SessionDescription.Type.OFFER, sdp)
            pc.setRemoteDescription(remoteDescriptionObserver(answerCallback = { answerSdp ->
                dispatch { listener?.onAnswerReady(answerSdp) }
            }), description)
        }
    }

    override fun setRemoteAnswer(sdp: String) {
        safe {
            val pc = peerConnection ?: return@safe
            if (closed) return@safe
            Log.i(TAG, "webrtc_answer_received sdp_len=${sdp.length}")
            val description = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            pc.setRemoteDescription(remoteDescriptionObserver(answerCallback = null), description)
        }
    }

    override fun addRemoteIce(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        safe {
            val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
            if (remoteDescriptionSet && peerConnection != null && !closed) {
                peerConnection?.addIceCandidate(ice)
            } else {
                pendingRemoteIce.add(
                    IceCandidateQueue.RemoteIce(sdpMid, sdpMLineIndex, candidate),
                )
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        safe {
            Log.i(TAG, "webrtc_mute_changed muted=$muted")
            audioTrack?.setEnabled(!muted)
        }
    }

    override fun close() {
        if (closed && peerConnection == null) return
        safe {
            Log.i(TAG, "webrtc_close")
            closed = true
            remoteDescriptionSet = false
            pendingRemoteIce.clear()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            audioTrack?.dispose()
            audioTrack = null
            audioSource?.dispose()
            audioSource = null
        }
    }

    /** Releases the factory; only used on full application shutdown. */
    fun disposeFactory() {
        close()
        factory = null
    }

    private fun flushPendingIce() {
        if (!remoteDescriptionSet) return
        val pc = peerConnection ?: return
        for (ice in pendingRemoteIce.drain()) {
            pc.addIceCandidate(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate))
        }
    }

    /**
     * Runs native→Java callback bodies so that no exception escapes into the
     * WebRTC JNI layer (which aborts the process via jvm.cc HandleException).
     */
    private fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "webrtc_callback_error ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun dispatch(block: () -> Unit) {
        try {
            callbackScope.launch(callbackDispatcher) {
                safe(block)
            }
        } catch (e: Exception) {
            Log.e(TAG, "webrtc_dispatch_error ${e.message}")
        }
    }

    private val offerObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = safe {
            val pc = peerConnection ?: return@safe
            if (description == null) {
                dispatch { listener?.onFailed("create_offer_failed") }
                return@safe
            }
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "webrtc_local_description_failed $error")
                    dispatch { listener?.onFailed("set_local_description_failed") }
                }

                override fun onSetSuccess() {
                    val local = pc.localDescription?.description
                    if (local != null) {
                        Log.i(TAG, "webrtc_offer_created")
                        dispatch { listener?.onOfferReady(local) }
                    }
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "webrtc_set_local_description_failed $error")
                    dispatch { listener?.onFailed("set_local_description_failed") }
                }
            }, description)
        }

        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "webrtc_create_offer_failed $error")
            dispatch { listener?.onFailed("create_offer_failed") }
        }

        override fun onSetSuccess() = Unit

        override fun onSetFailure(error: String?) {
            Log.e(TAG, "webrtc_create_offer_failed $error")
            dispatch { listener?.onFailed("create_offer_failed") }
        }
    }

    private fun remoteDescriptionObserver(answerCallback: ((String) -> Unit)?): SdpObserver =
        object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) = Unit

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "webrtc_create_answer_failed $error")
                dispatch { listener?.onFailed("create_answer_failed") }
            }

            override fun onSetSuccess() = safe {
                remoteDescriptionSet = true
                flushPendingIce()
                val pc = peerConnection ?: return@safe
                if (answerCallback == null) return@safe
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        val pc2 = peerConnection ?: return
                        pc2.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(description: SessionDescription?) = Unit

                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "webrtc_set_answer_failed $error")
                                dispatch { listener?.onFailed("set_answer_failed") }
                            }

                            override fun onSetSuccess() {
                                val answer = pc2.localDescription?.description ?: return
                                Log.i(TAG, "webrtc_answer_created")
                                answerCallback(answer)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "webrtc_set_answer_failed $error")
                                dispatch { listener?.onFailed("set_answer_failed") }
                            }
                        }, description)
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "webrtc_create_answer_failed $error")
                        dispatch { listener?.onFailed("create_answer_failed") }
                    }

                    override fun onSetSuccess() = Unit

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "webrtc_create_answer_failed $error")
                        dispatch { listener?.onFailed("create_answer_failed") }
                    }
                }, MediaConstraints())
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "webrtc_set_remote_description_failed $error")
                dispatch { listener?.onFailed("set_remote_description_failed") }
            }
        }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(ice: IceCandidate?) = safe {
            Log.i(TAG, "ice_candidate_local")
            if (ice == null) return@safe
            dispatch {
                listener?.onLocalIce(ice.sdpMid, ice.sdpMLineIndex, ice.sdp)
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = safe {
            Log.i(TAG, "webrtc_ice_connection_state $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.i(TAG, "webrtc_connected")
                    dispatch { listener?.onConnected() }
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    dispatch { listener?.onFailed("ice_connection_failed") }
                }
                else -> Unit
            }
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) = safe {
            Log.i(TAG, "webrtc_peer_connection_state $state")
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    Log.i(TAG, "webrtc_connected")
                    dispatch { listener?.onConnected() }
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    dispatch { listener?.onFailed("peer_connection_failed") }
                }
                else -> Unit
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) = safe {
            val track = transceiver.receiver.track()
            if (track is AudioTrack) {
                Log.i(TAG, "webrtc_remote_audio_track_added")
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onRemoveTrack(receiver: RtpReceiver) = Unit
        override fun onAddStream(mediaStream: MediaStream) = Unit
        override fun onRemoveStream(mediaStream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    private companion object {
        const val TAG = "WebRtcManager"
        const val LOCAL_AUDIO_TRACK_ID = "tpcall_mic"
    }
}
