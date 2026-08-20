package ir.tafakoritech.tpcall.ui.theme

import androidx.compose.ui.unit.dp

/** Consistent spacing scale. Prefer these tokens over ad-hoc values. */
object TpDimens {
    val SpaceXxs = 4.dp
    val SpaceXs = 8.dp
    val SpaceSm = 12.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp
    val SpaceXxl = 48.dp

    val ScreenPadding = 20.dp
    val SectionSpacing = 24.dp
    val ControlGap = 12.dp
}

/** Coherent corner-radius scale. */
object TpRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 18.dp
    val ExtraLarge = 24.dp
}

/** Reusable control dimensions. */
object TpControl {
    val TouchTarget = 48.dp
    val DialKey = 84.dp
    val DialKeyGap = 16.dp
    val CallControl = 64.dp

    /** Minimum width of a call control so Persian labels fit below the circle. */
    val CallControlLabelMinWidth = 88.dp
    val PrimaryButtonHeight = 60.dp
    val Avatar = 96.dp
    val AvatarLarge = 120.dp
}
