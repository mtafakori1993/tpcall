package ir.tafakoritech.tpcall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.ui.theme.TpRadius

/**
 * Small status pill used for connection / presence. Always includes a text
 * label so state is never communicated by color alone.
 */
@Composable
fun TpStatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(TpRadius.Small))
            .background(TpColors.SurfacePrimary)
            .padding(horizontal = TpDimens.SpaceSm, vertical = TpDimens.SpaceXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(TpDimens.SpaceXxs))
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = TpColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
