package com.onemind.sales.presentation.losses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.sales.presentation.detail.SkeletonBlock
import com.onemind.sales.presentation.detail.SkeletonLine
import com.onemind.sales.presentation.detail.lineHeightDp
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_loading
import com.onemind.sales.presentation.generated.resources.sales_losses_possible_loss
import org.jetbrains.compose.resources.stringResource

/**
 * 02c while loading: the loaded layout with skeletons in the value slots (no figure, no 0: rule 2), so
 * nothing moves when the answer arrives. Real labels stay real.
 */
@Composable
internal fun LossesSkeleton() {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val description = stringResource(Res.string.sales_detail_loading)
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        OneMindCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight)) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.heroGap)) {
                    Text(text = stringResource(Res.string.sales_losses_possible_loss), style = type.overline, color = colors.textSecondary)
                    SkeletonLine(type.display, widthFraction = HERO_FIGURE)
                    // The change badge: a caption line plus DeltaBadge's 3 above and below (heroGap = 6).
                    SkeletonBlock(Modifier.fillMaxWidth(BADGE).height(type.caption.lineHeightDp() + spacing.heroGap))
                    SkeletonLine(type.caption, widthFraction = RECORDS_LINE)
                }
                SkeletonBlock(
                    Modifier.fillMaxWidth().height(spacing.shareBarHeight),
                    RoundedCornerShape(OneMindTheme.radius.shareBar),
                )
            }
        }
        OneMindListCard {
            repeat(BY_TYPE_ROWS) { index ->
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.rowHeightTall)
                                .padding(horizontal = spacing.cardPaddingCompact),
                    ) {
                        SkeletonBlock(Modifier.size(spacing.swatch), RoundedCornerShape(OneMindTheme.radius.swatch))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap)) {
                            SkeletonLine(type.body, widthFraction = ROW_NAME)
                            SkeletonLine(type.caption, widthFraction = ROW_META)
                        }
                        Box(modifier = Modifier.width(spacing.rowHeightTall)) { SkeletonLine(type.body, widthFraction = 1f) }
                    }
                    if (index < BY_TYPE_ROWS - 1) {
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
}

// Skeleton widths as shares of their slot; drawing only.
private const val HERO_FIGURE = 0.5f
private const val BADGE = 0.45f
private const val RECORDS_LINE = 0.6f
private const val ROW_NAME = 0.4f
private const val ROW_META = 0.7f

/** Discounts, deleted bills, refunds. */
private const val BY_TYPE_ROWS = 3
