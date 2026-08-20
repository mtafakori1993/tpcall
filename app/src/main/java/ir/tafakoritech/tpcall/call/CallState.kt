package ir.tafakoritech.tpcall.call

/**
 * The single authoritative high-level call state of the application.
 */
sealed interface CallState {

    data object Idle : CallState

    data class OutgoingRinging(
        val callId: String?,
        val extension: String,
        val name: String?,
    ) : CallState

    data class IncomingRinging(
        val callId: String,
        val extension: String,
        val name: String,
    ) : CallState

    data class Connecting(
        val callId: String,
        val peerExtension: String,
        val peerName: String?,
    ) : CallState

    data class InCall(
        val callId: String,
        val peerExtension: String,
        val peerName: String?,
        val startedAt: Long,
    ) : CallState
}

val CallState.isActive: Boolean
    get() = this != CallState.Idle

fun CallState.currentCallId(): String? = when (this) {
    is CallState.OutgoingRinging -> callId
    is CallState.IncomingRinging -> callId
    is CallState.Connecting -> callId
    is CallState.InCall -> callId
    else -> null
}
