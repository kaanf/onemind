package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * List row inside an [OneMindListCard]: optional leading (avatar, swatch, dot, icon box), title + meta,
 * trailing value + meta, optional chevron. Pass `showDivider = false` for the last row.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingValue: String? = null,
    trailingMeta: String? = null,
    trailingValueColor: Color = OneMindTheme.colors.textPrimary,
    trailingValueWeight: FontWeight = FontWeight.Medium,
    /** Drawn in place of [trailingValue] (a skeleton while loading). */
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    minHeight: Dp = 56.dp,
    /** Side padding inside the row (its card has none), so the ripple covers the full width. */
    horizontalPadding: Dp = OneMindTheme.spacing.cardPaddingCompact,
    onClick: (() -> Unit)? = null,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = horizontalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.rowGap),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = type.body.copy(fontWeight = if (meta != null) FontWeight.SemiBold else FontWeight.Medium),
                    color = colors.textPrimary,
                )
                if (meta != null) Text(text = meta, style = type.caption, color = colors.textSecondary)
            }
            trailingContent?.invoke()
            if (trailingValue != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = trailingValue,
                        style = type.body.copy(fontWeight = trailingValueWeight),
                        color = trailingValueColor,
                        maxLines = 1,
                    )
                    if (trailingMeta != null) {
                        Text(text = trailingMeta, style = type.caption, color = colors.textSecondary, maxLines = 1)
                    }
                }
            }
            if (showChevron) {
                Icon(OneMindIcons.Forward, contentDescription = null, tint = colors.iconMuted, modifier = Modifier.size(16.dp))
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(OneMindTheme.spacing.borderWidth).background(colors.divider))
        }
    }
}
