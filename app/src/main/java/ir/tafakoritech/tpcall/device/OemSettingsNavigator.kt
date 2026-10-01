package ir.tafakoritech.tpcall.device

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Builds system / OEM settings intents and opens the first candidate the device
 * can resolve, always ending in the app-details fallback so a missing OEM
 * activity can never crash or silently do nothing. Never attempts to silently
 * grant privileged permissions — it only makes the correct settings page
 * reachable.
 *
 * Xiaomi component names are the documented MIUI/HyperOS activities; every
 * candidate is guarded by [IntentLauncher.canResolve] so a renamed/missing
 * activity on a newer ROM simply falls back to the next candidate.
 *
 * This shared helper drives every navigation and is what the safe-fallback
 * unit tests exercise.
 */
fun launchFirstResolvable(
    launcher: IntentLauncher,
    logger: ReadinessLogger,
    name: String,
    intents: List<Intent>,
): Boolean {
    for (intent in intents) {
        if (launcher.canResolve(intent) && launcher.launch(intent)) {
            logger.log("oem_settings_opened", mapOf("setting" to name))
            return true
        }
    }
    logger.log("oem_settings_unavailable", mapOf("setting" to name))
    return false
}

class OemSettingsNavigator(
    private val launcher: IntentLauncher,
    private val packageName: String,
    private val logger: ReadinessLogger,
) {
    /** Generic app settings page; available on every Android device. */
    fun openAppDetails(): Boolean =
        launchFirstResolvable(launcher, logger, "app_details", listOf(appDetails()))

    /**
     * Requests the battery-optimization exemption for TP Call. Falls back to
     * the global ignore-battery list, then to the app details page.
     */
    fun openBatteryExemption(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "battery_exemption",
        listOfNotNull(
            requestIgnoreBatteryOptimizations(),
            ignoreBatteryOptimizationList(),
            appDetails(),
        ),
    )

    /** Opens the app's OEM/Android battery page (no exemption request). */
    fun openBatteryList(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "battery_list",
        listOfNotNull(ignoreBatteryOptimizationList(), appDetails()),
    )

    /** Android 14+ per-app full-screen-intent permission page. */
    fun openFullScreenIntentSettings(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "full_screen_calls",
        listOfNotNull(fullScreenIntentSettings(), appDetails()),
    )

    /** Android app notification settings for TP Call. */
    fun openNotificationSettings(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "notifications",
        listOfNotNull(appNotificationSettings(), appDetails()),
    )

    /** Closest Xiaomi app-permission page (covers "show on lock screen"). */
    fun openXiaomiLockScreen(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "xiaomi_lock_screen",
        listOfNotNull(xiaomiAppPermissionEditor(), appDetails()),
    )

    /** Xiaomi background app management (background popup permission). */
    fun openXiaomiBackgroundPopup(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "xiaomi_background_popup",
        listOfNotNull(xiaomiBackgroundAppManagement(), appDetails()),
    )

    /** Xiaomi auto-start management. */
    fun openXiaomiAutoStart(): Boolean = launchFirstResolvable(
        launcher,
        logger,
        "xiaomi_auto_start",
        listOfNotNull(xiaomiAutoStart(), appDetails()),
    )

    // ---------------------------------------------------------------------

    private fun appDetails(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun appNotificationSettings(): Intent? =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

    private fun fullScreenIntentSettings(): Intent? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
        } else {
            null
        }

    private fun requestIgnoreBatteryOptimizations(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))

    private fun ignoreBatteryOptimizationList(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // MIUI/HyperOS known component names. Guarded by canResolve() — never
    // blindly launched, because they differ between MIUI and HyperOS versions.

    private fun xiaomiAppPermissionEditor(): Intent? =
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
            )
            .putExtra("extra_pkgname", packageName)

    private fun xiaomiBackgroundAppManagement(): Intent? =
        Intent("miui.intent.action.OP_BACKGROUND_APP_MANAGEMENT")
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.background.AppBackgroundManagementActivity",
            )

    private fun xiaomiAutoStart(): Intent? =
        Intent("miui.intent.action.OP_AUTO_START")
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            .putExtra("extra_pkgname", packageName)
}
