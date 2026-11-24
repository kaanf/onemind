package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/** Card: `surface` + 1 px `border`, radius 16, no shadow, content clipped to the shape. */
@Composable
fun OneMindCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(OneMindTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(OneMindTheme.radius.card)
    Column(
        modifier =
            modifier
                // Clipped, so a clickable row's ripple keeps the card's corners.
                .clip(shape)
                .background(OneMindTheme.colors.surface, shape)
                .border(OneMindTheme.spacing.borderWidth, OneMindTheme.colors.border, shape)
                .padding(contentPadding),
        content = content,
    )
}

/** Card holding [ListRow]s: no padding; each row pads itself (16), so its ripple fills the card's width. */
@Composable
fun OneMindListCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = OneMindCard(modifier = modifier, contentPadding = PaddingValues(0.dp), content = content)
