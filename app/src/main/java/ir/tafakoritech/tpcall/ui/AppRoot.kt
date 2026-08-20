package ir.tafakoritech.tpcall.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.call.SessionState
import ir.tafakoritech.tpcall.call.isActive
import ir.tafakoritech.tpcall.di.AppContainer
import ir.tafakoritech.tpcall.ui.auth.AuthScreen
import ir.tafakoritech.tpcall.ui.call.ConnectingScreen
import ir.tafakoritech.tpcall.ui.call.InCallScreen
import ir.tafakoritech.tpcall.ui.call.IncomingScreen
import ir.tafakoritech.tpcall.ui.call.OutgoingScreen

/**
 * Top-level screen dispatcher. Call state (not manual navigation calls) drives
 * which screen is shown.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val controller = container.callController
    val sessionState by controller.sessionState.collectAsStateWithLifecycle()
    val callState by controller.callState.collectAsStateWithLifecycle()
    val flash by controller.flash.collectAsStateWithLifecycle()
    val serviceRunning by container.serviceCoordinator.running.collectAsStateWithLifecycle()

    // Keep the foreground signaling service alive exactly while authenticated.
    LaunchedEffect(sessionState, serviceRunning) {
        when {
            sessionState is SessionState.Authenticated && !serviceRunning ->
                container.serviceCoordinator.startService()
            sessionState is SessionState.LoggedOut && serviceRunning ->
                container.serviceCoordinator.stopService()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(flash) {
        flash?.let { snackbarHostState.showSnackbar(it.text) }
    }

    val context = LocalContext.current
    BackHandler(enabled = callState.isActive) {
        when (callState) {
            is CallState.IncomingRinging -> controller.rejectCurrentCall()
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // Each screen (auth, call, dialer, history, bottom bar) manages its own
    // system-bar insets, so the outer scaffold must not consume them again —
    // otherwise every screen would be double-padded away from the safe areas.
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (sessionState) {
                SessionState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                SessionState.LoggedOut -> AuthScreen(controller = controller)

                is SessionState.Authenticated -> {
                    when (val state = callState) {
                        CallState.Idle -> MainScaffold(
                            container = container,
                            onLogout = { controller.logout() },
                        )

                        is CallState.IncomingRinging -> IncomingScreen(controller, state)
                        is CallState.OutgoingRinging -> OutgoingScreen(controller, state)
                        is CallState.Connecting -> ConnectingScreen(controller, state)
                        is CallState.InCall -> InCallScreen(controller, state)
                    }
                }
            }
        }
    }
}
