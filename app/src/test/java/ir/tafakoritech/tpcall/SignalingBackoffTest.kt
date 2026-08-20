package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.data.signaling.SignalingBackoff
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the exponential-backoff-with-jitter policy used by the signaling
 * reconnect worker.
 */
class SignalingBackoffTest {

    @Test
    fun `base delays follow 1-2-4-8-15 sequence`() {
        assertEquals(1_000L, SignalingBackoff.baseDelayMs(1))
        assertEquals(2_000L, SignalingBackoff.baseDelayMs(2))
        assertEquals(4_000L, SignalingBackoff.baseDelayMs(3))
        assertEquals(8_000L, SignalingBackoff.baseDelayMs(4))
    }

    @Test
    fun `backoff caps at 15 seconds`() {
        assertEquals(15_000L, SignalingBackoff.baseDelayMs(5))
        assertEquals(15_000L, SignalingBackoff.baseDelayMs(6))
        assertEquals(15_000L, SignalingBackoff.baseDelayMs(10))
        assertEquals(15_000L, SignalingBackoff.baseDelayMs(100))
    }

    @Test
    fun `jittered delay stays within bounds`() {
        val random = Random(42)
        repeat(200) { attempt ->
            val delay = SignalingBackoff.jitteredDelayMs(attempt + 1, random)
            assertTrue(
                "attempt=$attempt delay=$delay must be >= 1s",
                delay >= SignalingBackoff.MIN_DELAY_MS,
            )
            assertTrue(
                "attempt=$attempt delay=$delay must be <= 15s",
                delay <= 15_000L,
            )
        }
    }

    @Test
    fun `jittered delay deviates from base within plus-minus 20 percent`() {
        val random = Random(7)
        repeat(100) { attempt ->
            val base = SignalingBackoff.baseDelayMs(attempt + 1)
            val delay = SignalingBackoff.jitteredDelayMs(attempt + 1, random)
            val low = (base * 0.8).toLong().coerceAtLeast(SignalingBackoff.MIN_DELAY_MS)
            val high = (base * 1.2).toLong().coerceAtMost(15_000L)
            assertTrue(
                "attempt=$attempt base=$base delay=$delay must be within [$low,$high]",
                delay in low..high,
            )
        }
    }

    @Test
    fun `first retry is approximately one second`() {
        val random = Random(1)
        repeat(50) {
            val delay = SignalingBackoff.jitteredDelayMs(1, random)
            assertTrue(delay in 800L..1_200L)
        }
    }

    @Test
    fun `second retry is approximately two seconds`() {
        val random = Random(2)
        repeat(50) {
            val delay = SignalingBackoff.jitteredDelayMs(2, random)
            assertTrue(delay in 1_600L..2_400L)
        }
    }

    @Test
    fun `never retries faster than once per second`() {
        val random = Random(3)
        repeat(500) { i ->
            val delay = SignalingBackoff.jitteredDelayMs((i % 8) + 1, random)
            assertTrue("delay=$delay must never be < 1s", delay >= 1_000L)
        }
    }
}
