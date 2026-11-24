package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * The venue button at the top of the tabs (*Main bar ▾*): a white pill, 44 high, `borderStrong`. Without
 * [onClick] (the switcher sheet isn't built yet) it shows the name only, with no chevron and no click.
 * [contentDescription] is what a screen reader says (*Venue: Main bar*).
 */
@Composable
fun VenueButton(
    name: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.tileGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .heightIn(min = spacing.minTouchTarget)
                .background(colors.surface, CircleShape)
                .border(spacing.borderWidth, colors.borderStrong, CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    if (onClick != null) role = Role.Button
                }.padding(horizontal = spacing.pillPadding),
    ) {
        // Canvas: an 8 px green dot before the name.
        Box(modifier = Modifier.size(spacing.statusDot).background(colors.positive, CircleShape))
        Text(
            text = name,
            style = OneMindTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Icon(
                imageVector = OneMindIcons.ChevronDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(spacing.chevronSize),
            )
        }
    }
}

/**
 * The account button: the user's initials (*KF*) in a dark 44 circle. Without [onClick] (no account
 * screen yet) it is a label, not a button.
 */
@Composable
fun AccountButton(
    initials: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(OneMindTheme.spacing.minTouchTarget)
                .background(OneMindTheme.colors.textPrimary, CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    if (onClick != null) role = Role.Button
                },
    ) {
        Text(
            text = initials,
            style = OneMindTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = OneMindTheme.colors.onAccent,
            maxLines = 1,
        )
    }
}
