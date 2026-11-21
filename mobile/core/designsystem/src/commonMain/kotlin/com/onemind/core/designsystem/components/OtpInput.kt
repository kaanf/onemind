package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * One-time code input: [length] boxes 56 high, radius 12; the box that receives the next digit has an
 * accent border. Only digits are accepted. [contentDescription] labels the field (*Code*).
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    onDone: (() -> Unit)? = null,
) {
    val colors = OneMindTheme.colors
    BasicTextField(
        value = value,
        onValueChange = { input -> onValueChange(sanitizeOtp(input, length)) },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
        keyboardActions =
            KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { index ->
                    val focused = index == value.length.coerceAtMost(length - 1)
                    val shape = RoundedCornerShape(OneMindTheme.radius.control)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(colors.surface, shape)
                                .border(
                                    if (focused) OneMindTheme.spacing.focusBorderWidth else OneMindTheme.spacing.borderWidth,
                                    if (focused) colors.accent else colors.borderStrong,
                                    shape,
                                ),
                    ) {
                        Text(
                            text = value.getOrNull(index)?.toString().orEmpty(),
                            style = OneMindTheme.typography.figure,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        },
    )
}

/** Keeps digits only, at most [length]; pasted codes with spaces or dashes still work. */
internal fun sanitizeOtp(
    input: String,
    length: Int,
): String = input.filter { it in '0'..'9' }.take(length)
