package ir.tafakoritech.tpcall.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.tafakoritech.tpcall.MainActivity
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.call.CallNotifier
import ir.tafakoritech.tpcall.service.CallService

/**
 * Builds all notifications and owns the single stable call-notification ID.
 *
 * Notification lifecycle: the [ir.tafakoritech.tpcall.service.CallService]
 * is the only publisher of the call notification. It must call
 * [NotificationManager]'s startForeground() with [NOTIFICATION_ID_CALL] before
 * any CallStyle notification is shown; this class only builds notifications and
 * exposes guarded publish helpers that never crash the call.
 *
 * Channels:
 * - Signaling (persistent, low importance): [CHANNEL_SIGNALING].
 * - Incoming calls (ringtone): [CHANNEL_INCOMING_CALL] — IMPORTANCE_HIGH, a
 *   dedicated ringtone channel whose sound is the default RINGTONE uri (never
 *   the notification uri) with USAGE_NOTIFICATION_RINGTONE audio semantics so
 *   incoming calls follow the Ring volume on OEMs like Xiaomi/MIUI.
 *   NOTE: Android persists channel sound config; the channel id is versioned
 *   (_v2) so old installs get the new ringtone channel instead of reusing the
 *   previous notification-sound channel.
 * - Active calls (silent): [CHANNEL_ACTIVE_CALL] — no sound, so the ongoing
 *   foreground call notification never replays a ringtone after answer.
 *
 * CallStyle (android.app.Notification.CallStyle) is available since API 31;
 * below that a high-priority NotificationCompat fallback is used.
 */
class CallNotificationManager(context: Context) : CallNotifier {

    private val appContext = context.applicationContext
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val ringtoneUri: Uri?
        get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i(TAG, "notification_channels_init_started")
            runCatching {
                // Old development channel (notification-sound) must be removed so a
                // fresh channel with ringtone semantics is created; Android will not
                // update sound/attributes of an already-created channel.
                deleteLegacyChannel(CHANNEL_CALLS_LEGACY)

                val signaling = NotificationChannel(
                    CHANNEL_SIGNALING,
                    appContext.getString(R.string.channel_signaling_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = appContext.getString(R.string.channel_signaling_desc)
                    setShowBadge(false)
                }

                // Built inline inside createChannels() instead of a lazy property so
                // this method never reads a property initialized later in declaration
                // order (Kotlin runs property initializers/init blocks top-to-bottom,
                // and accessing an uninitialized lazy delegate throws an NPE).
                val ringtoneAudioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val incoming = NotificationChannel(
                    CHANNEL_INCOMING_CALL,
                    appContext.getString(R.string.channel_incoming_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = appContext.getString(R.string.channel_incoming_desc)
                    enableVibration(true)
                    // Ringtone semantics: default RINGTONE uri + ringtone usage, so
                    // the incoming alert follows the Ring volume, not Notification.
                    ringtoneUri?.let { uri ->
                        setSound(uri, ringtoneAudioAttributes)
                        Log.i(
                            TAG,
                            "incoming_ringtone_channel_created " +
                                "channel=$CHANNEL_INCOMING_CALL " +
                                "uri=${uri.scheme}:${uri.lastPathSegment} " +
                                "usage=USAGE_NOTIFICATION_RINGTONE",
                        )
                    } ?: Log.w(TAG, "incoming_ringtone_channel_created_no_default_ringtone")
                }

                val active = NotificationChannel(
                    CHANNEL_ACTIVE_CALL,
                    appContext.getString(R.string.channel_active_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = appContext.getString(R.string.channel_active_desc)
                    setShowBadge(false)
                    // Active/ongoing call must be silent — never replay a ringtone.
                    setSound(null, null)
                    enableVibration(false)
                }

                notificationManager.createNotificationChannel(signaling)
                notificationManager.createNotificationChannel(incoming)
                notificationManager.createNotificationChannel(active)

                Log.i(TAG, "incoming_call_channel_created channel=$CHANNEL_INCOMING_CALL")
                Log.i(TAG, "active_call_channel_created channel=$CHANNEL_ACTIVE_CALL")
                Log.i(TAG, "notification_channels_init_complete")
            }.onFailure { e ->
                // Channel setup must never crash app startup; log and continue.
                Log.e(TAG, "notification_channels_init_failed ${e.message}")
            }
        }
    }

    private fun deleteLegacyChannel(channelId: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.deleteNotificationChannel(channelId)
                Log.i(TAG, "legacy_channel_deleted channel=$channelId")
            }
        }.onFailure { e ->
            Log.w(TAG, "legacy_channel_delete_failed ${e.message}")
        }
    }

    fun persistentNotification(statusText: String): Notification {
        val contentIntent = activityIntent(flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return NotificationCompat.Builder(appContext, CHANNEL_SIGNALING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    // ---------------------------------------------------------------------
    // Call notification (single stable ID 1003)
    // ---------------------------------------------------------------------

    /**
     * Builds the incoming-call notification (CallStyle where supported) for a
     * ringing call. Must be published through the foreground service.
     */
    @SuppressLint("MissingPermission")
    fun buildIncomingCallNotification(
        callId: String,
        name: String,
        extension: String,
    ): Notification {
        val fullScreenIntent = activityIntent(flags = Intent.FLAG_ACTIVITY_NEW_TASK)
        val acceptIntent = serviceIntent(CallService.ACTION_ACCEPT)
        val rejectIntent = serviceIntent(CallService.ACTION_REJECT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(name)
                .setKey(extension)
                .build()
            val builder = Notification.Builder(appContext, CHANNEL_INCOMING_CALL)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(appContext.getString(R.string.notif_incoming_title))
                .setContentText(appContext.getString(R.string.notif_incoming_caption, extension))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenIntent, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            val style = Notification.CallStyle.forIncomingCall(caller, rejectIntent, acceptIntent)
                .setDeclineButtonColorHint(0xFFE05255.toInt())
                .setAnswerButtonColorHint(0xFF69B437.toInt())
            return builder.setStyle(style).build()
        }

        return NotificationCompat.Builder(appContext, CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(appContext.getString(R.string.notif_incoming_title))
            .setContentText(appContext.getString(R.string.notif_incoming_text, name, extension))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, appContext.getString(R.string.notif_action_reject), rejectIntent)
            .addAction(0, appContext.getString(R.string.notif_action_answer), acceptIntent)
            .build()
    }

    /**
     * Builds the ongoing/active-call notification. Uses Android's built-in
     * chronometer (setWhen + setUsesChronometer) so the elapsed time is drawn
     * by the system — no per-second NotificationManager updates are needed.
     */
    @SuppressLint("MissingPermission")
    fun buildActiveCallNotification(
        callId: String,
        name: String,
        extension: String,
        startedAt: Long,
    ): Notification {
        val hangupIntent = serviceIntent(CallService.ACTION_HANGUP)
        val text = appContext.getString(R.string.notif_active_text, name, extension)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(name)
                .setKey(extension)
                .build()
            val builder = Notification.Builder(appContext, CHANNEL_ACTIVE_CALL)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(name)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(startedAt)
                .setUsesChronometer(true)
                .setShowWhen(true)
            val style = Notification.CallStyle.forOngoingCall(caller, hangupIntent)
            return builder.setStyle(style).build()
        }

        return NotificationCompat.Builder(appContext, CHANNEL_ACTIVE_CALL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(0, appContext.getString(R.string.notif_action_hangup), hangupIntent)
            .build()
    }

    /** Safe cancellation of the call notification. Never crashes the call. */
    fun cancelCallNotification() {
        runCatching {
            notificationManager.cancel(NOTIFICATION_ID_CALL)
        }.onFailure { e ->
            Log.w(TAG, "call_notification_cancel_failed ${e.message}")
        }
    }

    // ---------------------------------------------------------------------
    // CallNotifier (incoming) — legacy bridge from CallController
    //
    // The foreground-service lifecycle is the real publisher now; these bridge
    // methods remain only to keep the CallController contract intact and are
    // no-ops here (CallService owns the CallStyle lifecycle via startForeground).
    // ---------------------------------------------------------------------

    override fun showIncoming(callId: String, name: String, extension: String) {
        Log.i(TAG, "call_notification_incoming_bridge_ignored call_id=$callId")
    }

    override fun hideIncoming(callId: String) {
        // The foreground service tears the notification down; nothing to do here.
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    fun hideAll() {
        notificationManager.cancelAll()
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun activityIntent(flags: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(flags)
        return PendingIntent.getActivity(
            appContext,
            REQUEST_ACTIVITY,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(appContext, CallService::class.java).setAction(action)
        return PendingIntent.getService(
            appContext,
            requestCodeFor(action),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(action: String): Int = when (action) {
        CallService.ACTION_ACCEPT -> REQUEST_ACCEPT
        CallService.ACTION_REJECT -> REQUEST_REJECT
        CallService.ACTION_HANGUP -> REQUEST_HANGUP
        else -> REQUEST_ACTIVITY
    }

    companion object {
        const val CHANNEL_SIGNALING = "signaling"

        /** Dedicated incoming-call channel with ringtone semantics (versioned). */
        const val CHANNEL_INCOMING_CALL = "tpcall_incoming_calls_v2"

        /** Silent channel for the ongoing active call. */
        const val CHANNEL_ACTIVE_CALL = "tpcall_active_calls"

        /** Old channel id that used the default notification sound; removed. */
        const val CHANNEL_CALLS_LEGACY = "calls"

        const val NOTIFICATION_ID_SIGNALING = 1002

        /** The single stable ID for the call notification (ringing + active). */
        const val NOTIFICATION_ID_CALL = 1003

        private const val REQUEST_ACTIVITY = 1
        private const val REQUEST_ACCEPT = 2
        private const val REQUEST_REJECT = 3
        private const val REQUEST_HANGUP = 4

        private const val TAG = "CallNotificationManager"
    }
}
