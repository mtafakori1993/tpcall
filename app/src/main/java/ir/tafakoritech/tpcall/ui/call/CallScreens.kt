package ir.tafakoritech.tpcall.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.call.CallState
import ir.tafakoritech.tpcall.ui.components.TpAvatar
import ir.tafakoritech.tpcall.ui.components.TpCallControl
import ir.tafakoritech.tpcall.ui.rememberMicrophonePermissionRequester
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import kotlinx.coroutines.delay

/** Shared layout wrapper for the call screens. */
@Composable
fun CallScreenScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = TpDimens.ScreenPadding)
            .padding(vertical = TpDimens.SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
fun IncomingScreen(controller: CallController, state: CallState.IncomingRinging) {
    val micRequester = rememberMicrophonePermissionRequester()
    IncomingCallContent(
        state = state,
        onAccept = { micRequester { controller.acceptCurrentCall() } },
        onReject = { controller.rejectCurrentCall() },
    )
}

@Composable
fun IncomingCallContent(
    state: CallState.IncomingRinging,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    CallScreenScaffold {
        Text(
            text = "تماس ورودی",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceLg))
        TpAvatar(name = state.name, size = TpControl.AvatarLarge, pulsing = true)
        Spacer(Modifier.height(TpDimens.SpaceLg))
        Text(
            text = state.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TpColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = state.extension,
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(TpDimens.SpaceXxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TpCallControl(
                icon = painterResource(R.drawable.ic_call_end),
                label = "رد تماس",
                destructive = true,
                size = 76.dp,
                iconSize = 30.dp,
                onClick = onReject,
            )
            TpCallControl(
                icon = painterResource(R.drawable.ic_call),
                label = "پاسخ",
                size = 76.dp,
                iconSize = 30.dp,
                onClick = onAccept,
            )
        }
    }
}

@Composable
fun OutgoingScreen(controller: CallController, state: CallState.OutgoingRinging) {
    OutgoingCallContent(
        state = state,
        onHangup = { controller.endCurrentCall() },
    )
}

@Composable
fun OutgoingCallContent(
    state: CallState.OutgoingRinging,
    onHangup: () -> Unit,
) {
    CallScreenScaffold {
        Text(
            text = "در حال تماس…",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceLg))
        TpAvatar(name = state.name ?: "", size = TpControl.AvatarLarge, pulsing = true)
        Spacer(Modifier.height(TpDimens.SpaceLg))
        Text(
            text = state.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            color = TpColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = state.extension,
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(TpDimens.SpaceXxl))

        TpCallControl(
            icon = painterResource(R.drawable.ic_call_end),
            label = "قطع تماس",
            destructive = true,
            size = 76.dp,
            iconSize = 30.dp,
            onClick = onHangup,
        )
    }
}

@Composable
fun ConnectingScreen(controller: CallController, state: CallState.Connecting) {
    ConnectingCallContent(
        state = state,
        onHangup = { controller.endCurrentCall() },
    )
}

@Composable
fun ConnectingCallContent(
    state: CallState.Connecting,
    onHangup: () -> Unit,
) {
    CallScreenScaffold {
        Text(
            text = "در حال برقراری تماس…",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceLg))
        TpAvatar(name = state.peerName ?: "", size = TpControl.AvatarLarge, pulsing = true)
        Spacer(Modifier.height(TpDimens.SpaceLg))
        Text(
            text = state.peerName ?: "",
            style = MaterialTheme.typography.headlineSmall,
            color = TpColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = state.peerExtension,
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(TpDimens.SpaceXxl))

        TpCallControl(
            icon = painterResource(R.drawable.ic_call_end),
            label = "قطع تماس",
            destructive = true,
            size = 76.dp,
            iconSize = 30.dp,
            onClick = onHangup,
        )
    }
}

@Composable
fun InCallScreen(controller: CallController, state: CallState.InCall) {
    val muted by controller.muted.collectAsStateWithLifecycle()
    val speakerOn by controller.speakerOn.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    InCallContent(
        state = state,
        muted = muted,
        speakerOn = speakerOn,
        onMute = { controller.toggleMute() },
        onSpeaker = { controller.toggleSpeaker() },
        onHangup = { controller.endCurrentCall() },
        elapsedSeconds = { (now - state.startedAt) / 1000 },
    )
}

@Composable
fun InCallContent(
    state: CallState.InCall,
    muted: Boolean,
    speakerOn: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHangup: () -> Unit,
    elapsedSeconds: () -> Long,
) {
    CallScreenScaffold {
        TpAvatar(name = state.peerName ?: "", size = TpControl.Avatar)
        Spacer(Modifier.height(TpDimens.SpaceMd))
        Text(
            text = state.peerName ?: "",
            style = MaterialTheme.typography.headlineSmall,
            color = TpColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = state.peerExtension,
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))
        Text(
            text = formatDuration(elapsedSeconds()),
            style = MaterialTheme.typography.displaySmall,
            color = TpColors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TpCallControl(
                icon = painterResource(if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic),
                label = if (muted) "میکروفون خاموش" else "میکروفون",
                selected = muted,
                onClick = onMute,
            )
            TpCallControl(
                icon = painterResource(R.drawable.ic_volume_up),
                label = if (speakerOn) "بلندگو روشن" else "بلندگو",
                selected = speakerOn,
                onClick = onSpeaker,
            )
        }

        Spacer(Modifier.height(TpDimens.SpaceXl))

        TpCallControl(
            icon = painterResource(R.drawable.ic_call_end),
            label = "قطع تماس",
            destructive = true,
            size = 76.dp,
            iconSize = 30.dp,
            onClick = onHangup,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
