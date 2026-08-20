package ir.tafakoritech.tpcall.data.signaling

import ir.tafakoritech.tpcall.config.AppConfig
import kotlin.random.Random

/**
 * Pure exponential-backoff delay computation, testable without Android.
 *
 * Base sequence (attempt 1,2,3,4,5+): 1s, 2s, 4s, 8s, 15s, 15s, ...
 * Final delay is the base ± 20% jitter, never below 1s and never above 15s.
 */
object SignalingBackoff {

    const val MIN_DELAY_MS: Long = 1_000L

    /** Base delay for the given attempt (1-based), without jitter. */
    fun baseDelayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceAtLeast(0).coerceAtMost(4)
        return (AppConfig.WS_RECONNECT_BASE_MS shl shift)
            .coerceAtMost(AppConfig.WS_RECONNECT_MAX_MS)
    }

    /**
     * Base delay with ±20% jitter, clamped to [MIN_DELAY_MS,
     * WS_RECONNECT_MAX_MS]. Randomness spreads simultaneous reconnects across
     * many devices without ever retrying faster than once per second.
     */
    fun jitteredDelayMs(attempt: Int, random: Random = Random.Default): Long {
        val base = baseDelayMs(attempt).toDouble()
        val low = base * 0.8
        val high = base * 1.2
        val value = if (high <= low) base else random.nextDouble(low, high)
        return value.toLong().coerceIn(MIN_DELAY_MS, AppConfig.WS_RECONNECT_MAX_MS)
    }
}
