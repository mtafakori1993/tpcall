package ir.tafakoritech.tpcall.call

/**
 * Buffers remote ICE candidates that arrive before the remote description has
 * been applied, and flushes them afterwards. Android-free and unit-testable.
 */
class IceCandidateQueue {

    data class RemoteIce(
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String,
    )

    private val queue = ArrayDeque<RemoteIce>()

    val size: Int get() = queue.size

    fun add(ice: RemoteIce) {
        queue.addLast(ice)
    }

    /** Returns and clears all buffered candidates. */
    fun drain(): List<RemoteIce> {
        if (queue.isEmpty()) return emptyList()
        val result = queue.toList()
        queue.clear()
        return result
    }

    fun clear() {
        queue.clear()
    }
}
