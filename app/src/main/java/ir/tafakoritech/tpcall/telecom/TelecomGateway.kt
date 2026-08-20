package ir.tafakoritech.tpcall.telecom

/**
 * Android-free facade over Core-Telecom used by [ir.tafakoritech.tpcall.call.CallController].
 *
 * The production implementation is [TpTelecomManager]; unit tests use a fake.
 * All types referenced here are pure Kotlin so the controller can be tested on
 * the JVM without an Android device or the Telecom framework.
 */
interface TelecomGateway {

    /** Registers the app with Android Telecom. Idempotent and safe to call often. */
    fun register()

    /**
     * Registers an incoming (ringing) call with Android Telecom.
     * Creating the same call_id twice must be a no-op.
     */
    fun addIncomingCall(call: TpCallInfo)

    /**
     * Registers an outgoing (dialing) call with Android Telecom.
     * Creating the same call_id twice must be a no-op.
     */
    fun addOutgoingCall(call: TpCallInfo)

    /** Answers the call: the app (UI or system) accepted an incoming call. */
    fun answer(callId: String)

    /** Marks the call active (media connected / un-held). */
    fun setActive(callId: String)

    /** Marks the call inactive (held). */
    fun setInactive(callId: String)

    /** Disconnects the Telecom call with the given cause. Idempotent. */
    fun disconnect(callId: String, cause: TpDisconnectCause)

    /** Requests a specific audio endpoint (e.g. speaker / earpiece). */
    fun requestEndpoint(callId: String, endpoint: TpEndpointType)

    /** True when a Telecom call session is currently registered for callId. */
    fun hasCall(callId: String): Boolean

    /** Releases all Telecom call sessions (logout / app shutdown). */
    fun release()

    /** Routes system-originated Telecom events back into the app. */
    fun setListener(listener: TelecomEvents?)
}

/**
 * System-originated Telecom events. All are invoked on the caller's coroutine
 * scope by the production manager.
 */
interface TelecomEvents {
    /** The system (headset / watch / notification / Telecom UI) answered the call. */
    fun onSystemAnswer(callId: String)

    /** The system requested the call be disconnected (headset hangup, etc.). */
    fun onSystemDisconnect(callId: String, cause: TpDisconnectCause)

    /** The system set the call active (e.g. un-hold after a SIM call). */
    fun onSystemSetActive(callId: String)

    /** The system set the call inactive (e.g. hold because a SIM call was answered). */
    fun onSystemSetInactive(callId: String)

    /** The system changed the active audio endpoint. */
    fun onEndpointChanged(callId: String, endpoint: TpEndpointType)

    /** The system changed the global mute state. */
    fun onMuteChanged(callId: String, muted: Boolean)

    /** The Telecom call session for callId has fully ended and been removed. */
    fun onCallRemoved(callId: String)
}
