package ir.tafakoritech.tpcall.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import ir.tafakoritech.tpcall.call.SessionState
import ir.tafakoritech.tpcall.data.signaling.SignalingConnectionState
import ir.tafakoritech.tpcall.ui.components.TpDialPad
import ir.tafakoritech.tpcall.ui.components.TpPrimaryButton
import ir.tafakoritech.tpcall.ui.components.TpStatusPill
import ir.tafakoritech.tpcall.ui.rememberMicrophonePermissionRequester
import ir.tafakoritech.tpcall.ui.requestNotificationPermissionOnce
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl
import ir.tafakoritech.tpcall.ui.theme.TpDimens

@Composable
fun HomeScreen(
    controller: CallController,
    dialerViewModel: DialerViewModel,
    onLogout: () -> Unit,
) {
    val state by dialerViewModel.uiState.collectAsStateWithLifecycle()
    val session by controller.sessionState.collectAsStateWithLifecycle()
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val user = (session as? SessionState.Authenticated)?.user

    requestNotificationPermissionOnce()

    val micRequester = rememberMicrophonePermissionRequester()

    HomeContent(
        state = state,
        myName = user?.name ?: "",
        myExtension = user?.extension ?: "",
        connectionState = connectionState,
        onDigit = dialerViewModel::onDigitPressed,
        onBackspace = dialerViewModel::onBackspacePressed,
        onClear = dialerViewModel::onClearPressed,
        onCallPressed = { micRequester { dialerViewModel.onCallPressed() } },
        onLogout = onLogout,
    )
}

@Composable
private fun HomeContent(
    state: DialerViewModel.DialerUiState,
    myName: String,
    myExtension: String,
    connectionState: SignalingConnectionState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCallPressed: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val signalingConnected = connectionState == SignalingConnectionState.Connected

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary),
    ) {
        val spaceTop = TpDimens.SpaceXxs
        val spaceMid = TpDimens.SpaceXs
        val lookupHeight = 32.dp
        val buttonGap = TpDimens.SpaceSm
        val keyGap = 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = TpDimens.ScreenPadding)
                .padding(top = TpDimens.SpaceXxs, bottom = TpDimens.SpaceSm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Top bar -------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TP Call",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                TpStatusPill(
                    text = when (connectionState) {
                        SignalingConnectionState.Connected -> "متصل"
                        SignalingConnectionState.Connecting -> "در حال اتصال"
                        is SignalingConnectionState.Reconnecting -> "در حال بازیابی ارتباط"
                        SignalingConnectionState.Disconnected -> "قطع ارتباط"
                    },
                    dotColor = when (connectionState) {
                        SignalingConnectionState.Connected -> TpColors.Online
                        SignalingConnectionState.Connecting,
                        is SignalingConnectionState.Reconnecting,
                        -> TpColors.AccentBright

                        SignalingConnectionState.Disconnected -> TpColors.Destructive
                    },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "گزینه‌ها",
                            tint = TpColors.TextSecondary,
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("خروج") },
                            onClick = {
                                menuExpanded = false
                                onLogout()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("درباره برنامه") },
                            onClick = { menuExpanded = false },
                        )
                    }
                }
            }

            // ---- Identity ------------------------------------------------------
            Spacer(Modifier.height(spaceTop))
            Text(
                text = myName,
                style = MaterialTheme.typography.headlineSmall,
                color = TpColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TpDimens.SpaceXxs))
            Text(
                text = "داخلی $myExtension",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            // ---- Dialed extension display --------------------------------------
            Spacer(Modifier.height(spaceMid))
            // Fixed-height slot so the keypad below never jumps while typing.
            Box(
                modifier = Modifier.height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.extensionInput.isEmpty()) {
                    Text(
                        text = "شماره داخلی را وارد کنید",
                        style = MaterialTheme.typography.titleMedium,
                        color = TpColors.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = state.extensionInput,
                        style = MaterialTheme.typography.displayMedium.copy(textDirection = TextDirection.Ltr),
                        color = TpColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ---- Resolved user / lookup -----------------------------------------
            Spacer(Modifier.height(spaceMid))
            Box(
                modifier = Modifier.height(lookupHeight),
                contentAlignment = Alignment.Center,
            ) {
                ResolvedUserInfo(state)
            }

            // ---- Keypad ----------------------------------------------------------
            // The keypad flexes to fill all remaining height and width. Keys are
            // sized from both dimensions (never overflow vertically, fill most of
            // the screen width) so the grid stays large, symmetric and centered.
            Spacer(Modifier.height(spaceMid))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val heightBased = (maxHeight.value - 3 * keyGap.value) / 4f
                val widthBased = (maxWidth.value - 2 * keyGap.value) / 3f
                val keySize = minOf(heightBased, widthBased).coerceIn(
                    TpControl.TouchTarget.value,
                    TpControl.DialKey.value,
                ).dp
                TpDialPad(
                    value = state.extensionInput,
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    enabled = signalingConnected,
                    keySize = keySize,
                    keyGap = keyGap,
                )
            }

            // Guaranteed gap between the keypad and the call button.
            Spacer(Modifier.height(buttonGap))

            // ---- Call action ------------------------------------------------------
            if (state.isSelfCall) {
                Text(
                    text = "این داخلی متعلق به شماست",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TpColors.Destructive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = TpDimens.SpaceSm),
                )
            } else if (!signalingConnected && state.extensionInput.isNotEmpty()) {
                Text(
                    text = "ارتباط با سرور برقرار نیست",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TpColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = TpDimens.SpaceSm),
                )
            }
            TpPrimaryButton(
                text = "تماس",
                icon = painterResource(R.drawable.ic_call),
                onClick = onCallPressed,
                enabled = dialerCallEnabled(state, signalingConnected),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun dialerCallEnabled(state: DialerViewModel.DialerUiState, signalingConnected: Boolean): Boolean {
    return signalingConnected &&
        state.extensionInput.length == 3 &&
        state.extensionInput.all { it in '0'..'9' } &&
        !state.isSelfCall
}

@Composable
private fun ResolvedUserInfo(state: DialerViewModel.DialerUiState) {
    when (state.lookup) {
        LookupState.IDLE -> Unit
        LookupState.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = TpColors.AccentBright,
            )
            Spacer(Modifier.size(TpDimens.SpaceXs))
            Text(
                text = "در حال بررسی…",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextSecondary,
            )
        }
        LookupState.NOT_FOUND -> Text(
            text = "این داخلی وجود ندارد",
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.Destructive,
        )
        LookupState.FOUND -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.resolvedName.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = TpColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TpDimens.SpaceXxs))
            when (state.resolvedOnline) {
                true -> TpStatusPill(text = "آنلاین", dotColor = TpColors.Online)
                false -> TpStatusPill(text = "آفلاین", dotColor = TpColors.Offline)
                null -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "Home / empty dialer", showBackground = true, backgroundColor = 0xFF021C25, widthDp = 360, heightDp = 720)
@Composable
private fun HomeEmptyPreview() {
    ir.tafakoritech.tpcall.ui.theme.TPCallTheme {
        HomeContent(
            state = DialerViewModel.DialerUiState(signalingConnected = true),
            myName = "محمد تفکری",
            myExtension = "101",
            connectionState = SignalingConnectionState.Connected,
            onDigit = {},
            onBackspace = {},
            onClear = {},
            onCallPressed = {},
            onLogout = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Home / with compact bottom bar",
    showBackground = true,
    backgroundColor = 0xFF021C25,
    widthDp = 360,
    heightDp = 720,
)
@Composable
private fun HomeWithBottomBarPreview() {
    ir.tafakoritech.tpcall.ui.theme.TPCallTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                HomeContent(
                    state = DialerViewModel.DialerUiState(signalingConnected = true),
                    myName = "محمد تفکری",
                    myExtension = "101",
                    connectionState = SignalingConnectionState.Connected,
                    onDigit = {},
                    onBackspace = {},
                    onClear = {},
                    onCallPressed = {},
                    onLogout = {},
                )
            }
            ir.tafakoritech.tpcall.ui.components.TpBottomBar(
                selected = 0,
                onSelect = {},
                items = listOf(
                    ir.tafakoritech.tpcall.ui.components.TpBottomNavItem(
                        icon = painterResource(R.drawable.ic_call),
                        label = "تماس",
                    ),
                    ir.tafakoritech.tpcall.ui.components.TpBottomNavItem(
                        icon = painterResource(R.drawable.ic_person),
                        label = "مخاطبین",
                    ),
                    ir.tafakoritech.tpcall.ui.components.TpBottomNavItem(
                        icon = painterResource(R.drawable.ic_history),
                        label = "تاریخچه",
                    ),
                ),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home / two digits", showBackground = true, backgroundColor = 0xFF021C25, widthDp = 360, heightDp = 720)
@Composable
private fun HomeTwoDigitsPreview() {
    ir.tafakoritech.tpcall.ui.theme.TPCallTheme {
        HomeContent(
            state = DialerViewModel.DialerUiState(extensionInput = "20", signalingConnected = true),
            myName = "محمد تفکری",
            myExtension = "101",
            connectionState = SignalingConnectionState.Connected,
            onDigit = {},
            onBackspace = {},
            onClear = {},
            onCallPressed = {},
            onLogout = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home / resolved extension", showBackground = true, backgroundColor = 0xFF021C25, widthDp = 360, heightDp = 720)
@Composable
private fun HomeResolvedPreview() {
    ir.tafakoritech.tpcall.ui.theme.TPCallTheme {
        HomeContent(
            state = DialerViewModel.DialerUiState(
                extensionInput = "203",
                lookup = LookupState.FOUND,
                resolvedName = "حسابداری",
                resolvedOnline = true,
                signalingConnected = true,
            ),
            myName = "محمد تفکری",
            myExtension = "101",
            connectionState = SignalingConnectionState.Connected,
            onDigit = {},
            onBackspace = {},
            onClear = {},
            onCallPressed = {},
            onLogout = {},
        )
    }
}
