package ir.tafakoritech.tpcall.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ir.tafakoritech.tpcall.ui.theme.TpColors
import ir.tafakoritech.tpcall.ui.theme.TpRadius

/**
 * Spectrum-style structured text field: persistent label, clear focus state,
 * inline supporting/error text, and consistent TP colors.
 */
@Composable
fun TpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        shape = RoundedCornerShape(TpRadius.Medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TpColors.BackgroundSecondary,
            unfocusedContainerColor = TpColors.BackgroundSecondary,
            disabledContainerColor = TpColors.DisabledSurface,
            focusedTextColor = TpColors.TextPrimary,
            unfocusedTextColor = TpColors.TextPrimary,
            disabledTextColor = TpColors.DisabledText,
            cursorColor = TpColors.AccentBright,
            focusedBorderColor = TpColors.BorderFocused,
            unfocusedBorderColor = TpColors.BorderDefault,
            disabledBorderColor = TpColors.BorderDefault,
            focusedLabelColor = TpColors.AccentBright,
            unfocusedLabelColor = TpColors.TextSecondary,
            focusedSupportingTextColor = TpColors.TextSecondary,
            unfocusedSupportingTextColor = TpColors.TextSecondary,
            errorBorderColor = TpColors.Destructive,
            errorLabelColor = TpColors.Destructive,
            errorSupportingTextColor = TpColors.Destructive,
            errorContainerColor = TpColors.DestructiveSurface,
        ),
        modifier = modifier,
    )
}

/** Convenience wrapper for a masked password field with a show/hide toggle. */
@Composable
fun TpPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    TpTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            androidx.compose.material3.IconButton(onClick = onToggleVisibility) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (visible) ir.tafakoritech.tpcall.R.drawable.ic_visibility_off
                        else ir.tafakoritech.tpcall.R.drawable.ic_visibility,
                    ),
                    contentDescription = if (visible) "پنهان کردن رمز" else "نمایش رمز",
                    tint = TpColors.TextSecondary,
                )
            }
        },
    )
}
