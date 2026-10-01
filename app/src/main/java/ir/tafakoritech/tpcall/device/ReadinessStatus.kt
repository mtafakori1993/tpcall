package ir.tafakoritech.tpcall.device

/**
 * Readiness of a single requirement. OEM-specific settings are often not
 * readable through a public API, so booleans would be dishonest there —
 * [UNKNOWN] represents "نیاز به بررسی" and can be acknowledged by the user.
 */
enum class ReadinessStatus {
    READY,
    MISSING,
    UNKNOWN,
    NOT_APPLICABLE,
}
