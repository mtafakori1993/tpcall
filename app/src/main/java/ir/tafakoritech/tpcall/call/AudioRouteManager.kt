package ir.tafakoritech.tpcall.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * The single layer responsible for call audio: routing (earpiece vs speaker),
 * audio mode, audio focus, and the incoming-call ringtone/vibration.
 *
 * Telecom cooperation: when a Core-Telecom call manages the active call
 * ([setTelecomManaged] true), this layer must NOT fight Telecom over
 * AudioManager.mode / audio focus / speakerphone. Telecom becomes the primary
 * system-level audio-route authority and the app requests endpoints through
 * Core-Telecom instead. This layer then only owns the pre-answer ringtone and
 * the fallback path used when no Telecom session exists for the call.
 */
class AudioRouteManager(context: Context) : CallAudio {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var ringtonePlayer: MediaPlayer? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var callAudioActive = false
    private var hasFocus = false
    private var telecomManaged = false

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
    }

    override fun startRinging() {
        startRingtone()
        startVibration()
    }

    override fun stopRinging() {
        stopRingtone()
        stopVibration()
    }
    /**
     * Telecommunication state set by the controller: true when the current call
     * is registered with Core-Telecom, which then owns the audio route/focus.
     */
    override fun setTelecomManaged(managed: Boolean) {
        if (telecomManaged == managed) return
        telecomManaged = managed
        Log.i(TAG, "telecom_managed_changed managed=$managed")
    }

    override fun startCallAudio() {
        if (callAudioActive) return
        callAudioActive = true
        stopRingtone()
        stopVibration()
        if (telecomManaged) {
            // Telecom manages mode/focus/routing for the call. Do not fight it.
            Log.i(TAG, "call_audio_started_telecom_managed")
            return
        }
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        requestAudioFocus()
        Log.i(TAG, "call_audio_started")
    }

    override fun stopCallAudio() {
        if (!callAudioActive) return
        callAudioActive = false
        if (telecomManaged) {
            // Telecom restores the audio mode/focus when the call ends.
            Log.i(TAG, "call_audio_stopped_telecom_managed")
            return
        }
        abandonAudioFocus()
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = previousMode
        Log.i(TAG, "call_audio_stopped")
    }

    override fun setSpeakerOn(on: Boolean) {
        if (telecomManaged) {
            // Telecom controls the endpoint via requestEndpointChange; nothing
            // to do here (fallback only for non-Telecom calls).
            Log.i(TAG, "speaker_toggled_skipped_telecom_managed on=$on")
            return
        }
        if (!audioManager.isSpeakerphoneOn && audioManager.isWiredHeadsetOn) {
            // Headset present: keep routing to headset, nothing to change.
            return
        }
        audioManager.isSpeakerphoneOn = on
        Log.i(TAG, "speaker_toggled on=$on")
    }

    override fun release() {
        stopRinging()
        stopCallAudio()
    }

    private fun startRingtone() {
        if (ringtonePlayer != null) {
            // Already ringing; never start a second loop (duplicate audio).
            return
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(appContext, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            ringtonePlayer = player
            Log.i(TAG, "incoming_ringtone_fallback_started uri=${uri.scheme}:${uri.lastPathSegment}")
        } catch (e: Exception) {
            Log.w(TAG, "incoming_ringtone_fallback_failed ${e.message}")
            stopRingtone()
        }
    }

    private fun stopRingtone() {
        val player = ringtonePlayer
        if (player == null) return
        ringtonePlayer = null
        try {
            player.stop()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
        Log.i(TAG, "incoming_ringtone_fallback_stopped")
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0),
            )
        } catch (e: Exception) {
            Log.w(TAG, "vibrate_failed ${e.message}")
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun requestAudioFocus() {
        val result = audioManager.requestAudioFocus(focusRequest)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private companion object {
        const val TAG = "AudioRouteManager"
    }
}
