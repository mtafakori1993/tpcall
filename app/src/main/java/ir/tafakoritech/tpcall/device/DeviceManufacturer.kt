package ir.tafakoritech.tpcall.device

/**
 * Normalized OEM detection. Both Build.MANUFACTURER and Build.BRAND are taken
 * into account because e.g. a POCO device reports brand "POCO" but
 * manufacturer "Xiaomi", and "Redmi"/"Mi" devices report their own brand.
 *
 * Matching is case-insensitive substring matching on the lowercased values so
 * that no assumption about a single translated label or casing is made.
 */
enum class DeviceManufacturer(val key: String) {
    XIAOMI("xiaomi"),
    SAMSUNG("samsung"),
    GOOGLE("google"),
    OPPO("oppo"),
    REALME("realme"),
    ONEPLUS("oneplus"),
    VIVO("vivo"),
    HUAWEI("huawei"),
    HONOR("honor"),
    OTHER("other");

    /** True for the Xiaomi/Redmi/POCO family that MIUI/HyperOS ships on. */
    val isXiaomiFamily: Boolean
        get() = this == XIAOMI

    companion object {
        fun detect(manufacturer: String?, brand: String?): DeviceManufacturer {
            val source = listOfNotNull(manufacturer, brand)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            fun matches(vararg needles: String): Boolean =
                source.any { s -> needles.any { s.contains(it) } }

            return when {
                matches("xiaomi", "redmi", "poco", "miui", "hyperos") -> XIAOMI
                matches("samsung") -> SAMSUNG
                matches("google") -> GOOGLE
                matches("oneplus") -> ONEPLUS
                matches("realme") -> REALME
                matches("oppo") -> OPPO
                matches("vivo") -> VIVO
                matches("honor") -> HONOR
                matches("huawei") -> HUAWEI
                else -> OTHER
            }
        }
    }
}
