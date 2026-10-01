package ir.tafakoritech.tpcall.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * TP / Tafakor Pardazan brand palette. These are the permanent project colors
 * and must not be replaced with unrelated Material default hues.
 */
object TpBrand {
    val DeepNavy = Color(0xFF021C25)
    val Navy = Color(0xFF091E27)
    val DarkTeal = Color(0xFF0E3338)
    val Teal = Color(0xFF053A3D)
    val ForestTeal = Color(0xFF165A54)
    val AuroraLightGreen = Color(0xFF91DA73)
    val AuroraGreen = Color(0xFF69B437)
    val ForestGreen = Color(0xFF23643D)
    val DeepGreen = Color(0xFF022C22)
    val DarkForest = Color(0xFF0D2D23)
}

/**
 * Semantic design tokens used throughout the UI. Semantic roles (not raw hex)
 * are the only colors referenced by screens/components.
 */
object TpColors {
    // Backgrounds (dark, calm, aurora)
    val BackgroundPrimary = TpBrand.DeepNavy
    val BackgroundSecondary = TpBrand.Navy
    val SurfacePrimary = TpBrand.DarkTeal
    val SurfaceSecondary = TpBrand.Teal
    val SurfaceSelected = TpBrand.ForestGreen
    val SurfacePressed = TpBrand.ForestTeal

    // Accent
    val AccentPrimary = TpBrand.AuroraGreen
    val AccentBright = TpBrand.AuroraLightGreen
    val AccentMuted = TpBrand.ForestGreen
    val AccentDeep = TpBrand.ForestTeal

    // Text
    val TextPrimary = Color(0xFFE9F2EF)
    val TextSecondary = Color(0xFFA9C2BD)
    val TextTertiary = Color(0xFF73908B)
    val TextOnAccent = TpBrand.DeepNavy
    val TextOnDestructive = Color(0xFFFFF6F6)

    // Borders
    val BorderDefault = Color(0xFF1D4B50)
    val BorderStrong = Color(0xFF2A6167)
    val BorderFocused = TpBrand.AuroraLightGreen

    // Destructive (restrained semantic red; the TP palette has no destructive hue)
    val Destructive = Color(0xFFE05255)
    val DestructivePressed = Color(0xFFC34246)
    val DestructiveSurface = Color(0xFF3D1C1F)

    // Attention/warning (restrained warm amber, compatible with the aurora palette)
    val Warning = Color(0xFFE0A458)

    // Disabled
    val DisabledSurface = Color(0xFF0A2B2F)
    val DisabledText = Color(0xFF48656B)

    // Status
    val Online = TpBrand.AuroraGreen
    val Offline = TextTertiary
    val Scrim = Color(0xCC021C25)
}
