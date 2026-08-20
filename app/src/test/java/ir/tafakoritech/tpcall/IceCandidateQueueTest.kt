package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.call.IceCandidateQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IceCandidateQueueTest {

    @Test
    fun `candidates buffered until drained`() {
        val queue = IceCandidateQueue()
        queue.add(IceCandidateQueue.RemoteIce("0", 0, "candidate:a"))
        queue.add(IceCandidateQueue.RemoteIce(null, 1, "candidate:b"))
        assertEquals(2, queue.size)

        val drained = queue.drain()
        assertEquals(2, drained.size)
        assertEquals("candidate:a", drained[0].candidate)
        assertEquals("candidate:b", drained[1].candidate)
        assertEquals(0, queue.size)
    }

    @Test
    fun `drain on empty queue returns empty list`() {
        val queue = IceCandidateQueue()
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `clear removes all candidates`() {
        val queue = IceCandidateQueue()
        queue.add(IceCandidateQueue.RemoteIce("0", 0, "candidate:a"))
        queue.clear()
        assertEquals(0, queue.size)
    }
}
