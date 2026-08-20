package ir.tafakoritech.tpcall.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl
import ir.tafakoritech.tpcall.ui.theme.TpDimens

/**
 * Reusable call control: icon in a circular surface with a short Persian label
 * rendered BELOW the circle. The circle contains ONLY the icon; CircleShape
 * clipping applies only to the icon surface, never to the whole component, so
 * labels such as "قطع تماس" stay fully visible underneath.
 *
 * State is never communicated by color alone — the icon and label change too.
 */
@Composable
fun TpCallControl(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    size: Dp = TpControl.CallControl,
    iconSize: Dp = 26.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val circleColor by animateColorAsState(
        targetValue = when {
            !enabled -> TpColors.DisabledSurface
            destructive -> if (pressed) TpColors.DestructivePressed else TpColors.DestructiveSurface
            selected -> if (pressed) TpColors.SurfacePressed else TpColors.AccentMuted
            pressed -> TpColors.SurfacePressed
            else -> TpColors.SurfacePrimary
        },
        animationSpec = tween(durationMillis = 120),
        label = "callControlColor",
    )
    val iconTint = when {
        !enabled -> TpColors.DisabledText
        destructive -> TpColors.Destructive
        selected -> TpColors.AccentBright
        else -> TpColors.TextPrimary
    }

    Column(
        modifier = modifier
            .widthIn(min = TpControl.CallControlLabelMinWidth)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                role = Role.Button,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.height(TpDimens.SpaceXs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) TpColors.TextSecondary else TpColors.DisabledText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(horizontal = TpDimens.SpaceXs),
        )
    }
}
