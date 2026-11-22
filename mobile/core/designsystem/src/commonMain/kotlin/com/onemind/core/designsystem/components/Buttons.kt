package com.onemind.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

enum class PrimaryButtonStyle {
    /** Main call to action. */
    Accent,

    /** Final approve actions and *Continue with Apple*. */
    Dark,
}

/** Primary button: full width, 52 (CTA) or 48 high, radius 12. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PrimaryButtonStyle = PrimaryButtonStyle.Accent,
    height: Dp = 52.dp,
) {
    val colors = OneMindTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = height),
        shape = RoundedCornerShape(OneMindTheme.radius.control),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (style == PrimaryButtonStyle.Accent) colors.accent else colors.textPrimary,
                contentColor = colors.onAccent,
                disabledContainerColor = colors.borderStrong,
                disabledContentColor = colors.textSecondary,
            ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Text(text = text, style = OneMindTheme.typography.button)
    }
}

/** Outline button: white, `borderStrong` border, radius 12. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val colors = OneMindTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(OneMindTheme.radius.control),
        border = BorderStroke(OneMindTheme.spacing.borderWidth, colors.borderStrong),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                disabledContentColor = colors.textSecondary,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text = text, style = OneMindTheme.typography.button)
    }
}
