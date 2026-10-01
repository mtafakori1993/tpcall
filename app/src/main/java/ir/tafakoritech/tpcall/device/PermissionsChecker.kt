package ir.tafakoritech.tpcall.device

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Read-only view of the permissions TP Call needs. Abstracted so the readiness
 * logic is unit-testable without Android framework stubs.
 */
interface PermissionsChecker {
    fun hasRecordAudio(): Boolean
    fun hasPostNotifications(): Boolean

    /**
     * Android 14+ made full-screen-intent capability a user-managed setting.
     * Below API 34 it is granted by default (no user toggle exists).
     */
    fun canUseFullScreenIntent(): Boolean
}

class AndroidPermissionsChecker(context: Context) : PermissionsChecker {
    private val appContext = context.applicationContext

    override fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    override fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(false)
    }
}
