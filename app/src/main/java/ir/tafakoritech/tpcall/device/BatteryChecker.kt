package ir.tafakoritech.tpcall.device

import android.content.Context
import android.os.PowerManager

/**
 * Read-only view of the Android battery-optimization exemption. Abstracted so
 * the readiness logic is unit-testable.
 */
interface BatteryChecker {
    fun isIgnoringBatteryOptimizations(): Boolean
}

class AndroidBatteryChecker(context: Context) : BatteryChecker {
    private val appContext = context.applicationContext

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching {
            pm.isIgnoringBatteryOptimizations(appContext.packageName)
        }.getOrDefault(false)
    }
}
