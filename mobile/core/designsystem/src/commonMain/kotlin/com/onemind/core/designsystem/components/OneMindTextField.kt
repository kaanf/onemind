package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * Single-line text input: label above, field 52 high, radius 12, white, `borderStrong` border that
 * turns into a 1.5 accent border while focused; placeholder in `textPlaceholder` (decorative only,
 * the label names the field).
 */
@Composable
fun OneMindTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = OneMindTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(OneMindTheme.radius.control)
    val textStyle = OneMindTheme.typography.bodyLarge.copy(color = colors.textPrimary)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The field itself carries the label, so a screen reader says "Work email, edit box" once.
        Text(text = label, style = OneMindTheme.typography.label, color = colors.textPrimary, modifier = Modifier.clearAndSetSemantics { })
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(colors.surface, shape)
                            .border(
                                if (focused) OneMindTheme.spacing.focusBorderWidth else OneMindTheme.spacing.borderWidth,
                                if (focused) colors.accent else colors.borderStrong,
                                shape,
                            ).padding(horizontal = 14.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = textStyle, color = colors.textPlaceholder)
                    }
                    innerTextField()
                }
            },
        )
    }
}
