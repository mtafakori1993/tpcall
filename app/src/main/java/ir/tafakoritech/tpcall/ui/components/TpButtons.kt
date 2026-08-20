package ir.tafakoritech.tpcall.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpControl
import ir.tafakoritech.tpcall.ui.theme.TpDimens
import ir.tafakoritech.tpcall.ui.theme.TpRadius

/** Highest-emphasis action button (e.g. تماس, ورود). */
@Composable
fun TpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: Painter? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(TpControl.PrimaryButtonHeight),
        shape = RoundedCornerShape(TpRadius.Large),
        colors = ButtonDefaults.buttonColors(
            containerColor = TpColors.AccentPrimary,
            contentColor = TpColors.TextOnAccent,
            disabledContainerColor = TpColors.DisabledSurface,
            disabledContentColor = TpColors.DisabledText,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = TpColors.TextOnAccent,
                strokeWidth = 2.5.dp,
            )
        } else {
            if (icon != null) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(TpDimens.SpaceXs))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Secondary action button: dark surface + border. */
@Composable
fun TpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(TpControl.PrimaryButtonHeight),
        shape = RoundedCornerShape(TpRadius.Large),
        border = BorderStroke(1.dp, TpColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TpColors.SurfacePrimary,
            contentColor = TpColors.TextPrimary,
            disabledContainerColor = TpColors.DisabledSurface,
            disabledContentColor = TpColors.DisabledText,
        ),
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(TpDimens.SpaceXs))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Destructive action button (reject/hangup). */
@Composable
fun TpDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(TpControl.PrimaryButtonHeight),
        shape = RoundedCornerShape(TpRadius.Large),
        colors = ButtonDefaults.buttonColors(
            containerColor = TpColors.Destructive,
            contentColor = TpColors.TextOnDestructive,
            disabledContainerColor = TpColors.DisabledSurface,
            disabledContentColor = TpColors.DisabledText,
        ),
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(TpDimens.SpaceXs))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Low-emphasis text action. */
@Composable
fun TpTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = TpColors.AccentBright,
            disabledContentColor = TpColors.DisabledText,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
