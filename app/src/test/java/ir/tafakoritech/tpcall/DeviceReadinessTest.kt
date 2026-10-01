package ir.tafakoritech.tpcall

import android.content.Intent
import ir.tafakoritech.tpcall.device.BatteryChecker
import ir.tafakoritech.tpcall.device.DeviceInfo
import ir.tafakoritech.tpcall.device.DeviceManufacturer
import ir.tafakoritech.tpcall.device.DeviceReadinessManager
import ir.tafakoritech.tpcall.device.IntentLauncher
import ir.tafakoritech.tpcall.device.OemSettingsNavigator
import ir.tafakoritech.tpcall.device.PermissionsChecker
import ir.tafakoritech.tpcall.device.ReadinessItemType
import ir.tafakoritech.tpcall.device.ReadinessLogger
import ir.tafakoritech.tpcall.device.ReadinessStatus
import ir.tafakoritech.tpcall.device.launchFirstResolvable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceReadinessTest {

    // ---------------------------------------------------------------------
    // Manufacturer normalization
    // ---------------------------------------------------------------------

    @Test
    fun `detects xiaomi from manufacturer or brand aliases`() {
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.detect("Xiaomi", "Redmi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.detect("unknown", "POCO"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.detect("Redmi", "Redmi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.detect("Xiaomi", "Xiaomi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturer.detect("xiaomi", "mi"))
    }

    @Test
    fun `normalizes common manufacturers case-insensitively`() {
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturer.detect("samsung", "samsung"))
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturer.detect("SAMSUNG", "SM-A525F"))
        assertEquals(DeviceManufacturer.GOOGLE, DeviceManufacturer.detect("Google", "google"))
        assertEquals(DeviceManufacturer.ONEPLUS, DeviceManufacturer.detect("OnePlus", "OnePlus"))
        assertEquals(DeviceManufacturer.REALME, DeviceManufacturer.detect("realme", "RMX"))
        assertEquals(DeviceManufacturer.OPPO, DeviceManufacturer.detect("OPPO", "OPPO"))
        assertEquals(DeviceManufacturer.VIVO, DeviceManufacturer.detect("vivo", "vivo"))
        assertEquals(DeviceManufacturer.HUAWEI, DeviceManufacturer.detect("HUAWEI", "HUAWEI"))
        assertEquals(DeviceManufacturer.HONOR, DeviceManufacturer.detect("HONOR", "HONOR"))
    }

    @Test
    fun `unknown or empty values map to other`() {
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.detect("unknown", "unknown"))
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.detect(null, null))
        assertEquals(DeviceManufacturer.OTHER, DeviceManufacturer.detect("  ", null))
    }

    // ---------------------------------------------------------------------
    // Relevance per manufacturer
    // ---------------------------------------------------------------------

    @Test
    fun `samsung gets no xiaomi-only rows`() {
        val state = manager(
            manufacturer = DeviceManufacturer.SAMSUNG,
            sdk = 35,
        ).currentState()

        assertFalse(state.visibleItems.contains(ReadinessItemType.LOCK_SCREEN_DISPLAY))
        assertFalse(state.visibleItems.contains(ReadinessItemType.BACKGROUND_POPUP))
        assertFalse(state.visibleItems.contains(ReadinessItemType.AUTO_START))
        assertTrue(state.visibleItems.contains(ReadinessItemType.MICROPHONE))
        assertTrue(state.visibleItems.contains(ReadinessItemType.BATTERY_UNRESTRICTED))
    }

    @Test
    fun `xiaomi gets xiaomi readiness rows as unknown`() {
        val state = manager(
            manufacturer = DeviceManufacturer.XIAOMI,
            sdk = 35,
        ).currentState()

        assertTrue(state.visibleItems.contains(ReadinessItemType.LOCK_SCREEN_DISPLAY))
        assertTrue(state.visibleItems.contains(ReadinessItemType.BACKGROUND_POPUP))
        assertTrue(state.visibleItems.contains(ReadinessItemType.AUTO_START))
        // No reliable public API -> never falsely reported as enabled.
        assertEquals(ReadinessStatus.UNKNOWN, state.statusOf(ReadinessItemType.LOCK_SCREEN_DISPLAY))
        assertEquals(ReadinessStatus.UNKNOWN, state.statusOf(ReadinessItemType.BACKGROUND_POPUP))
        assertEquals(ReadinessStatus.UNKNOWN, state.statusOf(ReadinessItemType.AUTO_START))
    }

    // ---------------------------------------------------------------------
    // Battery state mapping
    // ---------------------------------------------------------------------

    @Test
    fun `battery unrestricted maps to ready`() {
        val state = manager(battery = true).currentState()
        assertEquals(ReadinessStatus.READY, state.batteryUnrestricted)
        assertEquals(4, state.readyCount)
    }

    @Test
    fun `battery restricted maps to missing`() {
        val state = manager(battery = false).currentState()
        assertEquals(ReadinessStatus.MISSING, state.batteryUnrestricted)
    }

    @Test
    fun `battery restriction is logged when detected`() {
        val logger = FakeLogger()
        manager(battery = false, logger = logger).currentState()
        assertTrue(logger.events.any { it.first == "battery_optimization_detected" })
    }

    // ---------------------------------------------------------------------
    // Full-screen state mapping
    // ---------------------------------------------------------------------

    @Test
    fun `full screen is not applicable below android 14`() {
        val state = manager(sdk = 33, fullScreen = false).currentState()
        assertEquals(ReadinessStatus.NOT_APPLICABLE, state.fullScreenCalls)
        assertFalse(state.visibleItems.contains(ReadinessItemType.FULL_SCREEN_CALLS))
    }

    @Test
    fun `full screen ready when permitted on android 14`() {
        val state = manager(sdk = 34, fullScreen = true).currentState()
        assertEquals(ReadinessStatus.READY, state.fullScreenCalls)
    }

    @Test
    fun `full screen missing when permission denied on android 14`() {
        val state = manager(sdk = 34, fullScreen = false).currentState()
        assertEquals(ReadinessStatus.MISSING, state.fullScreenCalls)
    }

    @Test
    fun `full screen missing is logged`() {
        val logger = FakeLogger()
        manager(sdk = 34, fullScreen = false, logger = logger).currentState()
        assertTrue(logger.events.any { it.first == "full_screen_permission_missing" })
    }

    // ---------------------------------------------------------------------
    // Critical gate
    // ---------------------------------------------------------------------

    @Test
    fun `critical is not ready when microphone missing`() {
        assertFalse(manager(mic = false).currentState().isCriticalReady())
    }

    @Test
    fun `critical is ready when everything standard is present`() {
        assertTrue(manager(mic = true, notif = true, sdk = 35, fullScreen = true, battery = false)
            .currentState().isCriticalReady())
    }

    // ---------------------------------------------------------------------
    // Safe fallback intent behavior
    // ---------------------------------------------------------------------

    @Test
    fun `launchFirstResolvable skips unresolvable and opens fallback`() {
        val first = Intent("com.test.first")
        val second = Intent("com.test.second")
        val fallback = Intent("com.test.fallback")
        val launcher = FakeLauncher(resolvable = { it == fallback })
        val logger = FakeLogger()

        val opened = launchFirstResolvable(launcher, logger, "test", listOf(first, second, fallback))

        assertTrue(opened)
        assertEquals(listOf(first, second, fallback), launcher.attempted)
        assertEquals(listOf(fallback), launcher.launched)
        assertEquals("oem_settings_opened", logger.events.last().first)
    }

    @Test
    fun `launchFirstResolvable opens first resolvable candidate`() {
        val first = Intent("com.test.first")
        val launcher = FakeLauncher(resolvable = { it == first })
        val logger = FakeLogger()

        val opened = launchFirstResolvable(launcher, logger, "test", listOf(first))

        assertTrue(opened)
        assertEquals(listOf(first), launcher.launched)
        assertEquals("oem_settings_opened", logger.events.last().first)
    }

    @Test
    fun `launchFirstResolvable logs unavailable when nothing resolves`() {
        val first = Intent("com.test.a")
        val second = Intent("com.test.b")
        val launcher = FakeLauncher(resolvable = { false })
        val logger = FakeLogger()

        val opened = launchFirstResolvable(launcher, logger, "test", listOf(first, second))

        assertFalse(opened)
        assertEquals(listOf(first, second), launcher.attempted)
        assertEquals("oem_settings_unavailable", logger.events.last().first)
    }

    @Test
    fun `navigator battery exemption never crashes when no OEM activity resolves`() {
        // No candidate (including the app-details fallback) resolves: the
        // navigator must return false and log, never throw.
        val logger = FakeLogger()
        val navigator = OemSettingsNavigator(
            launcher = FakeLauncher(resolvable = { false }),
            packageName = "ir.tafakoritech.tpcall",
            logger = logger,
        )
        assertFalse(navigator.openBatteryExemption())
        assertTrue(logger.events.any { it.first == "oem_settings_unavailable" })
    }

    // ---------------------------------------------------------------------
    // Re-check after resume
    // ---------------------------------------------------------------------

    @Test
    fun `refresh re-reads system state and logs the grant transition`() {
        val batteryHolder = java.util.concurrent.atomic.AtomicBoolean(false)
        val logger = FakeLogger()
        val m = manager(
            battery = false,
            logger = logger,
            batteryProvider = { batteryHolder.get() },
        )

        assertEquals(ReadinessStatus.MISSING, m.refresh().batteryUnrestricted)
        batteryHolder.set(true)
        assertEquals(ReadinessStatus.READY, m.refresh().batteryUnrestricted)
        assertTrue(logger.events.any { it.first == "battery_exemption_granted" })
        assertTrue(logger.events.any { it.first == "readiness_refreshed" })
    }

    @Test
    fun `openSetting routes battery to navigator and logs request`() {
        val logger = FakeLogger()
        val m = manager(logger = logger)
        m.openSetting(ReadinessItemType.BATTERY_UNRESTRICTED)
        assertTrue(logger.events.any { it.first == "battery_exemption_requested" })
    }

    // ---------------------------------------------------------------------

    private fun manager(
        manufacturer: DeviceManufacturer = DeviceManufacturer.SAMSUNG,
        sdk: Int = 35,
        mic: Boolean = true,
        notif: Boolean = true,
        fullScreen: Boolean = true,
        battery: Boolean = true,
        logger: ReadinessLogger = FakeLogger(),
        batteryProvider: () -> Boolean = { battery },
    ): DeviceReadinessManager = DeviceReadinessManager(
        deviceInfo = DeviceInfo(
            manufacturer = manufacturer,
            brand = "TestBrand",
            model = "TestModel",
            sdkInt = sdk,
            osLabel = "Android 15",
        ),
        permissions = object : PermissionsChecker {
            override fun hasRecordAudio() = mic
            override fun hasPostNotifications() = notif
            override fun canUseFullScreenIntent() = fullScreen
        },
        battery = object : BatteryChecker {
            override fun isIgnoringBatteryOptimizations() = batteryProvider()
        },
        navigator = OemSettingsNavigator(
            launcher = FakeLauncher(),
            packageName = "ir.tafakoritech.tpcall",
            logger = logger,
        ),
        logger = logger,
    )
}

class FakeLauncher(private val resolvable: (Intent) -> Boolean = { true }) : IntentLauncher {
    val attempted = mutableListOf<Intent>()
    val launched = mutableListOf<Intent>()

    override fun canResolve(intent: Intent): Boolean {
        attempted.add(intent)
        return resolvable(intent)
    }

    override fun launch(intent: Intent): Boolean {
        launched.add(intent)
        return true
    }
}

class FakeLogger : ReadinessLogger {
    val events = mutableListOf<Pair<String, Map<String, String>>>()

    override fun log(event: String, fields: Map<String, String>) {
        events.add(event to fields)
    }
}
