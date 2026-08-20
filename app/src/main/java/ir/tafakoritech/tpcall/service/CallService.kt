package ir.tafakoritech.tpcall.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.TpCallApplication
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.call.SessionState
import ir.tafakoritech.tpcall.data.signaling.SignalingStatus
import ir.tafakoritech.tpcall.notification.CallNotificationDecision
import ir.tafakoritech.tpcall.notification.CallNotificationLifecycle
import ir.tafakoritech.tpcall.notification.CallNotificationManager
import ir.tafakoritech.tpcall.notification.CallNotificationPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that:
 *  - keeps the signaling WebSocket alive while the user is logged in
 *  - delivers Accept/Reject/Hangup actions from incoming and active call
 *    notifications
 *  - OWNS the call-notification foreground lifecycle: for every ringing/active
 *    VoIP call the service publishes the call notification through
 *    startForeground() (NOTIFICATION_ID_CALL), so CallStyle notifications are
 *    always foreground-service notifications — never bare NotificationManager
 *    posts (which Android rejects on API 31+ with IllegalArgumentException).
 *
 * Foreground type: specialUse (a persistent signaling client for an internal
 * VoIP phone system). The call notification reuses the same declared type via
 * ServiceCompat.startForeground(...).
 */
class CallService : Service() {

    private val container get() = (application as TpCallApplication).container
    private var statusJob: Job? = null
    private var callNotificationJob: Job? = null
    private var foregroundShown = false

    private val callLifecycle = CallNotificationLifecycle()

    override fun onCreate() {
        super.onCreate()
        container.serviceCoordinator.markRunning(true)
        Log.i(TAG, "call_service_created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> container.callController.acceptCurrentCall()
            ACTION_REJECT -> container.callController.rejectCurrentCall()
            ACTION_HANGUP -> container.callController.endCurrentCall()
        }

        ensureForeground()

        if (container.callController.sessionState.value is SessionState.Authenticated) {
            container.callController.startSignaling()
        }

        observeSignalingStatus()
        observeCallState()

        return START_STICKY
    }

    /**
     * Starts the service in the foreground with the signaling notification.
     * Idempotent and safe to call repeatedly.
     */
    private fun ensureForeground() {
        if (foregroundShown) return
        val notification = container.notificationManager.persistentNotification(
            getString(R.string.notif_signaling_text),
        )
        startForegroundCompat(CallNotificationManager.NOTIFICATION_ID_SIGNALING, notification)
        foregroundShown = true
        Log.i(TAG, "call_service_foreground_started")
    }

    /**
     * Wraps startForeground so a platform/OEM rejection (e.g. invalid FGS
     * transition) can never kill the process. The call still proceeds.
     */
    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, notificationId, notification, type)
            Log.i(TAG, "call_service_foreground_started id=$notificationId")
        }.onFailure { e ->
            Log.w(TAG, "call_notification_update_failed ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun observeSignalingStatus() {
        if (statusJob?.isActive == true) return
        statusJob = container.scope.launch {
            container.callController.signalingStatus.collectLatest { status ->
                // While a call is foreground, the call notification is what the
                // user sees; do not overlay the signaling notification on it.
                if (callLifecycle.isForegroundEstablished()) return@collectLatest
                val text = when (status) {
                    SignalingStatus.CONNECTED -> getString(R.string.notif_signaling_connected)
                    SignalingStatus.CONNECTING -> getString(R.string.notif_signaling_text)
                    SignalingStatus.RECONNECTING -> getString(R.string.notif_signaling_reconnecting)
                    else -> getString(R.string.notif_signaling_disconnected)
                }
                val notification = container.notificationManager.persistentNotification(text)
                if (container.notificationManager.hasNotificationPermission()) {
                    runCatching {
                        NotificationManagerCompat.from(this@CallService)
                            .notify(CallNotificationManager.NOTIFICATION_ID_SIGNALING, notification)
                    }.onFailure { e ->
                        Log.w(TAG, "call_notification_update_failed ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * The call-notification foreground lifecycle.
     *
     * IDLE  -> RINGING : startForeground(call notification)   [enter foreground]
     * RINGING -> ACTIVE: startForeground(same id, active)     [update, stays fg]
     * ACTIVE -> ENDED  : stopForeground + cancel              [leave foreground]
     *
     * No per-second ticker is used: the active notification uses the system
     * chronometer (setWhen/setUsesChronometer), so no NotificationManager
     * updates are needed while the call is connected.
     */
    private fun observeCallState() {
        if (callNotificationJob?.isActive == true) return
        callNotificationJob = container.scope.launch {
            container.callController.callState.collectLatest { state ->
                val phase = phaseFor(state)
                when (callLifecycle.transition(phase)) {
                    CallNotificationDecision.EnterForeground,
                    CallNotificationDecision.UpdateForeground,
                    -> enterOrUpdateCallForeground(state, phase)

                    CallNotificationDecision.StopForeground -> stopCallForeground()
                    CallNotificationDecision.None -> Unit
                }
            }
        }
    }

    private fun phaseFor(state: CallState): CallNotificationPhase = when (state) {
        CallState.Idle -> CallNotificationPhase.IDLE
        is CallState.IncomingRinging,
        is CallState.OutgoingRinging,
        -> CallNotificationPhase.RINGING

        is CallState.Connecting,
        is CallState.InCall,
        -> CallNotificationPhase.ACTIVE
    }

    /**
     * Publishes (or updates) the call notification as the foreground-service
     * notification. CallStyle is only ever shown through startForeground(),
     * which is what Android requires.
     */
    private fun enterOrUpdateCallForeground(state: CallState, phase: CallNotificationPhase) {
        val notification = buildCallNotification(state)
        startForegroundCompat(CallNotificationManager.NOTIFICATION_ID_CALL, notification)
        when (state) {
            is CallState.IncomingRinging -> Log.i(
                TAG,
                "incoming_ringtone_started call_id=${state.callId} channel=${CallNotificationManager.CHANNEL_INCOMING_CALL}",
            )
            is CallState.InCall -> Log.i(
                TAG,
                "call_notification_active_updated call_id=${state.callId}",
            )
            else -> Log.i(TAG, "call_notification_foreground_updated")
        }
    }

    private fun buildCallNotification(state: CallState): Notification {
        val manager = container.notificationManager
        return when (state) {
            is CallState.IncomingRinging -> manager.buildIncomingCallNotification(
                callId = state.callId,
                name = state.name,
                extension = state.extension,
            )
            is CallState.OutgoingRinging -> manager.buildActiveCallNotification(
                callId = state.callId.orEmpty(),
                name = state.name ?: state.extension,
                extension = state.extension,
                startedAt = System.currentTimeMillis(),
            )
            is CallState.Connecting -> manager.buildActiveCallNotification(
                callId = state.callId,
                name = state.peerName ?: state.peerExtension,
                extension = state.peerExtension,
                startedAt = System.currentTimeMillis(),
            )
            is CallState.InCall -> manager.buildActiveCallNotification(
                callId = state.callId,
                name = state.peerName ?: state.peerExtension,
                extension = state.peerExtension,
                startedAt = state.startedAt,
            )
            CallState.Idle -> manager.persistentNotification(getString(R.string.notif_signaling_text))
        }
    }

    /**
     * Leaves call-foreground state: removes the call notification and restores
     * the signaling foreground notification so the service stays alive for
     * signaling after the call.
     */
    private fun stopCallForeground() {
        Log.i(TAG, "incoming_ringtone_stopped")
        Log.i(TAG, "call_service_foreground_stopped")
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }.onFailure { e ->
            Log.w(TAG, "call_notification_update_failed ${e.message}")
        }
        container.notificationManager.cancelCallNotification()
        foregroundShown = false
        if (container.callController.sessionState.value is SessionState.Authenticated) {
            ensureForeground()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "call_service_destroyed")
        statusJob?.cancel()
        statusJob = null
        callNotificationJob?.cancel()
        callNotificationJob = null
        container.serviceCoordinator.markRunning(false)
        container.notificationManager.hideAll()
        if (container.callController.sessionState.value is SessionState.Authenticated) {
            // Service stopped without a logout (e.g. system/user action): stop
            // signaling; the Activity restarts the service when next foregrounded.
            container.callController.stopSignaling()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ACCEPT = "ir.tafakoritech.tpcall.action.ACCEPT_CALL"
        const val ACTION_REJECT = "ir.tafakoritech.tpcall.action.REJECT_CALL"
        const val ACTION_HANGUP = "ir.tafakoritech.tpcall.action.HANGUP_CALL"
        private const val TAG = "CallService"
    }
}
