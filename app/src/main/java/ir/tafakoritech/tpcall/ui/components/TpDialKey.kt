package ir.tafakoritech.tpcall.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl

/**
 * One keypad key. Renders either a digit or an icon (backspace). Provides its
 * own pressed state (color + subtle scale), long-press support, and TalkBack
 * semantics. State-free — all state lives in the dialer screen/view model.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TpDialKey(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: Painter? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
    keySize: Dp = TpControl.DialKey,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val surfaceColor by animateColorAsState(
        targetValue = when {
            !enabled -> TpColors.DisabledSurface
            pressed -> TpColors.SurfacePressed
            else -> TpColors.SurfacePrimary
        },
        animationSpec = tween(durationMillis = 120),
        label = "dialKeyColor",
    )
    val contentColor = if (enabled) TpColors.TextPrimary else TpColors.DisabledText
    val scale = if (pressed) 0.96f else 1f

    Box(
        modifier = modifier
            .size(keySize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(surfaceColor)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        } else if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** The backspace key used by [TpDialPad]: tap deletes one digit, long-press clears all. */
@Composable
fun TpBackspaceKey(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keySize: Dp = TpControl.DialKey,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    TpDialKey(
        modifier = modifier,
        icon = painterResource(R.drawable.ic_backspace),
        contentDescription = "پاک کردن رقم، نگه‌داشتن برای پاک کردن همه",
        enabled = enabled,
        keySize = keySize,
        onClick = onBackspace,
        onLongClick = onClear,
    )
}
