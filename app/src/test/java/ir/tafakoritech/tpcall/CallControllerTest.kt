package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.call.SessionState
import ir.tafakoritech.tpcall.data.api.ApiErrorKind
import ir.tafakoritech.tpcall.data.api.ApiException
import ir.tafakoritech.tpcall.data.model.UserDto
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallControllerTest {

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
        assertTrue(controller.sessionState.value is SessionState.Authenticated)
        controller.startSignaling()
        runCurrent()
        signaling.setConnected()
        runCurrent()
        assertEquals(SignalingStatus.CONNECTED, controller.signalingStatus.value)
    }

    private fun incomingCallPayload(callId: String = "B", ext: String = "101", name: String = "محمد") = buildJsonObject {
        put("call_id", callId)
        putJsonObject("from") {
            put("extension", ext)
            put("name", name)
        }
    }

    @Test
    fun `outgoing call reaches InCall`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        assertEquals(CallState.OutgoingRinging(null, "203", null), controller.callState.value)
        assertEquals("call", signaling.sent.last().type)
        val requestId = signaling.sent.last().requestId
        assertTrue(requestId != null)

        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
            putJsonObject("to") {
                put("extension", "203")
                put("name", "حسابداری")
            }
        })
        runCurrent()
        val ringing = controller.callState.value as CallState.OutgoingRinging
        assertEquals("A", ringing.callId)
        assertEquals("حسابداری", ringing.name)

        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        assertTrue(controller.callState.value is CallState.Connecting)
        assertEquals(1, rtc.beginCalls.size)
        assertEquals(1, rtc.createOfferCount)

        rtc.listener?.onOfferReady("sdp-offer")
        val offerSent = signaling.sentOfType("offer").last()
        assertEquals("A", offerSent.payload["call_id"]!!.jsonPrimitive.content)
        assertEquals("sdp-offer", offerSent.payload["sdp"]!!.jsonPrimitive.content)

        signaling.emit("answer", payload = buildJsonObject {
            put("call_id", "A")
            put("sdp", "sdp-answer")
        })
        runCurrent()
        assertEquals("sdp-answer", rtc.setRemoteAnswerSdp)

        signaling.emit("ice_candidate", payload = buildJsonObject {
            put("call_id", "A")
            putJsonObject("candidate") {
                put("sdpMid", "0")
                put("sdpMLineIndex", 0)
                put("candidate", "candidate:1")
            }
        })
        runCurrent()
        assertEquals(1, rtc.remoteIce.size)

        rtc.listener?.onConnected()
        val inCall = controller.callState.value as CallState.InCall
        assertEquals("A", inCall.callId)
        assertEquals("203", inCall.peerExtension)

        controller.endCurrentCall()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("A", signaling.sentOfType("hangup").last().payload["call_id"]!!.jsonPrimitive.content)
        assertTrue(rtc.closeCount >= 1)
    }

    @Test
    fun `incoming call accept reaches InCall`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()
        assertEquals(CallState.IncomingRinging("B", "101", "محمد"), controller.callState.value)
        assertEquals(1, audio.startRingingCount)
        assertEquals(1, notifier.shownIncoming.size)

        // Duplicate incoming_call must not re-ring.
        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()
        assertEquals(1, audio.startRingingCount)
        assertEquals(1, notifier.shownIncoming.size)

        controller.acceptCurrentCall()
        assertEquals(CallState.Connecting("B", "101", "محمد"), controller.callState.value)
        assertEquals("B", signaling.sentOfType("accept").last().payload["call_id"]!!.jsonPrimitive.content)
        assertTrue(audio.stopRingingCount >= 1)
        assertEquals(1, notifier.hiddenIncoming.size)

        signaling.emit("offer", payload = buildJsonObject {
            put("call_id", "B")
            put("sdp", "offer-sdp")
        })
        runCurrent()
        assertEquals("offer-sdp", rtc.applyRemoteOfferSdp)
        assertEquals(1, rtc.beginCalls.size)

        rtc.listener?.onAnswerReady("answer-sdp")
        val answerSent = signaling.sentOfType("answer").last()
        assertEquals("B", answerSent.payload["call_id"]!!.jsonPrimitive.content)
        assertEquals("answer-sdp", answerSent.payload["sdp"]!!.jsonPrimitive.content)

        rtc.listener?.onConnected()
        assertTrue(controller.callState.value is CallState.InCall)

        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "B")
            put("reason", "remote_hangup")
        })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertTrue(rtc.closeCount >= 1)
        assertTrue(audio.stopCallAudioCount >= 1)
    }

    @Test
    fun `incoming call rejected returns to Idle`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        controller.rejectCurrentCall()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("B", signaling.sentOfType("reject").last().payload["call_id"]!!.jsonPrimitive.content)
        assertTrue(audio.stopRingingCount >= 1)
    }

    @Test
    fun `caller receives rejected`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()

        signaling.emit("rejected", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("تماس رد شد", controller.flash.value?.text)
    }

    @Test
    fun `busy returns to Idle with message`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        runCurrent()

        signaling.emit("busy", payload = buildJsonObject { put("extension", "203") })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("داخلی 203 مشغول است", controller.flash.value?.text)
    }

    @Test
    fun `unavailable returns to Idle with message`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        runCurrent()

        signaling.emit("unavailable", payload = buildJsonObject {
            put("extension", "203")
            put("reason", "offline")
        })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("داخلی 203 در دسترس نیست", controller.flash.value?.text)
    }

    @Test
    fun `cannot call self is blocked locally`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("101")
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("نمی‌توانید با داخلی خودتان تماس بگیرید", controller.flash.value?.text)
        assertEquals(0, signaling.sent.size)
    }

    @Test
    fun `cannot start call while another call is active`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        runCurrent()
        controller.startCall("204")
        assertEquals(1, signaling.sentOfType("call").size)
    }

    @Test
    fun `stale call events are ignored`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        rtc.listener?.onConnected()
        assertTrue(controller.callState.value is CallState.InCall)

        // Events for a different call id must be ignored.
        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "Z")
            put("reason", "remote_hangup")
        })
        runCurrent()
        assertTrue(controller.callState.value is CallState.InCall)

        signaling.emit("offer", payload = buildJsonObject { put("call_id", "Z"); put("sdp", "x") })
        runCurrent()
        assertNull(rtc.applyRemoteOfferSdp)

        signaling.emit("answer", payload = buildJsonObject { put("call_id", "Z"); put("sdp", "y") })
        runCurrent()
        assertNull(rtc.setRemoteAnswerSdp)

        signaling.emit("ice_candidate", payload = buildJsonObject {
            put("call_id", "Z")
            putJsonObject("candidate") {
                put("sdpMid", "0")
                put("sdpMLineIndex", 0)
                put("candidate", "candidate:stale")
            }
        })
        runCurrent()
        assertEquals(0, rtc.remoteIce.size)
    }

    @Test
    fun `duplicate accept is prevented`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()

        controller.acceptCurrentCall()
        controller.acceptCurrentCall()
        controller.acceptCurrentCall()
        assertEquals(1, signaling.sentOfType("accept").size)
        assertEquals(CallState.Connecting("B", "101", "محمد"), controller.callState.value)
    }

    @Test
    fun `duplicate hangup is safe`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        controller.endCurrentCall()
        controller.endCurrentCall()
        controller.endCurrentCall()
        assertEquals(1, signaling.sentOfType("hangup").size)
        assertEquals(CallState.Idle, controller.callState.value)
    }

    @Test
    fun `invalid session is cleared on startup`() = runTest {
        buildController()
        sessionStore.save("bad-token", UserDto(name = "User 101", extension = "101"))
        authApi.meResult = Result.failure(
            ApiException(ApiErrorKind.UNAUTHORIZED, null, "expired"),
        )

        controller.initialize()
        runCurrent()

        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertNull(sessionStore.session)
    }

    @Test
    fun `session replaced forces logout`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.emit("session_replaced")
        runCurrent()

        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertNull(sessionStore.session)
        assertTrue(signaling.stopped)
    }

    @Test
    fun `session replaced via error message forces logout`() = runTest {
        buildController()
        authenticateAndConnect()

        // The live backend delivers session replacement as type=error with
        // code=session_replaced and then closes the socket.
        signaling.emit("error", payload = buildJsonObject {
            put("code", "session_replaced")
            put("message", "session replaced by a new connection")
        })
        runCurrent()

        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertNull(sessionStore.session)
        assertTrue(signaling.stopped)
        assertEquals("این داخلی در دستگاه دیگری وارد شده است", controller.flash.value?.text)
    }

    @Test
    fun `websocket auth failure forces logout`() = runTest {
        buildController()
        authenticateAndConnect()

        signaling.setAuthFailed()
        runCurrent()

        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertNull(sessionStore.session)
    }

    @Test
    fun `negotiation timeout ends the call`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()
        assertTrue(controller.callState.value is CallState.Connecting)

        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("برقراری تماس ناموفق بود", controller.flash.value?.text)
    }

    @Test
    fun `webrtc failure sends hangup and cleans up`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()
        signaling.emit("accepted", payload = buildJsonObject { put("call_id", "A") })
        runCurrent()

        rtc.listener?.onFailed("ice_connection_failed")
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("A", signaling.sentOfType("hangup").last().payload["call_id"]!!.jsonPrimitive.content)
        assertEquals("برقراری تماس ناموفق بود", controller.flash.value?.text)
        assertTrue(rtc.closeCount >= 1)
    }

    @Test
    fun `logout clears session and stops signaling`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.logout()
        assertEquals(SessionState.LoggedOut, controller.sessionState.value)
        assertNull(sessionStore.session)
        assertTrue(signaling.stopped)
        assertEquals(SignalingStatus.DISCONNECTED, controller.signalingStatus.value)
    }

    @Test
    fun `no answer ends the call with message`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.startCall("203")
        val requestId = signaling.sent.last().requestId
        signaling.emit("call", requestId, buildJsonObject {
            put("call_id", "A")
            put("status", "ringing")
        })
        runCurrent()

        signaling.emit("call_ended", payload = buildJsonObject {
            put("call_id", "A")
            put("reason", "no_answer")
        })
        runCurrent()
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("پاسخ داده نشد", controller.flash.value?.text)
    }

    @Test
    fun `mute and speaker toggle through gateway`() = runTest {
        buildController()
        authenticateAndConnect()

        controller.toggleMute()
        assertEquals(listOf(true), rtc.muteStates)
        controller.toggleMute()
        assertEquals(listOf(true, false), rtc.muteStates)

        controller.toggleSpeaker()
        assertEquals(listOf(true), audio.speakerStates)
        controller.toggleSpeaker()
        assertEquals(listOf(true, false), audio.speakerStates)
    }

    @Test
    fun `login success stores session and authenticates`() = runTest {
        buildController()
        authApi.loginResult = Result.success(
            ir.tafakoritech.tpcall.data.model.AuthResponse(
                accessToken = "token-x",
                user = UserDto(name = "محمد", extension = "101"),
            ),
        )

        controller.login("101", "12345678")
        runCurrent()

        assertTrue(controller.sessionState.value is SessionState.Authenticated)
        assertEquals("token-x", sessionStore.session?.token)
        assertEquals("101", sessionStore.session?.user?.extension)
        assertNull(controller.authError.value)
    }

    @Test
    fun `login failure maps invalid credentials message`() = runTest {
        buildController()
        authApi.loginResult = Result.failure(
            ApiException(ApiErrorKind.UNAUTHORIZED, "invalid_credentials", "unauthorized"),
        )

        controller.login("101", "wrong")
        runCurrent()

        assertEquals("داخلی یا رمز عبور اشتباه است", controller.authError.value)
        assertNull(sessionStore.session)
        assertTrue(controller.sessionState.value !is SessionState.Authenticated)
    }

    @Test
    fun `register conflict maps extension already exists message`() = runTest {
        buildController()
        authApi.registerResult = Result.failure(
            ApiException(ApiErrorKind.CONFLICT, "extension_already_exists", "conflict"),
        )

        controller.register("نام", "203", "12345678")
        runCurrent()

        assertEquals("این داخلی قبلاً ثبت شده است", controller.authError.value)
    }

    @Test
    fun `accept without microphone permission stays ringing`() = runTest {
        buildController(micPermission = false)
        authenticateAndConnect()

        signaling.emit("incoming_call", payload = incomingCallPayload())
        runCurrent()
        assertEquals(CallState.IncomingRinging("B", "101", "محمد"), controller.callState.value)

        controller.acceptCurrentCall()
        assertEquals(CallState.IncomingRinging("B", "101", "محمد"), controller.callState.value)
        assertEquals(0, signaling.sentOfType("accept").size)
        assertEquals("برای مکالمه دسترسی میکروفون لازم است", controller.flash.value?.text)
    }

    @Test
    fun `outgoing call without microphone permission is blocked`() = runTest {
        buildController(micPermission = false)
        authenticateAndConnect()

        controller.startCall("203")
        assertEquals(CallState.Idle, controller.callState.value)
        assertEquals("برای مکالمه دسترسی میکروفون لازم است", controller.flash.value?.text)
        assertEquals(0, signaling.sentOfType("call").size)
    }
}
