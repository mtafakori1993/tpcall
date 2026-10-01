package ir.tafakoritech.tpcall.device

import android.content.Context
import android.content.Intent

/**
 * Small seam around startActivity / resolveActivity so the OEM settings
 * fallback logic is unit-testable without a real Activity or PackageManager.
 */
interface IntentLauncher {
    fun canResolve(intent: Intent): Boolean
    fun launch(intent: Intent): Boolean
}

class AndroidIntentLauncher(context: Context) : IntentLauncher {
    private val appContext = context.applicationContext

    override fun canResolve(intent: Intent): Boolean = runCatching {
        intent.resolveActivity(appContext.packageManager) != null
    }.getOrDefault(false)

    override fun launch(intent: Intent): Boolean = runCatching {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse { false }
}
