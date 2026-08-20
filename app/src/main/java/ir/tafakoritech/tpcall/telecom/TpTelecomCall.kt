package ir.tafakoritech.tpcall.telecom

import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import android.telecom.DisconnectCause
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps one Core-Telecom [CallControlScope] for a single TP Call `call_id`.
 *
 * All control operations are idempotent: answer/disconnect may be invoked more
 * than once (UI racing a system hangup, duplicate accept, cleanup) without
 * corrupting the underlying Telecom session.
 */
class TpTelecomCall(
    val callId: String,
    private val scope: CallControlScope,
) {
    private val answered = AtomicBoolean(false)
    private val disconnected = AtomicBoolean(false)

    @Volatile
    var currentEndpoint: TpEndpointType = TpEndpointType.UNKNOWN
        private set

    @Volatile
    private var availableEndpoints: List<CallEndpointCompat> = emptyList()

    /**
     * Called by the manager when the platform answers via a system surface
     * (headset, watch, notification). Marks the call answered so the app's own
     * answer action becomes a no-op and never double-answers.
     */
    fun markSystemAnswered() {
        answered.set(true)
    }

    suspend fun answer(): CallControlResult {
        if (!answered.compareAndSet(false, true)) {
            return CallControlResult.Success()
        }
        return runCatching {
            scope.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
        }.getOrDefault(CallControlResult.Error(androidx.core.telecom.CallException.ERROR_UNKNOWN))
    }

    suspend fun setActive(): CallControlResult {
        if (disconnected.get()) return CallControlResult.Success()
        return runCatching { scope.setActive() }
            .getOrDefault(CallControlResult.Error(androidx.core.telecom.CallException.ERROR_UNKNOWN))
    }

    suspend fun setInactive(): CallControlResult {
        if (disconnected.get()) return CallControlResult.Success()
        return runCatching { scope.setInactive() }
            .getOrDefault(CallControlResult.Error(androidx.core.telecom.CallException.ERROR_UNKNOWN))
    }

    suspend fun disconnect(cause: DisconnectCause) {
        if (!disconnected.compareAndSet(false, true)) return
        runCatching { scope.disconnect(cause) }
    }

    suspend fun requestEndpoint(endpointType: Int) {
        if (disconnected.get()) return
        val target = availableEndpoints.firstOrNull { it.type == endpointType } ?: return
        runCatching { scope.requestEndpointChange(target) }
    }

    fun updateCurrentEndpoint(endpoint: CallEndpointCompat) {
        currentEndpoint = TpEndpointType.fromCompatType(endpoint.type)
    }

    fun updateAvailableEndpoints(endpoints: List<CallEndpointCompat>) {
        availableEndpoints = endpoints
    }
}
