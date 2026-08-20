package ir.tafakoritech.tpcall.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpControl

/**
 * The on-screen numeric keypad.
 *
 * Digits follow conventional telephone ordering (1-9 then 0) and are rendered
 * left-to-right even inside the RTL Persian UI. No software keyboard is needed.
 *
 * State belongs to the caller: [value] drives the limit on appended digits and
 * the enabled state of backspace.
 */
@Composable
fun TpDialPad(
    value: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keySize: Dp = TpControl.DialKey,
    keyGap: Dp = TpControl.DialKeyGap,
) {
    val digitsEnabled = enabled && value.length < 3

    // Force LTR so digits follow conventional telephone ordering (1-2-3 … 0)
    // even though the surrounding UI is RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(keyGap),
        ) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                    row.forEach { digit ->
                        TpDialKey(
                            text = digit.toString(),
                            keySize = keySize,
                            onClick = { onDigit(digit) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                TpBackspaceKey(
                    enabled = enabled && value.isNotEmpty(),
                    keySize = keySize,
                    onBackspace = onBackspace,
                    onClear = onClear,
                )
                TpDialKey(
                    text = "0",
                    enabled = digitsEnabled,
                    keySize = keySize,
                    onClick = { onDigit('0') },
                )
                Spacer(modifier = Modifier.size(keySize))
            }
        }
    }
}
