package ir.tafakoritech.tpcall.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that requests RECORD_AUDIO if missing and then invokes
 * [onGranted]. Never called repeatedly while a request is pending.
 */
@Composable
fun rememberMicrophonePermissionRequester(): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pending?.invoke()
        pending = null
    }
    return remember {
        { onGranted: () -> Unit ->
            val granted = ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onGranted()
            } else {
                pending = onGranted
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/**
 * Requests POST_NOTIFICATIONS once if required (Android 13+). Failure to grant
 * does not break foreground calling; background incoming calls may not be
 * visible without it.
 */
@Composable
fun requestNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
