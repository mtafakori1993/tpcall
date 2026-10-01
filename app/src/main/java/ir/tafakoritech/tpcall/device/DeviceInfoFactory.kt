package ir.tafakoritech.tpcall.device

import android.annotation.SuppressLint
import android.os.Build
import java.lang.reflect.Method

/**
 * Builds a [DeviceInfo] snapshot from the running device. Reads the MIUI/HyperOS
 * version property reflectively (it is a hidden system property, not a public
 * API) purely for the header label; every read is guarded and non-fatal.
 */
object DeviceInfoFactory {
    fun create(): DeviceInfo {
        val manufacturer = DeviceManufacturer.detect(Build.MANUFACTURER, Build.BRAND)
        val androidLabel = "Android ${DeviceInfo.androidVersionName(Build.VERSION.SDK_INT)}"
        val osLabel = if (manufacturer.isXiaomiFamily) {
            val miui = systemProperty("ro.miui.ui.version.name")
            if (miui != null) "HyperOS/MIUI $miui · $androidLabel" else "HyperOS/MIUI · $androidLabel"
        } else {
            androidLabel
        }
        return DeviceInfo(
            manufacturer = manufacturer,
            brand = Build.BRAND,
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            osLabel = osLabel,
        )
    }

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method: Method = clazz.getMethod("get", String::class.java)
        (method.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
