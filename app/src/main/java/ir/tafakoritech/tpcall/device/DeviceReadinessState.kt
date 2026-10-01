package ir.tafakoritech.tpcall.device

/** The individual requirements surfaced on the readiness screen. */
enum class ReadinessItemType {
    MICROPHONE,
    NOTIFICATIONS,
    FULL_SCREEN_CALLS,
    BATTERY_UNRESTRICTED,
    LOCK_SCREEN_DISPLAY,
    BACKGROUND_POPUP,
    AUTO_START,
}

/**
 * Snapshot of the device's readiness for reliable internal VoIP calls. One
 * field per requirement; [NOT_APPLICABLE] is used for items this device does
 * not expose (e.g. no Xiaomi-only rows on Samsung, no full-screen toggle below
 * Android 14), so the UI only ever shows relevant requirements.
 */
data class DeviceReadinessState(
    val microphone: ReadinessStatus,
    val notifications: ReadinessStatus,
    val fullScreenCalls: ReadinessStatus,
    val batteryUnrestricted: ReadinessStatus,
    val lockScreenDisplay: ReadinessStatus,
    val backgroundPopup: ReadinessStatus,
    val autoStart: ReadinessStatus,
    val manufacturer: DeviceManufacturer,
) {
    fun statusOf(type: ReadinessItemType): ReadinessStatus = when (type) {
        ReadinessItemType.MICROPHONE -> microphone
        ReadinessItemType.NOTIFICATIONS -> notifications
        ReadinessItemType.FULL_SCREEN_CALLS -> fullScreenCalls
        ReadinessItemType.BATTERY_UNRESTRICTED -> batteryUnrestricted
        ReadinessItemType.LOCK_SCREEN_DISPLAY -> lockScreenDisplay
        ReadinessItemType.BACKGROUND_POPUP -> backgroundPopup
        ReadinessItemType.AUTO_START -> autoStart
    }

    /** Items in display order, excluding anything not applicable to this device. */
    val visibleItems: List<ReadinessItemType>
        get() = ReadinessItemType.entries.filter { statusOf(it) != ReadinessStatus.NOT_APPLICABLE }

    val totalVisible: Int
        get() = visibleItems.size

    val readyCount: Int
        get() = visibleItems.count { statusOf(it) == ReadinessStatus.READY }

    /**
     * Critical requirements gate the post-login setup assistant. A
     * NOT_APPLICABLE full-screen toggle (pre-Android 14) or pre-granted
     * notification permission does not block the app.
     */
    fun isCriticalReady(): Boolean =
        microphone == ReadinessStatus.READY &&
            notifications != ReadinessStatus.MISSING &&
            fullScreenCalls != ReadinessStatus.MISSING
}
