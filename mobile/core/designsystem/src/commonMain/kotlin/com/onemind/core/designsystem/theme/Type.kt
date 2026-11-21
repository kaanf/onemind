package com.onemind.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.generated.resources.Res
import com.onemind.core.designsystem.generated.resources.inter_medium
import com.onemind.core.designsystem.generated.resources.inter_regular
import com.onemind.core.designsystem.generated.resources.inter_semibold
import com.onemind.core.designsystem.generated.resources.inter_tight_bold
import com.onemind.core.designsystem.generated.resources.inter_tight_medium
import com.onemind.core.designsystem.generated.resources.inter_tight_semibold
import org.jetbrains.compose.resources.Font

/** Tabular numerals everywhere (numbers align in rows and tiles) plus Inter's open digits. */
internal const val FONT_FEATURES = "tnum, cv11"

/**
 * Text styles from `docs/design/DESIGN_SYSTEM.md`. Inter for UI, Inter Tight for screen titles and
 * large figures. Vary the weight with `copy(fontWeight = …)` where the design does (e.g. active tab).
 */
@Immutable
data class OneMindTypography(
    /** Hero figure on Today (*48 320 Kč*). */
    val displayLarge: TextStyle,
    /** Hero figure on Sales, Discounts & losses. */
    val display: TextStyle,
    /** Figure in a detail card (*42 sold*). */
    val displaySmall: TextStyle,
    /** Screen title on a tab (*Today*). */
    val title: TextStyle,
    /** Figure in a stat tile (*18:40*, *3 940*). */
    val figure: TextStyle,
    /** Detail screen titles, card values. */
    val headline: TextStyle,
    /** Rows and chat text. */
    val body: TextStyle,
    /** Secondary rows, buttons in chips, date lines. */
    val bodySmall: TextStyle,
    /** Meta lines. */
    val caption: TextStyle,
    /** Section labels; pass UPPERCASE text (*PAID SO FAR*). */
    val overline: TextStyle,
    /** Tab bar labels; active tab uses SemiBold. */
    val tabLabel: TextStyle,
)

@Composable
internal fun oneMindTypography(): OneMindTypography {
    val inter =
        FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
        )
    val interTight =
        FontFamily(
            Font(Res.font.inter_tight_medium, FontWeight.Medium),
            Font(Res.font.inter_tight_semibold, FontWeight.SemiBold),
            Font(Res.font.inter_tight_bold, FontWeight.Bold),
        )
    return oneMindTypography(inter = inter, interTight = interTight)
}

internal fun oneMindTypography(
    inter: FontFamily,
    interTight: FontFamily,
): OneMindTypography {
    fun style(
        family: FontFamily,
        size: Int,
        weight: FontWeight,
        lineHeight: Double? = null,
        letterSpacing: Double? = null,
    ) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
        letterSpacing = letterSpacing?.em ?: TextStyle.Default.letterSpacing,
        fontFeatureSettings = FONT_FEATURES,
    )

    return OneMindTypography(
        displayLarge = style(interTight, 40, FontWeight.SemiBold, lineHeight = 1.0, letterSpacing = -0.03),
        display = style(interTight, 36, FontWeight.SemiBold, lineHeight = 1.05, letterSpacing = -0.03),
        displaySmall = style(interTight, 30, FontWeight.SemiBold, lineHeight = 1.0, letterSpacing = -0.02),
        title = style(interTight, 28, FontWeight.SemiBold, lineHeight = 1.15, letterSpacing = -0.02),
        figure = style(interTight, 22, FontWeight.SemiBold, lineHeight = 1.15),
        headline = style(inter, 17, FontWeight.SemiBold, lineHeight = 1.3),
        body = style(inter, 15, FontWeight.Normal, lineHeight = 1.45),
        bodySmall = style(inter, 13, FontWeight.Normal, lineHeight = 1.4),
        caption = style(inter, 12, FontWeight.Normal, lineHeight = 1.4),
        overline = style(inter, 11, FontWeight.SemiBold, lineHeight = 1.3, letterSpacing = 0.08),
        tabLabel = style(inter, 11, FontWeight.Medium, lineHeight = 1.3),
    )
}
