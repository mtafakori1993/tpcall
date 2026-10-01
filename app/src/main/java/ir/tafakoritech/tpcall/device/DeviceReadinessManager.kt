package ir.tafakoritech.tpcall.device

import android.os.Build

/**
 * Central device-readiness component. Owns device detection, permission and
 * battery checks, OEM settings navigation, and the re-check-on-resume contract
 * ([refresh] must be called every time the user returns from a settings page).
 *
 * All Android framework access lives behind small interfaces
 * ([PermissionsChecker], [BatteryChecker], [IntentLauncher]) so the logic is
 * unit-testable and never scattered across Compose screens.
 */
class DeviceReadinessManager(
    val deviceInfo: DeviceInfo,
    private val permissions: PermissionsChecker,
    private val battery: BatteryChecker,
    private val navigator: OemSettingsNavigator,
    private val logger: ReadinessLogger,
) {
    private var wasBatteryRestricted: Boolean? = null

    init {
        logger.log(
            "manufacturer_detected",
            mapOf(
                "manufacturer" to deviceInfo.manufacturer.key,
                "brand" to deviceInfo.brand,
                "model" to deviceInfo.model,
                "sdk" to deviceInfo.sdkInt.toString(),
                "os" to deviceInfo.osLabel,
            ),
        )
    }

    /** Recomputes the full readiness snapshot from the real system values. */
    fun currentState(): DeviceReadinessState {
        val mic = if (permissions.hasRecordAudio()) {
            ReadinessStatus.READY
        } else {
            ReadinessStatus.MISSING
        }
        val notif = if (permissions.hasPostNotifications()) {
            ReadinessStatus.READY
        } else {
            ReadinessStatus.MISSING
        }
        val fullScreen = when {
            deviceInfo.sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ReadinessStatus.NOT_APPLICABLE
            permissions.canUseFullScreenIntent() -> ReadinessStatus.READY
            else -> ReadinessStatus.MISSING
        }
        val batteryUnrestricted = if (battery.isIgnoringBatteryOptimizations()) {
            ReadinessStatus.READY
        } else {
            ReadinessStatus.MISSING
        }

        // Xiaomi lock-screen / background / auto-start settings have no reliable
        // public read API, so they surface as UNKNOWN ("نیاز به بررسی") rather
        // than being falsely reported as enabled. Non-Xiaomi devices do not show
        // these rows at all.
        val isXiaomi = deviceInfo.manufacturer.isXiaomiFamily
        val lockScreen = if (isXiaomi) ReadinessStatus.UNKNOWN else ReadinessStatus.NOT_APPLICABLE
        val backgroundPopup = if (isXiaomi) ReadinessStatus.UNKNOWN else ReadinessStatus.NOT_APPLICABLE
        val autoStart = if (isXiaomi) ReadinessStatus.UNKNOWN else ReadinessStatus.NOT_APPLICABLE

        if (fullScreen == ReadinessStatus.MISSING) {
            logger.log(
                "full_screen_permission_missing",
                mapOf("setting" to "full_screen_calls", "sdk" to deviceInfo.sdkInt.toString()),
            )
        }
        if (batteryUnrestricted == ReadinessStatus.MISSING) {
            logger.log("battery_optimization_detected", mapOf("status" to "restricted"))
        }
        logger.log(
            "device_readiness_checked",
            mapOf(
                "manufacturer" to deviceInfo.manufacturer.key,
                "brand" to deviceInfo.brand,
                "sdk" to deviceInfo.sdkInt.toString(),
                "mic" to mic.name,
                "notifications" to notif.name,
                "full_screen" to fullScreen.name,
                "battery" to batteryUnrestricted.name,
            ),
        )
        return DeviceReadinessState(
            microphone = mic,
            notifications = notif,
            fullScreenCalls = fullScreen,
            batteryUnrestricted = batteryUnrestricted,
            lockScreenDisplay = lockScreen,
            backgroundPopup = backgroundPopup,
            autoStart = autoStart,
            manufacturer = deviceInfo.manufacturer,
        )
    }

    /**
     * Re-checks everything after returning from a settings page. Also detects
     * the battery-exemption transition so the grant is logged exactly once.
     */
    fun refresh(): DeviceReadinessState {
        val restricted = !battery.isIgnoringBatteryOptimizations()
        if (wasBatteryRestricted == true && !restricted) {
            logger.log("battery_exemption_granted", mapOf("status" to "unrestricted"))
        }
        wasBatteryRestricted = restricted
        val state = currentState()
        logger.log(
            "readiness_refreshed",
            mapOf(
                "manufacturer" to deviceInfo.manufacturer.key,
                "ready" to state.readyCount.toString(),
                "total" to state.totalVisible.toString(),
            ),
        )
        return state
    }

    /** Opens the most relevant system/OEM settings page for [type]. */
    fun openSetting(type: ReadinessItemType): Boolean = when (type) {
        ReadinessItemType.FULL_SCREEN_CALLS -> navigator.openFullScreenIntentSettings()
        ReadinessItemType.BATTERY_UNRESTRICTED -> {
            logger.log("battery_exemption_requested", mapOf("status" to "requesting"))
            navigator.openBatteryExemption()
        }
        ReadinessItemType.LOCK_SCREEN_DISPLAY -> navigator.openXiaomiLockScreen()
        ReadinessItemType.BACKGROUND_POPUP -> navigator.openXiaomiBackgroundPopup()
        ReadinessItemType.AUTO_START -> navigator.openXiaomiAutoStart()

        // Runtime permissions are requested by the UI via ActivityResult launchers,
        // not through settings intents.
        ReadinessItemType.MICROPHONE,
        ReadinessItemType.NOTIFICATIONS,
        -> false
    }

    /** Generic app-settings fallback that works on every device. */
    fun openAppDetailsSettings(): Boolean = navigator.openAppDetails()
}
