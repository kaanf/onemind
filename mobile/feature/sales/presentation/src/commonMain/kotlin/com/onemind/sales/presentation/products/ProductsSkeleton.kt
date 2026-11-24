package com.onemind.sales.presentation.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindFilterChip
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.sales.presentation.detail.SkeletonBlock
import com.onemind.sales.presentation.detail.SkeletonLine
import com.onemind.sales.presentation.detail.lineHeightDp
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_loading
import com.onemind.sales.presentation.generated.resources.sales_products_all
import com.onemind.sales.presentation.generated.resources.sales_products_caption
import org.jetbrains.compose.resources.stringResource

/**
 * 02a while loading: the loaded layout (chips, chart card, item list) with skeletons in the value slots
 * and bars (no figure, no 0: rule 2), so nothing moves when the answer arrives. Real labels stay real.
 */
@Composable
internal fun ProductsSkeleton() {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val description = stringResource(Res.string.sales_detail_loading)
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        ChipsSkeleton()
        OneMindCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.cardPaddingTight),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.listGap), modifier = Modifier.weight(1f)) {
                        SkeletonLine(type.overline, widthFraction = OVERLINE)
                        SkeletonLine(type.displaySmall, widthFraction = FIGURE)
                    }
                    // The Peak tag: a caption line plus DeltaBadge's 3 above and below (heroGap = 6).
                    Box(modifier = Modifier.weight(PEAK_TAG)) {
                        SkeletonBlock(Modifier.fillMaxWidth().height(type.caption.lineHeightDp() + spacing.heroGap))
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.barGapWide),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().height(spacing.chartHeightTall),
                ) {
                    BARS.forEach { fraction ->
                        SkeletonBlock(Modifier.weight(1f).fillMaxHeight(fraction), RoundedCornerShape(OneMindTheme.radius.bar))
                    }
                }
                SkeletonLine(type.tabLabel, widthFraction = 1f)
                Text(text = stringResource(Res.string.sales_products_caption), style = type.caption, color = colors.textSecondary)
            }
        }
        val cardShape = RoundedCornerShape(OneMindTheme.radius.card)
        OneMindCard(modifier = Modifier.fillMaxWidth().clip(cardShape), contentPadding = PaddingValues()) {
            repeat(ROWS) { index ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.listRowHeight)
                            .padding(horizontal = spacing.cardPaddingCompact),
                ) {
                    Spacer(modifier = Modifier.width(spacing.selectionMarkerWidth))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap)) {
                        SkeletonLine(type.body, widthFraction = ROW_NAME)
                        SkeletonLine(type.caption, widthFraction = ROW_META)
                    }
                    Box(modifier = Modifier.width(spacing.rowHeightTall)) { SkeletonLine(type.labelLarge, widthFraction = 1f) }
                }
                if (index < ROWS - 1) {
                    HorizontalDivider(
                        thickness = spacing.borderWidth,
                        color = colors.divider,
                        modifier = Modifier.padding(horizontal = spacing.cardPaddingCompact),
                    )
                }
            }
        }
    }
}

/** *All* is a real label (its chip sets the row's height); the categories come with the data. */
@Composable
private fun ChipsSkeleton() {
    val gutter = OneMindTheme.spacing.screenHorizontal
    Row(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.bleed(gutter).padding(horizontal = gutter).height(IntrinsicSize.Min),
    ) {
        OneMindFilterChip(label = stringResource(Res.string.sales_products_all), selected = true, onClick = {})
        repeat(CHIPS) {
            SkeletonBlock(
                Modifier.fillMaxHeight(CHIP_HEIGHT).aspectRatio(CHIP_ASPECT),
                RoundedCornerShape(OneMindTheme.radius.chip),
            )
        }
    }
}

// Skeleton sizes as shares of their slot; drawing only.
private const val OVERLINE = 0.35f
private const val FIGURE = 0.45f
private const val PEAK_TAG = 0.3f
private const val ROW_NAME = 0.5f
private const val ROW_META = 0.75f
private const val ROWS = 5
private const val CHIPS = 4

/** The 36 high pill inside the chip's 48 touch target. */
private const val CHIP_HEIGHT = 0.75f
private const val CHIP_ASPECT = 2f

/** A fixed, evening-shaped silhouette: never data. */
private val BARS = listOf(0.25f, 0.4f, 0.55f, 0.45f, 0.6f, 0.8f, 1f, 0.85f, 0.65f, 0.4f, 0.2f)
