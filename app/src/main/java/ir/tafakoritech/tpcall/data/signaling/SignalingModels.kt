package ir.tafakoritech.tpcall.data.signaling

import ir.tafakoritech.tpcall.data.api.ApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WsEnvelope(
    val type: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    /** Returns null when the envelope is not a JSON object. */
    companion object {
        fun parse(raw: String): WsEnvelope? = runCatching {
            ApiClient.json.decodeFromString<WsEnvelope>(raw)
        }.getOrNull()
    }
}

@Serializable
data class PeerInfoDto(
    val extension: String = "",
    val name: String = "",
)

@Serializable
data class IncomingCallPayload(
    @SerialName("call_id") val callId: String? = null,
    val from: PeerInfoDto? = null,
)

@Serializable
data class CallResponsePayload(
    @SerialName("call_id") val callId: String? = null,
    val status: String? = null,
    val to: PeerInfoDto? = null,
    val code: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class SdpPayload(
    @SerialName("call_id") val callId: String? = null,
    val sdp: String? = null,
)

@Serializable
data class IceCandidateDto(
    val sdpMid: String? = null,
    @SerialName("sdpMLineIndex") val sdpMLineIndex: Int = 0,
    val candidate: String? = null,
)

@Serializable
data class IceCandidatePayload(
    @SerialName("call_id") val callId: String? = null,
    val candidate: IceCandidateDto? = null,
)

@Serializable
data class BusyPayload(
    val extension: String? = null,
)

@Serializable
data class UnavailablePayload(
    val extension: String? = null,
    val reason: String? = null,
)

@Serializable
data class CallEndedPayload(
    @SerialName("call_id") val callId: String? = null,
    val reason: String? = null,
)

@Serializable
data class ErrorPayload(
    val code: String? = null,
    val message: String? = null,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class CallIdPayload(
    @SerialName("call_id") val callId: String? = null,
)

/** Typed signaling message produced from a raw [WsEnvelope]. */
sealed interface WsMessage {
    data object Ping : WsMessage
    data object SessionReplaced : WsMessage
    data object Unknown : WsMessage

    data class CallResponse(
        val requestId: String?,
        val callId: String?,
        val status: String?,
        val toExtension: String?,
        val toName: String?,
        val code: String?,
    ) : WsMessage

    data class IncomingCall(
        val callId: String,
        val fromExtension: String,
        val fromName: String,
    ) : WsMessage

    data class Accepted(val callId: String?) : WsMessage
    data class Rejected(val callId: String?) : WsMessage
    data class Busy(val extension: String?) : WsMessage
    data class Unavailable(val extension: String?, val reason: String?) : WsMessage

    data class Offer(val callId: String?, val sdp: String?) : WsMessage
    data class Answer(val callId: String?, val sdp: String?) : WsMessage

    data class IceCandidate(
        val callId: String?,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String?,
    ) : WsMessage

    data class CallEnded(val callId: String?, val reason: String?) : WsMessage
    data class WsError(val code: String?, val message: String?, val callId: String?) : WsMessage
}

/**
 * Maps a raw envelope to a typed [WsMessage]. Unknown or malformed messages are
 * converted to [WsMessage.Unknown] and never crash the caller.
 */
fun WsEnvelope.toWsMessage(): WsMessage {
    return when (type) {
        "ping" -> WsMessage.Ping
        "pong" -> WsMessage.Unknown
        "session_replaced" -> WsMessage.SessionReplaced

        "call" -> {
            val p = payload.payloadAs<CallResponsePayload>()
            WsMessage.CallResponse(
                requestId = requestId,
                callId = p?.callId,
                status = p?.status,
                toExtension = p?.to?.extension,
                toName = p?.to?.name,
                code = p?.code ?: p?.error,
            )
        }

        "incoming_call" -> {
            val p = payload.payloadAs<IncomingCallPayload>()
            val from = p?.from
            WsMessage.IncomingCall(
                callId = p?.callId ?: "",
                fromExtension = from?.extension ?: "",
                fromName = from?.name ?: "",
            )
        }

        "accepted" -> WsMessage.Accepted(payload.payloadAs<CallIdPayload>()?.callId)
        "rejected" -> WsMessage.Rejected(payload.payloadAs<CallIdPayload>()?.callId)
        "busy" -> WsMessage.Busy(payload.payloadAs<BusyPayload>()?.extension)
        "unavailable" -> {
            val p = payload.payloadAs<UnavailablePayload>()
            WsMessage.Unavailable(p?.extension, p?.reason)
        }

        "offer" -> {
            val p = payload.payloadAs<SdpPayload>()
            WsMessage.Offer(p?.callId, p?.sdp)
        }

        "answer" -> {
            val p = payload.payloadAs<SdpPayload>()
            WsMessage.Answer(p?.callId, p?.sdp)
        }

        "ice_candidate" -> {
            val p = payload.payloadAs<IceCandidatePayload>()
            val c = p?.candidate
            WsMessage.IceCandidate(
                callId = p?.callId,
                sdpMid = c?.sdpMid,
                sdpMLineIndex = c?.sdpMLineIndex ?: 0,
                candidate = c?.candidate,
            )
        }

        "call_ended" -> {
            val p = payload.payloadAs<CallEndedPayload>()
            WsMessage.CallEnded(p?.callId, p?.reason)
        }

        "error" -> {
            val p = payload.payloadAs<ErrorPayload>()
            WsMessage.WsError(p?.code, p?.message, p?.callId)
        }

        else -> WsMessage.Unknown
    }
}

private inline fun <reified T> JsonObject.payloadAs(): T? = runCatching {
    ApiClient.json.decodeFromString(
        kotlinx.serialization.serializer<T>(),
        toString(),
    )
}.getOrNull()
