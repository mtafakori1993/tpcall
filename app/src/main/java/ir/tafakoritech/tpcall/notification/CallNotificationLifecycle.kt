package ir.tafakoritech.tpcall.notification

/**
 * Pure, Android-free lifecycle state machine for the call notification.
 *
 * It answers: "what must happen to the foreground-service notification now?"
 * The decision is derived only from the current call phase plus whether a
 * foreground call notification has already been established.
 *
 * Guarantee: an ACTIVE (ongoing) notification update is NEVER produced before
 * the foreground service notification lifecycle has been established — if the
 * first observed phase is ACTIVE, the machine returns [EnterForeground] so the
 * caller calls startForeground() (never a bare notify()).
 */
class CallNotificationLifecycle {

    private var foregroundEstablished = false

    /** True once a call notification has been registered via startForeground. */
    fun isForegroundEstablished(): Boolean = foregroundEstablished

    /**
     * Advances the machine for [phase] and returns the required action.
     *
     * - [CallNotificationPhase.RINGING]   -> [EnterForeground] (establish).
     * - [CallNotificationPhase.ACTIVE]    -> [EnterForeground] if not yet
     *   established, otherwise [UpdateForeground].
     * - [CallNotificationPhase.IDLE]      -> [StopForeground] if established,
     *   otherwise [None].
     */
    fun transition(phase: CallNotificationPhase): CallNotificationDecision {
        return when (phase) {
            CallNotificationPhase.RINGING -> {
                foregroundEstablished = true
                CallNotificationDecision.EnterForeground
            }
            CallNotificationPhase.ACTIVE -> {
                if (!foregroundEstablished) {
                    foregroundEstablished = true
                    CallNotificationDecision.EnterForeground
                } else {
                    CallNotificationDecision.UpdateForeground
                }
            }
            CallNotificationPhase.IDLE -> {
                val wasEstablished = foregroundEstablished
                foregroundEstablished = false
                if (wasEstablished) {
                    CallNotificationDecision.StopForeground
                } else {
                    CallNotificationDecision.None
                }
            }
        }
    }
}

/** Coarse call phases mapped from CallController.CallState. */
enum class CallNotificationPhase {
    IDLE,
    RINGING,
    ACTIVE,
}

/**
 * What the foreground service must do with its call notification.
 */
sealed interface CallNotificationDecision {
    /** Establish the call notification via startForeground(). */
    data object EnterForeground : CallNotificationDecision

    /** Update the already-established foreground call notification. */
    data object UpdateForeground : CallNotificationDecision

    /** Remove the call foreground notification and restore signaling. */
    data object StopForeground : CallNotificationDecision

    /** Nothing to do. */
    data object None : CallNotificationDecision
}
