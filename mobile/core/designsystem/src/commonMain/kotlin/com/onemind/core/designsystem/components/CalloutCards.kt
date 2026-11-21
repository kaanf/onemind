package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/** Hint card for rule-based insights: `accentSoft`, sparkle in accent, bold [lead] then [body]. */
@Composable
fun HintCard(
    lead: String,
    body: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) = CalloutCard(
    lead = lead,
    body = body,
    icon = OneMindIcons.Sparkle,
    iconTint = OneMindTheme.colors.accent,
    background = OneMindTheme.colors.accentSoft,
    border = null,
    modifier = modifier,
    onClick = onClick,
)

/** Warning card (*Above usual.*): `warningSurface` with `warningBorder`, warning icon in `warningText`. */
@Composable
fun WarningCard(
    lead: String,
    body: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) = CalloutCard(
    lead = lead,
    body = body,
    icon = OneMindIcons.Warning,
    iconTint = OneMindTheme.colors.warningText,
    background = OneMindTheme.colors.warningSurface,
    border = OneMindTheme.colors.warningBorder,
    modifier = modifier,
    onClick = onClick,
)

@Composable
private fun CalloutCard(
    lead: String,
    body: String,
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    border: Color?,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(OneMindTheme.radius.card)
    Row(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.rowGap),
        verticalAlignment = if (onClick != null) Alignment.CenterVertically else Alignment.Top,
        modifier =
            modifier
                .fillMaxWidth()
                .background(background, shape)
                .then(if (border != null) Modifier.border(OneMindTheme.spacing.borderWidth, border, shape) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
                    append(" ")
                    append(body)
                },
            style = OneMindTheme.typography.bodySmall.copy(lineHeight = OneMindTheme.typography.body.lineHeight),
            color = OneMindTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Icon(OneMindIcons.Forward, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
    }
}
