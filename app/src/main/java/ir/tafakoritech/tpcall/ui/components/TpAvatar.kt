package ir.tafakoritech.tpcall.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl

/**
 * Caller avatar: a deep-teal circle with the person icon (or initials) in a
 * light tone. Optional restrained breathing ring used while the phone rings.
 */
@Composable
fun TpAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = TpControl.Avatar,
    pulsing: Boolean = false,
) {
    val initials = rememberInitials(name)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (pulsing) {
            PulseRing(size = size)
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(TpColors.AccentDeep),
            contentAlignment = Alignment.Center,
        ) {
            if (initials.isNullOrBlank()) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = TpColors.TextPrimary,
                    modifier = Modifier.size(size * 0.45f),
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TpColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun rememberInitials(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        else -> words[0].first().toString()
    }
}

@Composable
private fun PulseRing(size: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseProgress",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = progress
                scaleY = progress
                alpha = (1f - progress) * 0.55f
            }
            .clip(CircleShape)
            .background(TpColors.AccentDeep),
    )
}
