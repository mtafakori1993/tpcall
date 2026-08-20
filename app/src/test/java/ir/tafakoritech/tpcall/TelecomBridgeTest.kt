package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.call.SessionState
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import ir.tafakoritech.tpcall.telecom.TpDisconnectCause
import ir.tafakoritech.tpcall.telecom.TpEndpointType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Core-Telecom bridge contract: every TP Call call maps to exactly
 * one Telecom call, system callbacks converge on the single controller actions,
 * and cleanup is idempotent. The Android Telecom framework itself is not tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelecomBridgeTest {

    private lateinit var sessionStore: FakeSessionStorage
    private lateinit var authApi: FakeAuthGateway
    private lateinit var signaling: FakeSignalingGateway
    private lateinit var rtc: FakeRtcGateway
    private lateinit var audio: FakeCallAudio
    private lateinit var notifier: FakeCallNotifier
    private lateinit var telecom: FakeTelecomGateway
    private lateinit var controller: CallController

    private fun TestScope.buildController(micPermission: Boolean = true) {
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
            micPermissionProvider = { micPermission },
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
    }

    private fun incomingCallPayload(callId: String = "B", ext: String = "101", name: String = "محمد") =
        buildJsonObject {
            put("call_id", callId)
            putJsonObject("from") {
                put("extension", ext)
                put("name", name)
            }
        }

    private fun TestScope.startOutgoingRinging(callId: String = "A", ext: String = "203"): String {
        controller.startCall(ext)
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", callId)
            put("status", "ringing")
            putJsonObject("to") {
                put("extension", ext)
                put("name", "حسابداری")
            }
        })
        runCurrent()
        return callId
    }

    // -----------------------------------------------------------------
    // Outgoing
    // -----------------------------------------------------------------

    @Test
    fun `outgoing backend call creates exactly one telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        assertEquals(0, telecom.outgoingCalls.size)

        startOutgoingRinging("A")
        assertEquals(1, telecom.outgoingCalls.size)
        assertEquals("A", telecom.outgoingCalls[0].callId)
        assertEquals("203", telecom.outgoingCalls[0].extension)

        // A duplicate ringing response for the same call must not duplicate.
        signaling.emit("call", signaling.sent.last().requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
            putJsonObject("to") { put("extension", "203"); put("name", "حسابداری") }
        })
        runCurrent()
        assertEquals(1, telecom.outgoingCalls.size)
    }

    @Test
    fun `outgoing accepted then webrtc connected marks telecom active`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        rtc.listener?.onConnected()
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)
        assertEquals(listOf("A"), telecom.setActiveCalls)
        assertEquals(true, audio.telecomManagedStates.last())
    }

    @Test
    fun `outgoing busy disconnects the telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("busy", payload = buildJsonObject { put("extension", "203") })
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(telecom.disconnectsFor("A").isNotEmpty())
    }

    @Test
    fun `outgoing rejected disconnects the telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("rejected", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(telecom.disconnectsFor("A").contains(TpDisconnectCause.REJECTED))
    }

    @Test
    fun `outgoing offline disconnects the telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("unavailable", payload = buildJsonObject {
            put("extension", "203")
            put("reason", "offline")
        })
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(telecom.disconnectsFor("A").isNotEmpty())
    }

    @Test
    fun `local hangup disconnects the telecom call once`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        controller.endCurrentCall()
        controller.endCurrentCall()
        assertEquals(1, telecom.disconnectsFor("A").size)
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `webrtc failure disconnects the telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        rtc.listener?.onFailed("ice_connection_failed")
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(telecom.disconnectsFor("A").isNotEmpty())
        assertTrue(rtc.closeCount >= 1)
    }

    @Test
    fun `backend remote_hangup disconnects the telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        rtc.listener?.onConnected()
        runCurrent()

        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "A")
            put("reason", "remote_hangup")
        })
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals(listOf(TpDisconnectCause.REMOTE), telecom.disconnectsFor("A"))
    }

    // -----------------------------------------------------------------
    // Incoming
    // -----------------------------------------------------------------

    @Test
    fun `incoming backend call creates exactly one telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        assertEquals(1, telecom.incomingCalls.size)
        assertEquals("B", telecom.incomingCalls[0].callId)
        assertEquals("محمد", telecom.incomingCalls[0].displayName)
        assertEquals("101", telecom.incomingCalls[0].extension)

        // Duplicate incoming event for the same call_id must not duplicate.
        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()
        assertEquals(1, telecom.incomingCalls.size)
    }

    @Test
    fun `accept callback routes through controller once`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        controller.acceptCurrentCall()
        controller.acceptCurrentCall()
        assertEquals(1, signaling.sentOfType("accept").size)
        assertEquals(1, telecom.answered.size)
        assertTrue(controller.callState.value is CallState.Connecting)
    }

    @Test
    fun `system answer converges on the single accept path`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        telecom.fireSystemAnswer("B")
        runCurrent()

        assertEquals(1, signaling.sentOfType("accept").size)
        assertEquals(1, telecom.answered.size)
        assertTrue(controller.callState.value is CallState.Connecting)
        assertEquals(1, notifier.hiddenIncoming.size)
        assertTrue(audio.stopRingingCount >= 1)
    }

    @Test
    fun `reject callback routes through controller once`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        controller.rejectCurrentCall()
        controller.rejectCurrentCall()
        assertEquals(1, signaling.sentOfType("reject").size)
        assertEquals(listOf(TpDisconnectCause.REJECTED), telecom.disconnectsFor("B"))
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `system disconnect on ringing call rejects it`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        telecom.fireSystemDisconnect("B")
        runCurrent()

        assertEquals(1, signaling.sentOfType("reject").size)
        assertEquals(0, signaling.sentOfType("hangup").size)
        assertEquals(listOf(TpDisconnectCause.REJECTED), telecom.disconnectsFor("B"))
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `system disconnect on active call hangs up`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        rtc.listener?.onConnected()
        runCurrent()

        telecom.fireSystemDisconnect("A")
        runCurrent()

        assertEquals(1, signaling.sentOfType("hangup").size)
        assertEquals(CallState.Idle, controller.callState.value)
    }

    // -----------------------------------------------------------------
    // Stale / idempotency
    // -----------------------------------------------------------------

    @Test
    fun `stale system events for unknown call_id are ignored`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        telecom.fireSystemAnswer("ZZ")
        runCurrent()
        assertEquals(0, signaling.sentOfType("accept").size)
        assertTrue(controller.callState.value is CallState.IncomingRinging)

        telecom.fireSystemDisconnect("ZZ")
        runCurrent()
        assertEquals(0, signaling.sentOfType("reject").size)
        assertTrue(controller.callState.value is CallState.IncomingRinging)
    }

    @Test
    fun `cleanup is idempotent and ends telecom call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        controller.endCurrentCall()
        controller.cleanupCurrentCall()
        controller.cleanupCurrentCall()
        assertEquals(CallState.Idle, controller.callState.value)
        // endCurrentCall disconnects once; later cleanups must not add duplicates.
        assertEquals(1, telecom.disconnectsFor("A").size)
    }

    @Test
    fun `speaker toggles request telecom endpoint when managed`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        controller.toggleSpeaker()
        assertEquals(listOf("A" to TpEndpointType.SPEAKER), telecom.endpointRequests)
        assertEquals(0, audio.speakerStates.size)

        controller.toggleSpeaker()
        assertEquals(listOf("A" to TpEndpointType.EARPIECE), telecom.endpointRequests.takeLast(1))
    }

    @Test
    fun `endpoint change from telecom updates speaker state`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        rtc.listener?.onConnected()
        runCurrent()

        assertFalse(controller.speakerOn.value)
        telecom.fireEndpointChanged("A", TpEndpointType.SPEAKER)
        assertTrue(controller.speakerOn.value)
    }

    @Test
    fun `system set inactive stops call audio`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        val before = audio.stopCallAudioCount

        telecom.fireSystemSetInactive("A")
        assertTrue(audio.stopCallAudioCount > before)
    }

    @Test
    fun `logout releases the telecom session and ends the call`() = runTest {
        buildController()
        authenticateAndConnect()

        startOutgoingRinging("A")
        controller.logout()

        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(telecom.disconnectsFor("A").isNotEmpty())
        assertTrue(signaling.stopped)
    }
}
