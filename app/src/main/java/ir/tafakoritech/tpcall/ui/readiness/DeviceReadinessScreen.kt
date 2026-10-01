package ir.tafakoritech.tpcall.ui.readiness

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.tafakoritech.tpcall.device.DeviceReadinessManager
import ir.tafakoritech.tpcall.device.ReadinessItemType
import ir.tafakoritech.tpcall.device.ReadinessStatus
import ir.tafakoritech.tpcall.ui.components.TpPrimaryButton
import ir.tafakoritech.tpcall.ui.components.TpSecondaryButton
import ir.tafakoritech.tpcall.ui.components.TpTextButton
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.ui.theme.TpRadius

/**
 * Device readiness / permissions setup assistant (آماده‌سازی تماس‌ها).
 *
 * Inspects the current device, shows only the requirements that apply to it,
 * and drives the user through the correct system/OEM settings pages. Every
 * return to this screen (onResume) triggers a re-check via
 * [DeviceReadinessManager.refresh], so no restart is ever needed.
 */
@Composable
fun DeviceReadinessScreen(
    manager: DeviceReadinessManager,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(manager) { mutableStateOf(manager.currentState()) }
    var acknowledged by remember(manager) { mutableStateOf(setOf<ReadinessItemType>()) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state = manager.refresh() }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state = manager.refresh() }

    // Mandatory re-check on resume: the user returns from a settings page and
    // the status must reflect the real system values without a restart.
    LifecycleResumeEffect(manager) {
        state = manager.refresh()
        onPauseOrDispose { }
    }

    BackHandler(enabled = true) { onBack?.invoke() ?: onDone() }

    val device = manager.deviceInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TpColors.BackgroundPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TpDimens.ScreenPadding)
            .padding(vertical = TpDimens.SpaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "آماده‌سازی تماس‌ها",
                style = MaterialTheme.typography.headlineSmall,
                color = TpColors.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (onBack != null) {
                TpTextButton(text = "بازگشت", onClick = onBack)
            }
        }
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = "${device.manufacturerLabel} · ${device.osLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.TextSecondary,
        )
        Spacer(Modifier.height(TpDimens.SpaceMd))
        Text(
            text = "${state.readyCount} از ${state.totalVisible} مورد آماده",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.AccentBright,
        )
        Spacer(Modifier.height(TpDimens.SpaceMd))

        state.visibleItems.forEach { type ->
            ReadinessRow(
                type = type,
                status = state.statusOf(type),
                acknowledged = type in acknowledged,
                onAction = {
                    when (type) {
                        ReadinessItemType.MICROPHONE -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        ReadinessItemType.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                state = manager.refresh()
                            }
                        }
                        else -> manager.openSetting(type)
                    }
                },
                onAcknowledge = { acknowledged = acknowledged + type },
            )
            Spacer(Modifier.height(TpDimens.SpaceSm))
        }

        if (state.batteryUnrestricted != ReadinessStatus.READY) {
            Spacer(Modifier.height(TpDimens.SpaceSm))
            Text(
                text = "محدودیت باتری ممکن است باعث از دست رفتن تماس‌های داخلی در پس‌زمینه شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(TpDimens.SpaceLg))
        TpSecondaryButton(
            text = "تنظیمات برنامه",
            onClick = { manager.openAppDetailsSettings() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))
        TpPrimaryButton(
            text = "ادامه",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadinessRow(
    type: ReadinessItemType,
    status: ReadinessStatus,
    acknowledged: Boolean,
    onAction: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val (title, description) = readinessText(type)
    val done = acknowledged || status == ReadinessStatus.READY
    val accent = when {
        done -> TpColors.Online
        status == ReadinessStatus.MISSING -> TpColors.Warning
        else -> TpColors.TextTertiary
    }
    val glyph = when {
        done -> "✓"
        status == ReadinessStatus.MISSING -> "!"
        else -> "؟"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TpColors.SurfacePrimary, RoundedCornerShape(TpRadius.Medium))
            .padding(TpDimens.SpaceMd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, style = MaterialTheme.typography.titleMedium, color = accent)
            Spacer(Modifier.width(TpDimens.SpaceSm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TpColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(TpDimens.SpaceXxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.TextSecondary,
        )
        Spacer(Modifier.height(TpDimens.SpaceSm))
        when {
            done -> Text(
                text = if (acknowledged) "انجام شد" else "آماده",
                style = MaterialTheme.typography.labelLarge,
                color = TpColors.Online,
            )
            status == ReadinessStatus.UNKNOWN -> Row {
                TpSecondaryButton(
                    text = "تنظیم",
                    onClick = onAction,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(TpDimens.SpaceSm))
                TpTextButton(text = "انجام شد", onClick = onAcknowledge)
            }
            else -> TpSecondaryButton(
                text = actionLabel(type),
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun readinessText(type: ReadinessItemType): Pair<String, String> = when (type) {
    ReadinessItemType.MICROPHONE -> "میکروفون" to "برای برقراری و دریافت تماس‌های صوتی لازم است."
    ReadinessItemType.NOTIFICATIONS -> "اعلان تماس‌ها" to "برای نمایش اعلان تماس ورودی در پس‌زمینه لازم است."
    ReadinessItemType.FULL_SCREEN_CALLS -> "تماس تمام‌صفحه" to "برای نمایش تماس ورودی روی کل صفحه لازم است."
    ReadinessItemType.BATTERY_UNRESTRICTED -> "محدودیت باتری" to "ممکن است تماس‌های داخلی در پس‌زمینه دریافت نشوند."
    ReadinessItemType.LOCK_SCREEN_DISPLAY -> "نمایش تماس روی صفحه قفل" to "برای نمایش تماس داخلی زمانی که گوشی قفل است."
    ReadinessItemType.BACKGROUND_POPUP -> "نمایش پنجره در پس‌زمینه" to "برای نمایش صفحه تماس وقتی TP Call در پس‌زمینه است."
    ReadinessItemType.AUTO_START -> "اجرای خودکار" to "اجازه می‌دهد TP Call بعد از مدیریت سیستم در پس‌زمینه فعال بماند."
}

private fun actionLabel(type: ReadinessItemType): String = when (type) {
    ReadinessItemType.MICROPHONE,
    ReadinessItemType.NOTIFICATIONS,
    -> "درخواست مجوز"

    ReadinessItemType.FULL_SCREEN_CALLS -> "فعال‌سازی تماس تمام‌صفحه"
    ReadinessItemType.BATTERY_UNRESTRICTED -> "بدون محدودیت کردن"
    ReadinessItemType.LOCK_SCREEN_DISPLAY,
    ReadinessItemType.BACKGROUND_POPUP,
    ReadinessItemType.AUTO_START,
    -> "تنظیم"
}
