package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.data.signaling.WsEnvelope
import ir.tafakoritech.tpcall.data.signaling.WsMessage
import ir.tafakoritech.tpcall.data.signaling.toWsMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingMessagesTest {

    private fun envelope(type: String, payload: String): WsEnvelope {
        val parsed = WsEnvelope.parse("""{"type":"$type","payload":$payload}""")
        assertNotNull(parsed)
        return parsed!!
    }

    @Test
    fun `parses incoming_call`() {
        val msg = envelope(
            "incoming_call",
            """{"call_id":"A","from":{"extension":"101","name":"محمد"}}""",
        ).toWsMessage()
        assertTrue(msg is WsMessage.IncomingCall)
        msg as WsMessage.IncomingCall
        assertEquals("A", msg.callId)
        assertEquals("101", msg.fromExtension)
        assertEquals("محمد", msg.fromName)
    }

    @Test
    fun `parses call response with ringing`() {
        val msg = envelope(
            "call",
            """{"call_id":"A","status":"ringing","to":{"extension":"203","name":"حسابداری"}}""",
        ).toWsMessage()
        msg as WsMessage.CallResponse
        assertEquals("A", msg.callId)
        assertEquals("ringing", msg.status)
        assertEquals("203", msg.toExtension)
        assertEquals("حسابداری", msg.toName)
        assertNull(msg.code)
    }

    @Test
    fun `parses call response error`() {
        val msg = envelope("call", """{"code":"cannot_call_self"}""").toWsMessage()
        msg as WsMessage.CallResponse
        assertEquals("cannot_call_self", msg.code)
    }

    @Test
    fun `parses offer answer and ice`() {
        val offer = envelope("offer", """{"call_id":"A","sdp":"v=0"}""").toWsMessage()
        offer as WsMessage.Offer
        assertEquals("A", offer.callId)
        assertEquals("v=0", offer.sdp)

        val ice = envelope(
            "ice_candidate",
            """{"call_id":"A","candidate":{"sdpMid":"0","sdpMLineIndex":0,"candidate":"candidate:1"}}""",
        ).toWsMessage()
        ice as WsMessage.IceCandidate
        assertEquals("A", ice.callId)
        assertEquals("candidate:1", ice.candidate)
        assertEquals(0, ice.sdpMLineIndex)
    }

    @Test
    fun `parses call_ended with reason`() {
        val msg = envelope("call_ended", """{"call_id":"A","reason":"no_answer"}""").toWsMessage()
        msg as WsMessage.CallEnded
        assertEquals("A", msg.callId)
        assertEquals("no_answer", msg.reason)
    }

    @Test
    fun `unknown type maps to Unknown`() {
        assertEquals(WsMessage.Unknown, envelope("new_future_type", "{}").toWsMessage())
    }

    @Test
    fun `malformed payload does not crash`() {
        val msg = envelope("incoming_call", """{"call_id":"A"}""").toWsMessage()
        msg as WsMessage.IncomingCall
        assertEquals("", msg.fromExtension)
    }

    @Test
    fun `malformed raw json is ignored`() {
        assertNull(WsEnvelope.parse("not json"))
    }

    @Test
    fun `request id preserved`() {
        val raw = """{"type":"call","request_id":"req-1","payload":{"to":"203"}}"""
        val env = WsEnvelope.parse(raw)!!
        assertEquals("req-1", env.requestId)
        val msg = env.toWsMessage() as WsMessage.CallResponse
        assertEquals("req-1", msg.requestId)
    }

    @Test
    fun `envelope can be serialized for sending`() {
        val payload = buildJsonObject {
            put("call_id", "A")
            putJsonObject("candidate") {
                put("sdpMid", "0")
                put("sdpMLineIndex", 0)
                put("candidate", "candidate:1")
            }
        }
        val raw = ir.tafakoritech.tpcall.data.api.ApiClient.json.encodeToString(
            WsEnvelope(type = "ice_candidate", requestId = null, payload = payload),
        )
        assertTrue(raw.contains("\"type\":\"ice_candidate\""))
    }
}
