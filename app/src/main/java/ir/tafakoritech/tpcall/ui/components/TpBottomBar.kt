package ir.tafakoritech.tpcall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens

/**
 * Compact, anchored Spectrum-inspired bottom navigation with exactly two
 * destinations: تماس (keypad) and تاریخچه (call history). It sits flush with
 * the bottom safe area (the background extends into the navigation-bar inset)
 * and is roughly half the height of a standard bar. The label always accompanies
 * the icon so the selected state is never conveyed by color alone.
 */
@Composable
fun TpBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    items: List<TpBottomNavItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TpColors.SurfacePrimary)
            .navigationBarsPadding()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            TpBottomNavItem(
                item = item,
                selected = index == selected,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

data class TpBottomNavItem(
    val icon: Painter,
    val label: String,
)

@Composable
private fun TpBottomNavItem(
    item: TpBottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) TpColors.AccentBright else TpColors.TextTertiary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
            )
            .padding(horizontal = TpDimens.SpaceSm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = item.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(TpDimens.SpaceXs))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
