package ir.tafakoritech.tpcall.di

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import ir.tafakoritech.tpcall.call.AudioRouteManager
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.WebRtcManager
import ir.tafakoritech.tpcall.data.api.ApiClient
import ir.tafakoritech.tpcall.data.api.AuthApi
import ir.tafakoritech.tpcall.data.session.SessionStore
import ir.tafakoritech.tpcall.data.signaling.SignalingClient
import ir.tafakoritech.tpcall.device.AndroidBatteryChecker
import ir.tafakoritech.tpcall.device.AndroidIntentLauncher
import ir.tafakoritech.tpcall.device.AndroidPermissionsChecker
import ir.tafakoritech.tpcall.device.AndroidReadinessLogger
import ir.tafakoritech.tpcall.device.DeviceInfoFactory
import ir.tafakoritech.tpcall.device.DeviceReadinessManager
import ir.tafakoritech.tpcall.device.OemSettingsNavigator
import ir.tafakoritech.tpcall.notification.CallNotificationManager
import ir.tafakoritech.tpcall.service.CallService
import ir.tafakoritech.tpcall.telecom.TpTelecomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency wiring for the app. Small enough that a DI framework is
 * not warranted for this MVP.
 */
class AppContainer(private val appContext: Context) {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttpClient: OkHttpClient = ApiClient.build(
        OkHttpClient.Builder()
            .connectTimeout(ir.tafakoritech.tpcall.config.AppConfig.REST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ir.tafakoritech.tpcall.config.AppConfig.REST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ir.tafakoritech.tpcall.config.AppConfig.REST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(ir.tafakoritech.tpcall.config.AppConfig.WS_HEARTBEAT_PING_MS, TimeUnit.MILLISECONDS)
            .build(),
    )

    val sessionStore = SessionStore(appContext)
    val authApi = AuthApi(okHttpClient)
    val notificationManager = CallNotificationManager(appContext)

    val deviceReadinessManager = DeviceReadinessManager(
        deviceInfo = DeviceInfoFactory.create(),
        permissions = AndroidPermissionsChecker(appContext),
        battery = AndroidBatteryChecker(appContext),
        navigator = OemSettingsNavigator(
            launcher = AndroidIntentLauncher(appContext),
            packageName = appContext.packageName,
            logger = AndroidReadinessLogger,
        ),
        logger = AndroidReadinessLogger,
    )
    val audioRouteManager = AudioRouteManager(appContext)
    val webRtcManager = WebRtcManager(appContext, scope, Dispatchers.Main.immediate)
    val telecomManager = TpTelecomManager(appContext, scope)

    val callController = CallController(
        scope = scope,
        sessionStore = sessionStore,
        authApi = authApi,
        signalingFactory = { token ->
            SignalingClient(
                connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager,
                client = okHttpClient,
                scope = scope,
            )
        },
        rtc = webRtcManager,
        audio = audioRouteManager,
        notifier = notificationManager,
        telecom = telecomManager,
        micPermissionProvider = { hasMicrophonePermission() },
    )

    val serviceCoordinator = ServiceCoordinator(appContext)

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
}

/** Starts/stops the signaling foreground service and tracks its liveness. */
class ServiceCoordinator(private val context: Context) {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun markRunning(value: Boolean) {
        _running.value = value
    }

    fun startService() {
        if (_running.value) return
        val intent = Intent(context, CallService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or similar; the service
            // will be started again when the app is next in the foreground.
            android.util.Log.w("ServiceCoordinator", "start_service_failed ${e.message}")
        }
    }

    fun stopService() {
        if (!_running.value) return
        context.stopService(Intent(context, CallService::class.java))
    }
}
