package ir.tafakoritech.tpcall.telecom

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Production [TelecomGateway] built on Jetpack Core-Telecom
 * (`androidx.core:core-telecom`).
 *
 * Responsibilities:
 *  - register the app with Android Telecom exactly once (idempotent)
 *  - register incoming/outgoing VoIP calls with Telecom, keyed by the
 *    authoritative TP Call `call_id`
 *  - expose idempotent control operations (answer / setActive / setInactive /
 *    disconnect / requestEndpoint)
 *  - forward system-originated Telecom events (answer, disconnect, hold, audio
 *    endpoints, mute) back to the app through [TelecomEvents]
 *
 * No audio is ever routed through Telecom; WebRTC remains the media engine.
 */
class TpTelecomManager(
    private val appContext: Context,
    private val scope: CoroutineScope,
) : TelecomGateway {

    private val callsManager = CallsManager(appContext)
    private val calls = ConcurrentHashMap<String, TpTelecomCall>()

    @Volatile
    private var registered = false

    private var telecomEvents: TelecomEvents? = null

    override fun setListener(listener: TelecomEvents?) {
        telecomEvents = listener
    }

    private val addressScheme = "tpcall"

    override fun register() {
        if (registered) return
        registered = true
        runCatching {
            callsManager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
            Log.i(TAG, "telecom_registered baseline")
        }.onFailure { e ->
            Log.w(TAG, "telecom_register_failed ${e.message}")
        }
    }

    override fun addIncomingCall(call: TpCallInfo) {
        launchCall(call, CallAttributesCompat.DIRECTION_INCOMING)
    }

    override fun addOutgoingCall(call: TpCallInfo) {
        launchCall(call, CallAttributesCompat.DIRECTION_OUTGOING)
    }

    private fun launchCall(call: TpCallInfo, direction: Int) {
        register()
        if (calls.containsKey(call.callId)) {
            Log.i(TAG, "telecom_add_skipped_duplicate call_id=${call.callId}")
            return
        }
        scope.launch {
            try {
                callsManager.addCall(
                    CallAttributesCompat(
                        displayName = call.displayName,
                        address = Uri.parse("$addressScheme:${call.extension}"),
                        direction = direction,
                        callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
                        callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
                    ),
                    onAnswer = { callType ->
                        val session = calls[call.callId]
                        session?.markSystemAnswered()
                        dispatch { telecomEvents?.onSystemAnswer(call.callId) }
                    },
                    onDisconnect = { cause ->
                        dispatch { telecomEvents?.onSystemDisconnect(call.callId, cause.toTpDisconnectCause()) }
                    },
                    onSetActive = {
                        dispatch { telecomEvents?.onSystemSetActive(call.callId) }
                    },
                    onSetInactive = {
                        dispatch { telecomEvents?.onSystemSetInactive(call.callId) }
                    },
                ) {
                    val session = TpTelecomCall(call.callId, this)
                    calls[call.callId] = session

                    launch { currentCallEndpoint.collect { endpoint ->
                        session.updateCurrentEndpoint(endpoint)
                        telecomEvents?.onEndpointChanged(call.callId, session.currentEndpoint)
                    } }
                    launch { availableEndpoints.collect { session.updateAvailableEndpoints(it) } }
                    launch { isMuted.collect { telecomEvents?.onMuteChanged(call.callId, it) } }
                }
            } catch (e: CallException) {
                Log.w(TAG, "telecom_add_call_failed code=${e.code} call_id=${call.callId}")
            } catch (e: Exception) {
                Log.w(TAG, "telecom_add_call_failed ${e.message} call_id=${call.callId}")
            } finally {
                calls.remove(call.callId)
                dispatch { telecomEvents?.onCallRemoved(call.callId) }
            }
        }
    }

    override fun answer(callId: String) {
        val session = calls[callId] ?: return
        scope.launch {
            val result = session.answer()
            if (result is CallControlResult.Error) {
                Log.w(TAG, "telecom_answer_failed code=${result.errorCode} call_id=$callId")
            }
        }
    }

    override fun setActive(callId: String) {
        val session = calls[callId] ?: return
        scope.launch {
            val result = session.setActive()
            if (result is CallControlResult.Error) {
                Log.w(TAG, "telecom_set_active_failed code=${result.errorCode} call_id=$callId")
            }
        }
    }

    override fun setInactive(callId: String) {
        val session = calls[callId] ?: return
        scope.launch {
            val result = session.setInactive()
            if (result is CallControlResult.Error) {
                Log.w(TAG, "telecom_set_inactive_failed code=${result.errorCode} call_id=$callId")
            }
        }
    }

    override fun disconnect(callId: String, cause: TpDisconnectCause) {
        val session = calls[callId] ?: return
        scope.launch {
            session.disconnect(cause.toDisconnectCause())
        }
    }

    override fun requestEndpoint(callId: String, endpoint: TpEndpointType) {
        val session = calls[callId] ?: return
        val compatType = when (endpoint) {
            TpEndpointType.EARPIECE -> CallEndpointCompat.TYPE_EARPIECE
            TpEndpointType.BLUETOOTH -> CallEndpointCompat.TYPE_BLUETOOTH
            TpEndpointType.WIRED_HEADSET -> CallEndpointCompat.TYPE_WIRED_HEADSET
            TpEndpointType.SPEAKER -> CallEndpointCompat.TYPE_SPEAKER
            TpEndpointType.STREAMING -> CallEndpointCompat.TYPE_STREAMING
            TpEndpointType.UNKNOWN -> return
        }
        scope.launch { session.requestEndpoint(compatType) }
    }

    override fun hasCall(callId: String): Boolean = calls.containsKey(callId)

    override fun release() {
        calls.keys.toList().forEach { callId ->
            scope.launch {
                calls[callId]?.disconnect(DisconnectCause(DisconnectCause.LOCAL))
            }
        }
        calls.clear()
    }

    private fun dispatch(block: () -> Unit) {
        try {
            scope.launch { block() }
        } catch (e: Exception) {
            Log.w(TAG, "telecom_dispatch_error ${e.message}")
        }
    }

    private companion object {
        const val TAG = "TpTelecomManager"
    }
}

internal fun DisconnectCause.toTpDisconnectCause(): TpDisconnectCause = when (code) {
    DisconnectCause.LOCAL -> TpDisconnectCause.LOCAL
    DisconnectCause.REMOTE -> TpDisconnectCause.REMOTE
    DisconnectCause.REJECTED -> TpDisconnectCause.REJECTED
    DisconnectCause.MISSED -> TpDisconnectCause.MISSED
    else -> TpDisconnectCause.LOCAL
}

internal fun TpDisconnectCause.toDisconnectCause(): DisconnectCause = when (this) {
    TpDisconnectCause.LOCAL -> DisconnectCause(DisconnectCause.LOCAL)
    TpDisconnectCause.REMOTE -> DisconnectCause(DisconnectCause.REMOTE)
    TpDisconnectCause.REJECTED -> DisconnectCause(DisconnectCause.REJECTED)
    TpDisconnectCause.MISSED -> DisconnectCause(DisconnectCause.MISSED)
}
