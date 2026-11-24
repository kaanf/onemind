package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindColors
import com.onemind.core.designsystem.theme.OneMindTheme

/** What a badge says; [Positive] / [Negative] are about meaning (good / bad), not the sign of the number. */
enum class BadgeTone { Positive, Negative, Neutral }

/** Small tag (*+12.4% vs last Friday*, *Peak 22:00*): 12/600, radius 6, tinted background. */
@Composable
fun DeltaBadge(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    /** SemiBold for deltas; Medium for a neutral tag such as *Peak 22:00* (02a). */
    weight: FontWeight = FontWeight.SemiBold,
) {
    val (background, content) = OneMindTheme.colors.badgeColors(tone)
    Text(
        text = text,
        style = OneMindTheme.typography.caption.copy(fontWeight = weight),
        color = content,
        maxLines = 1,
        modifier =
            modifier
                .background(background, RoundedCornerShape(OneMindTheme.radius.tag))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

internal fun OneMindColors.badgeColors(tone: BadgeTone) =
    when (tone) {
        BadgeTone.Positive -> positiveSurface to positive
        BadgeTone.Negative -> negativeSurface to negativeText
        BadgeTone.Neutral -> surfaceMuted to textPrimary
    }
