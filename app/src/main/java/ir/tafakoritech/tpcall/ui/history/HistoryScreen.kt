package ir.tafakoritech.tpcall.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.call.CallController
import ir.tafakoritech.tpcall.data.api.AuthGateway
import ir.tafakoritech.tpcall.data.model.CallHistoryItem
import ir.tafakoritech.tpcall.data.model.CallHistoryStatus
import ir.tafakoritech.tpcall.ui.components.TpTextButton
import ir.tafakoritech.tpcall.ui.rememberMicrophonePermissionRequester
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.ui.theme.TpRadius
import ir.tafakoritech.tpcall.util.CallHistoryFormatters
import java.time.Instant
import java.time.ZoneId

@Composable
fun HistoryScreen(
    controller: CallController,
    authApi: AuthGateway,
    token: String,
    onDialNumber: (String) -> Unit,
) {
    val viewModel: CallHistoryViewModel = viewModel(
        key = "history_$token",
        factory = CallHistoryViewModel.factory(controller, authApi, token),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val micRequester = rememberMicrophonePermissionRequester()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onScreenVisible()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    HistoryContent(
        state = state,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onCallAgain = { extension ->
            micRequester { controller.startCall(extension) }
        },
        onDialNumber = onDialNumber,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: CallHistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCallAgain: (String) -> Unit,
    onDialNumber: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary),
    ) {
        Text(
            text = "تاریخچه تماس‌ها",
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.TextPrimary,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = TpDimens.ScreenPadding)
                .padding(top = TpDimens.SpaceXxs, bottom = TpDimens.SpaceSm),
        )

        when {
            state.loading && state.calls.isEmpty() -> HistoryLoading()
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.calls.isEmpty() && state.error != null -> HistoryError(onRetry = onRefresh)
                    state.calls.isEmpty() -> HistoryEmpty()
                    else -> HistoryList(
                        calls = state.calls,
                        hasMore = state.hasMore,
                        refreshing = state.refreshing,
                        onLoadMore = onLoadMore,
                        onCallAgain = onCallAgain,
                        onDialNumber = onDialNumber,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    calls: List<CallHistoryItem>,
    hasMore: Boolean,
    refreshing: Boolean,
    onLoadMore: () -> Unit,
    onCallAgain: (String) -> Unit,
    onDialNumber: (String) -> Unit,
) {
    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TpDimens.ScreenPadding,
            vertical = TpDimens.SpaceXs,
        ),
        verticalArrangement = Arrangement.spacedBy(TpDimens.SpaceXs),
    ) {
        items(items = calls, key = { it.id }) { call ->
            HistoryRow(
                call = call,
                now = now,
                zone = zone,
                onCallAgain = { onCallAgain(CallHistoryPresentation.callAgainExtension(call)) },
                onRowTap = { onDialNumber(CallHistoryPresentation.callAgainExtension(call)) },
            )
        }
        if (hasMore) {
            item(key = "history_loading_more") {
                LoadingMoreFooter(refreshing = refreshing, onLoadMore = onLoadMore)
            }
        }
    }
}

@Composable
private fun LoadingMoreFooter(refreshing: Boolean, onLoadMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TpDimens.SpaceSm),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = TpColors.AccentBright,
        )
    }
    if (!refreshing) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onLoadMore() }
    }
}

@Composable
private fun HistoryRow(
    call: CallHistoryItem,
    now: Instant,
    zone: ZoneId,
    onCallAgain: () -> Unit,
    onRowTap: () -> Unit,
) {
    val directionIcon = CallHistoryPresentation.directionIconRes(call)
    val directionDescription = CallHistoryPresentation.directionDescription(call)
    val statusLabel = CallHistoryPresentation.statusLabel(call)
    val statusColor = if (call.status == CallHistoryStatus.COMPLETED) {
        TpColors.TextSecondary
    } else {
        TpColors.Destructive
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TpRadius.Medium))
            .background(TpColors.SurfacePrimary)
            .clickable(onClick = onRowTap)
            .padding(horizontal = TpDimens.SpaceMd, vertical = TpDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(directionIcon),
            contentDescription = directionDescription,
            tint = statusColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(TpDimens.SpaceSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.peerName,
                style = MaterialTheme.typography.titleMedium,
                color = TpColors.TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = call.peerExtension,
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusLabel +
                    " • " +
                    CallHistoryFormatters.formatCallDateTime(call.startedAt, now, zone),
                style = MaterialTheme.typography.bodySmall,
                color = TpColors.TextTertiary,
                maxLines = 1,
            )
        }

        call.durationSeconds?.let { seconds ->
            Text(
                text = CallHistoryFormatters.formatDuration(seconds),
                style = MaterialTheme.typography.labelMedium,
                color = TpColors.TextSecondary,
                modifier = Modifier.padding(horizontal = TpDimens.SpaceSm),
            )
        }

        // Small but accessible call-again action (48dp minimum touch target).
        IconButton(
            onClick = onCallAgain,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_call),
                contentDescription = "تماس مجدد با ${call.peerExtension}",
                tint = TpColors.AccentPrimary,
            )
        }
    }
}

@Composable
private fun HistoryEmpty() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = TpDimens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = TpColors.TextTertiary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(TpDimens.SpaceMd))
                Text(
                    text = "هنوز تماسی ندارید",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextSecondary,
                )
                Spacer(Modifier.height(TpDimens.SpaceXs))
                Text(
                    text = "تماس‌های ورودی و خروجی شما اینجا نمایش داده می‌شوند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TpColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HistoryError(onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = TpDimens.SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "دریافت تاریخچه تماس‌ها ناموفق بود",
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(TpDimens.SpaceSm))
                TpTextButton(text = "تلاش دوباره", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun HistoryLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TpDimens.ScreenPadding),
    ) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TpDimens.SpaceSm)
                    .height(64.dp)
                    .clip(RoundedCornerShape(TpRadius.Medium))
                    .background(TpColors.SurfacePrimary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = TpDimens.SpaceMd)
                        .size(20.dp)
                        .clip(RoundedCornerShape(TpRadius.Small))
                        .background(TpColors.DisabledSurface),
                )
                Column {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 14.dp)
                            .clip(RoundedCornerShape(TpRadius.Small))
                            .background(TpColors.DisabledSurface),
                    )
                    Spacer(Modifier.height(TpDimens.SpaceXs))
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 12.dp)
                            .clip(RoundedCornerShape(TpRadius.Small))
                            .background(TpColors.DisabledSurface),
                    )
                }
            }
        }
    }
}

