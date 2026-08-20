package ir.tafakoritech.tpcall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark aurora color scheme built from the TP semantic tokens.
 * The application is deliberately always dark — it is an internal corporate
 * phone client and the palette is designed around the dark deep-navy surface.
 */
private val TpDarkColorScheme = darkColorScheme(
    primary = TpColors.AccentPrimary,
    onPrimary = TpColors.TextOnAccent,
    primaryContainer = TpColors.AccentMuted,
    onPrimaryContainer = TpColors.TextPrimary,
    inversePrimary = TpColors.AccentBright,

    secondary = TpColors.AccentBright,
    onSecondary = TpColors.TextOnAccent,
    secondaryContainer = TpColors.SurfaceSecondary,
    onSecondaryContainer = TpColors.TextPrimary,

    tertiary = TpColors.AccentDeep,
    onTertiary = TpColors.TextPrimary,
    tertiaryContainer = TpColors.AccentDeep,
    onTertiaryContainer = TpColors.TextPrimary,

    background = TpColors.BackgroundPrimary,
    onBackground = TpColors.TextPrimary,
    surface = TpColors.BackgroundPrimary,
    onSurface = TpColors.TextPrimary,
    surfaceVariant = TpColors.SurfacePrimary,
    onSurfaceVariant = TpColors.TextSecondary,
    surfaceTint = TpColors.AccentPrimary,
    inverseSurface = TpColors.SurfacePrimary,
    inverseOnSurface = TpColors.TextPrimary,

    error = TpColors.Destructive,
    onError = TpColors.TextOnDestructive,
    errorContainer = TpColors.DestructiveSurface,
    onErrorContainer = Color(0xFFFFC9CA),

    outline = TpColors.BorderDefault,
    outlineVariant = TpColors.BorderStrong,
    scrim = TpColors.Scrim,
)

private val TpShapes = Shapes(
    extraSmall = RoundedCornerShape(TpRadius.Small),
    small = RoundedCornerShape(TpRadius.Small),
    medium = RoundedCornerShape(TpRadius.Medium),
    large = RoundedCornerShape(TpRadius.Large),
    extraLarge = RoundedCornerShape(TpRadius.ExtraLarge),
)

@Composable
fun TPCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TpDarkColorScheme,
        typography = Typography,
        shapes = TpShapes,
        content = content,
    )
}
