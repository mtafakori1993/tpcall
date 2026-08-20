package ir.tafakoritech.tpcall.telecom

/**
 * Pure Kotlin models shared between the CallController bridge and the
 * Core-Telecom integration layer. These deliberately avoid any Android
 * framework types so the bridge logic can be unit tested on the JVM.
 */

/** The reasons TP Call may tear down a Telecom call. */
enum class TpDisconnectCause {
    /** Local hangup / failure. */
    LOCAL,

    /** Remote side ended the call. */
    REMOTE,

    /** The callee rejected the call. */
    REJECTED,

    /** The call was never answered. */
    MISSED,
}

/** The audio endpoint types surfaced by Core-Telecom's CallEndpointCompat. */
enum class TpEndpointType {
    UNKNOWN,
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET,
    SPEAKER,
    STREAMING;

    companion object {
        fun fromCompatType(type: Int): TpEndpointType = when (type) {
            androidx.core.telecom.CallEndpointCompat.TYPE_EARPIECE -> EARPIECE
            androidx.core.telecom.CallEndpointCompat.TYPE_BLUETOOTH -> BLUETOOTH
            androidx.core.telecom.CallEndpointCompat.TYPE_WIRED_HEADSET -> WIRED_HEADSET
            androidx.core.telecom.CallEndpointCompat.TYPE_SPEAKER -> SPEAKER
            androidx.core.telecom.CallEndpointCompat.TYPE_STREAMING -> STREAMING
            else -> UNKNOWN
        }
    }
}

/** Identifies one registered Telecom call for the app's authoritative call_id. */
data class TpCallInfo(
    val callId: String,
    val displayName: String,
    val extension: String,
)
