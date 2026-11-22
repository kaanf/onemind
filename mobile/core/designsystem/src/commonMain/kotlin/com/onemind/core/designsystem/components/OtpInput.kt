package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * One-time code input: [length] boxes 56 high, radius 12; the box that receives the next digit has an
 * accent border. Tapping a filled box selects that digit, so the next one typed replaces it (and
 * backspace deletes it); tapping an empty box goes to the end. Only digits are accepted.
 * [contentDescription] labels the field (*Code*). Disabled (while a code is being checked) it keeps its
 * value and takes no input.
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val colors = OneMindTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var selection by remember { mutableStateOf(TextRange(value.length)) }
    // The value can change from outside (cleared after a wrong code): keep the selection inside it.
    val fieldValue = TextFieldValue(value, selection.coerceInOtp(value))
    BasicTextField(
        value = fieldValue,
        onValueChange = { input ->
            val digits = sanitizeOtp(input.text, length)
            selection = TextRange(input.selection.start.coerceIn(0, digits.length))
            onValueChange(digits)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
        keyboardActions =
            KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester).semantics { this.contentDescription = contentDescription },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { index ->
                    val focused = index == otpActiveBox(value, fieldValue.selection, length)
                    val shape = RoundedCornerShape(OneMindTheme.radius.control)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                // Not clickable: one field in the accessibility tree, the boxes are its drawing.
                                .pointerInput(enabled, value) {
                                    if (!enabled) return@pointerInput
                                    detectTapGestures {
                                        selection = otpSelectionForTap(index, value)
                                        focusRequester.requestFocus()
                                        keyboard?.show()
                                    }
                                }.background(colors.surface, shape)
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

/** A tap on box [index]: selects its digit (typing replaces it), or the end when the box is empty. */
internal fun otpSelectionForTap(
    index: Int,
    value: String,
): TextRange = if (index < value.length) TextRange(index, index + 1) else TextRange(value.length)

/** The box with the accent border: where the next digit goes. */
internal fun otpActiveBox(
    value: String,
    selection: TextRange,
    length: Int,
): Int = selection.start.coerceIn(0, value.length).coerceAtMost(length - 1)

internal fun TextRange.coerceInOtp(value: String): TextRange = TextRange(start.coerceIn(0, value.length), end.coerceIn(0, value.length))

/** Keeps digits only, at most [length]; pasted codes with spaces or dashes still work. */
internal fun sanitizeOtp(
    input: String,
    length: Int,
): String = input.filter { it in '0'..'9' }.take(length)
