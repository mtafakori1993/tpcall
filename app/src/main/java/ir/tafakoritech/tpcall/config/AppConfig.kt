package ir.tafakoritech.tpcall.config

import ir.tafakoritech.tpcall.BuildConfig

/**
 * Central configuration for the MVP.
 *
 * To point the app at a different server, change the two BuildConfig fields in
 * `app/build.gradle.kts` (defaultConfig.buildConfigField) or edit these values.
 */
object AppConfig {
    val BASE_URL: String = BuildConfig.BASE_URL

    val WS_URL: String = BuildConfig.WS_URL

    const val HEALTH_URL: String = "https://tpcallapi.tafakorpardazan.ir/health"

    const val WS_HEARTBEAT_PING_MS: Long = 25_000L

    const val WS_RECONNECT_BASE_MS: Long = 1_000L

    const val WS_RECONNECT_MAX_MS: Long = 15_000L

    /**
     * Bounded grace window while signaling is down during an active call.
     * WebRTC media may survive briefly; after this the call is ended locally.
     */
    const val SIGNALING_RECOVERY_GRACE_MS: Long = 15_000L

    const val NEGOTIATION_TIMEOUT_MS: Long = 20_000L

    const val REST_TIMEOUT_SECONDS: Long = 10L

    const val FLASH_MESSAGE_DURATION_MS: Long = 3_500L
}
