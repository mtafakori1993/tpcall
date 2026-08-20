package ir.tafakoritech.tpcall.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Call direction relative to the authenticated user (normalized by the backend). */
enum class CallDirection {
    INCOMING,
    OUTGOING,
}

/** Stable call status derived from the persisted end state. */
enum class CallHistoryStatus {
    COMPLETED,
    MISSED,
    REJECTED,
}

/** Android-friendly normalized history item. */
data class CallHistoryItem(
    val id: String,
    val callId: String,
    val direction: CallDirection,
    val status: CallHistoryStatus,
    val peerName: String,
    val peerExtension: String,
    val startedAt: Instant,
    val answeredAt: Instant?,
    val endedAt: Instant?,
    val durationSeconds: Long?,
)

@Serializable
data class CallHistoryResponseDto(
    val calls: List<CallHistoryItemDto> = emptyList(),
    val limit: Int = 30,
    val offset: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class CallHistoryItemDto(
    val id: String = "",
    @SerialName("call_id") val callId: String = "",
    val direction: String = "",
    val status: String = "",
    val peer: CallHistoryPeerDto = CallHistoryPeerDto(),
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
) {
    /** Maps to the domain model; returns null for unknown direction/status or
     * an unparseable timestamp so a single bad row never crashes the list. */
    fun toDomain(): CallHistoryItem? {
        val directionEnum = when (direction) {
            "outgoing" -> CallDirection.OUTGOING
            "incoming" -> CallDirection.INCOMING
            else -> return null
        }
        val statusEnum = when (status) {
            "completed" -> CallHistoryStatus.COMPLETED
            "missed" -> CallHistoryStatus.MISSED
            "rejected" -> CallHistoryStatus.REJECTED
            else -> return null
        }
        val started = runCatching { Instant.parse(startedAt) }.getOrNull() ?: return null
        val answered = answeredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val ended = endedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return CallHistoryItem(
            id = id,
            callId = callId,
            direction = directionEnum,
            status = statusEnum,
            peerName = peer.name,
            peerExtension = peer.extension,
            startedAt = started,
            answeredAt = answered,
            endedAt = ended,
            durationSeconds = durationSeconds,
        )
    }
}

@Serializable
data class CallHistoryPeerDto(
    val name: String = "",
    val extension: String = "",
)
