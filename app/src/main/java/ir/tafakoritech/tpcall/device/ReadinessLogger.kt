package ir.tafakoritech.tpcall.device

import android.util.Log

/**
 * Structured readiness logging. Events are short snake_case tags with
 * space-separated key=value fields so they are greppable in logcat. Never logs
 * tokens or credentials.
 */
interface ReadinessLogger {
    fun log(event: String, fields: Map<String, String> = emptyMap())
}

object AndroidReadinessLogger : ReadinessLogger {
    override fun log(event: String, fields: Map<String, String>) {
        val details = fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
        Log.i(TAG, if (details.isEmpty()) event else "$event $details")
    }

    private const val TAG = "DeviceReadiness"
}
