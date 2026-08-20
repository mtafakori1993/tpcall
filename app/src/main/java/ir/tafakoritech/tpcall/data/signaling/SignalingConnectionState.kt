package ir.tafakoritech.tpcall.data.signaling

/**
 * Authoritative signaling connection state, exposed as a StateFlow so no
 * individual screen guesses connectivity.
 */
sealed interface SignalingConnectionState {
    data object Disconnected : SignalingConnectionState
    data object Connecting : SignalingConnectionState
    data object Connected : SignalingConnectionState
    data class Reconnecting(val attempt: Int) : SignalingConnectionState
}
