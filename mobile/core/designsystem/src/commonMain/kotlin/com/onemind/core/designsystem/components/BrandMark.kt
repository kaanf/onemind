package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/** Brand mark (glass on a dark 36 box, radius 10) and the wordmark, as on A1. */
@Composable
fun BrandMark(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).background(OneMindTheme.colors.textPrimary, RoundedCornerShape(OneMindTheme.radius.chip)),
        ) {
            Icon(
                imageVector = OneMindIcons.Brand,
                contentDescription = null,
                tint = OneMindTheme.colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = name, style = OneMindTheme.typography.brand, color = OneMindTheme.colors.textPrimary)
    }
}
