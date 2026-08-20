package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies reconnect behavior at the controller layer: connection state is the
 * single source of truth, recovery grace bounds unknown call state, and new
 * calls are disabled while reconnecting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalingReconnectTest {

    private lateinit var sessionStore: FakeSessionStorage
    private lateinit var authApi: FakeAuthGateway
    private lateinit var signaling: FakeSignalingGateway
    private lateinit var rtc: FakeRtcGateway
    private lateinit var audio: FakeCallAudio
    private lateinit var notifier: FakeCallNotifier
    private lateinit var telecom: FakeTelecomGateway
    private lateinit var controller: CallController

    private fun TestScope.buildController() {
        sessionStore = FakeSessionStorage()
        authApi = FakeAuthGateway()
        signaling = FakeSignalingGateway()
        rtc = FakeRtcGateway()
        audio = FakeCallAudio()
        notifier = FakeCallNotifier()
        telecom = FakeTelecomGateway()
        controller = CallController(
            scope = backgroundScope,
            sessionStore = sessionStore,
            authApi = authApi,
            signalingFactory = { signaling },
            rtc = rtc,
            audio = audio,
            notifier = notifier,
            telecom = telecom,
            micPermissionProvider = { true },
        )
    }

    private fun TestScope.authenticateAndConnect() {
        sessionStore.save("token-1", UserDto(name = "User 101", extension = "101"))
        controller.initialize()
        runCurrent()
        controller.startSignaling()
        runCurrent()
        signaling.setConnected()
        runCurrent()
        assertEquals(SignalingStatus.CONNECTED, controller.signalingStatus.value)
        assertEquals(SignalingConnectionState.Connected, controller.connectionState.value)
    }

    private fun TestScope.reachInCall(callId: String = "A", ext: String = "203") {
        controller.startCall(ext)
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", callId)
            put("status", "ringing")
            putJsonObject("to") { put("extension", ext); put("name", "حسابداری") }
        })
        runCurrent()
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", callId) })
        runCurrent()
        rtc.listener?.onConnected()
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)
    }

    @Test
    fun `reconnecting state is exposed as the authoritative connection state`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.setReconnecting(attempt = 2)
        runCurrent()
        assertEquals(SignalingStatus.RECONNECTING, controller.signalingStatus.value)
        assertEquals(SignalingConnectionState.Reconnecting(2), controller.connectionState.value)

        signaling.setConnected()
        runCurrent()
        assertEquals(SignalingConnectionState.Connected, controller.connectionState.value)
    }

    @Test
    fun `disconnected state disables new outgoing calls`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.setReconnecting(attempt = 1)
        runCurrent()

        controller.startCall("203")
        assertEquals(0, signaling.sentOfType("call").size)
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `reconnect during active call keeps the call if signaling returns`() = runTest {
        buildController()
        authenticateAndConnect()
        reachInCall()

        // Signaling briefly drops during the call, then returns before grace.
        signaling.setReconnecting(attempt = 1)
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)

        advanceTimeBy(5_000)
        runCurrent()

        signaling.setConnected()
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)
        assertEquals(SignalingConnectionState.Connected, controller.connectionState.value)
    }

    @Test
    fun `grace period ends the call if signaling stays down`() = runTest {
        buildController()
        authenticateAndConnect()
        reachInCall()

        signaling.setReconnecting(attempt = 1)
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)

        // Within grace: still active.
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)

        // After grace expires without recovery: call ends locally.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(rtc.closeCount >= 1)
        assertTrue(telecom.disconnectsFor("A").isNotEmpty())
    }

    @Test
    fun `grace expiry only ends the current call`() = runTest {
        buildController()
        authenticateAndConnect()
        reachInCall(callId = "A", ext = "203")

        signaling.setReconnecting(attempt = 1)
        runCurrent()

        // Call ends before grace fully elapses (e.g. remote hangup arrives).
        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "A")
            put("reason", "remote_hangup")
        })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)

        // The stale recovery timer must not restart the call or throw.
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `logout stops signaling and clears connection state`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.logout()
        assertEquals(SignalingConnectionState.Disconnected, controller.connectionState.value)
        assertEquals(SignalingStatus.DISCONNECTED, controller.signalingStatus.value)
        assertTrue(signaling.stopped)
    }

    @Test
    fun `auth failure forces logout and disconnects`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.setAuthFailed()
        runCurrent()

        assertTrue(controller.sessionState.value is ir.tafakoritech.tpcall.call.SessionState.LoggedOut)
        assertTrue(signaling.stopped)
        assertEquals(SignalingConnectionState.Disconnected, controller.connectionState.value)
    }
}
