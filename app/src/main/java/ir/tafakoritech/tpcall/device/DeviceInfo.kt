package ir.tafakoritech.tpcall.device

/**
 * Immutable snapshot of the running device, read once at app startup. Pure data
 * so the readiness logic can be unit-tested without Android framework access.
 */
data class DeviceInfo(
    val manufacturer: DeviceManufacturer,
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val osLabel: String,
) {
    /** Persian display name for the readiness header. */
    val manufacturerLabel: String = when (manufacturer) {
        DeviceManufacturer.XIAOMI -> "شیائومی"
        DeviceManufacturer.SAMSUNG -> "سامسونگ"
        DeviceManufacturer.GOOGLE -> "گوگل"
        DeviceManufacturer.OPPO -> "اوپو"
        DeviceManufacturer.REALME -> "ریلمی"
        DeviceManufacturer.ONEPLUS -> "وان‌پلاس"
        DeviceManufacturer.VIVO -> "ویوو"
        DeviceManufacturer.HUAWEI -> "هواوی"
        DeviceManufacturer.HONOR -> "آنر"
        DeviceManufacturer.OTHER -> brand.ifBlank { "نامشخص" }
    }

    companion object {
        /** User-facing Android version from the API level. */
        fun androidVersionName(sdk: Int): String = when (sdk) {
            28 -> "9"
            29 -> "10"
            30 -> "11"
            31 -> "12"
            32 -> "12L"
            33 -> "13"
            34 -> "14"
            35 -> "15"
            36 -> "16"
            37 -> "17"
            else -> sdk.toString()
        }
    }
}
