package com.onemind.sales.presentation.losses

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OneMindLoadingSlot
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.theme.OneMindMotion
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.UnavailableLossComponent
import com.onemind.sales.presentation.detail.DetailMessage
import com.onemind.sales.presentation.detail.DetailScaffold
import com.onemind.sales.presentation.detail.SkeletonBlock
import com.onemind.sales.presentation.detail.SkeletonLine
import com.onemind.sales.presentation.detail.dateRange
import com.onemind.sales.presentation.detail.lineHeightDp
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_data_gaps
import com.onemind.sales.presentation.generated.resources.sales_detail_loading
import com.onemind.sales.presentation.generated.resources.sales_detail_no_data_yet
import com.onemind.sales.presentation.generated.resources.sales_detail_no_venue
import com.onemind.sales.presentation.generated.resources.sales_detail_venues_syncing
import com.onemind.sales.presentation.generated.resources.sales_losses_change_vs_day
import com.onemind.sales.presentation.generated.resources.sales_losses_change_vs_month
import com.onemind.sales.presentation.generated.resources.sales_losses_change_vs_week
import com.onemind.sales.presentation.generated.resources.sales_losses_deleted_bills
import com.onemind.sales.presentation.generated.resources.sales_losses_deleted_bills_meta
import com.onemind.sales.presentation.generated.resources.sales_losses_discount_other
import com.onemind.sales.presentation.generated.resources.sales_losses_discount_types
import com.onemind.sales.presentation.generated.resources.sales_losses_discount_unspecified
import com.onemind.sales.presentation.generated.resources.sales_losses_discounts
import com.onemind.sales.presentation.generated.resources.sales_losses_discounts_meta
import com.onemind.sales.presentation.generated.resources.sales_losses_empty
import com.onemind.sales.presentation.generated.resources.sales_losses_meta_count
import com.onemind.sales.presentation.generated.resources.sales_losses_possible_loss
import com.onemind.sales.presentation.generated.resources.sales_losses_records
import com.onemind.sales.presentation.generated.resources.sales_losses_refunds
import com.onemind.sales.presentation.generated.resources.sales_losses_refunds_meta
import com.onemind.sales.presentation.generated.resources.sales_losses_share
import com.onemind.sales.presentation.generated.resources.sales_losses_title
import com.onemind.sales.presentation.generated.resources.sales_losses_unavailable_body
import com.onemind.sales.presentation.generated.resources.sales_losses_unavailable_item_voids
import com.onemind.sales.presentation.generated.resources.sales_losses_unavailable_lead
import com.onemind.sales.presentation.generated.resources.sales_losses_unavailable_stock_corrections
import com.onemind.sales.presentation.generated.resources.sales_losses_unavailable_unpaid_closed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LossesRoot(
    onBack: () -> Unit,
    viewModel: LossesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LossesScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun LossesScreen(
    state: LossesState,
    onAction: (LossesAction) -> Unit,
    onBack: () -> Unit,
) {
    val ready = state.content as? LossesContent.Ready
    DetailScaffold(
        title = stringResource(Res.string.sales_losses_title),
        subtitle = ready?.losses?.let { dateRange(it.from, it.to) },
        subtitleLoading = state.content == LossesContent.Loading,
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(LossesAction.Refresh) },
    ) {
        // The canvas has no period control: 02c shows the week (*2 to 8 Nov*), the ViewModel's default.
        // Loading and figures are one layout: the cards stay and only their value slots shimmer, so nothing
        // moves when the answer arrives. Messages crossfade in place; a refresh of the same state doesn't animate.
        AnimatedContent(
            targetState = state.content,
            transitionSpec = { (fadeIn(OneMindMotion.medium()) togetherWith fadeOut(OneMindMotion.medium())).using(null) },
            contentKey = { if (it is LossesContent.Loading || it is LossesContent.Ready) LossesContent.Ready::class else it::class },
            label = "losses",
        ) { content ->
            val loadingDescription = stringResource(Res.string.sales_detail_loading)
            val loading = content == LossesContent.Loading
            Column(
                verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.sectionGapTight),
                modifier = if (loading) Modifier.semantics { contentDescription = loadingDescription } else Modifier,
            ) {
                when (content) {
                    // One call site for both, so each value slot crossfades from its skeleton in place.
                    LossesContent.Loading, is LossesContent.Ready -> {
                        LossesContentColumn(content as? LossesContent.Ready)
                    }

                    LossesContent.NoVenue -> {
                        DetailMessage(stringResource(Res.string.sales_detail_no_venue), isError = false)
                    }

                    is LossesContent.Failed -> {
                        DetailMessage(
                            content.message.asString(),
                            isError = true,
                            onRetry = { onAction(LossesAction.Refresh) },
                        )
                    }
                }
            }
        }
    }
}

/** The figures, or with [content] `null` (loading) the same cards with skeletons in their value slots. */
@Composable
private fun LossesContentColumn(content: LossesContent.Ready?) {
    val losses = content?.losses
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    content?.refreshError?.let { Text(text = it.asString(), style = type.caption, color = colors.negativeText) }
    if (losses != null && losses.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.sales_detail_venues_syncing, losses.pendingVenueCount, losses.pendingVenueCount),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
    if (losses != null && !losses.hasData) {
        DetailMessage(stringResource(Res.string.sales_detail_no_data_yet), isError = false)
        return
    }
    Hero(losses)
    if (losses != null && losses.rows.isEmpty() && losses.discountTypes.isEmpty()) {
        DetailMessage(stringResource(Res.string.sales_losses_empty), isError = false)
    } else {
        ByType(losses?.rows)
        losses?.let { DiscountTypes(it.discountTypes) }
    }
    if (losses == null) return
    if (losses.unavailable.isNotEmpty()) {
        val parts = losses.unavailable.map { it.text() }.joinToString(", ")
        WarningCard(
            lead = stringResource(Res.string.sales_losses_unavailable_lead),
            body = stringResource(Res.string.sales_losses_unavailable_body, parts),
        )
    }
    if (losses.hasDataGaps) {
        Text(text = stringResource(Res.string.sales_detail_data_gaps), style = type.caption, color = colors.warningText)
    }
}

/**
 * POSSIBLE LOSS card: overline, 36 figure, change badge beside the records line, then the stacked share
 * bar. With [losses] `null` (loading) the value slots shimmer, each crossfading to its value in place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(losses: LossesUi?) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    OneMindCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.heroGap)) {
                Text(text = stringResource(Res.string.sales_losses_possible_loss), style = type.overline, color = colors.textSecondary)
                if (losses == null || losses.possibleLoss != null) {
                    OneMindLoadingSlot(
                        value = losses?.possibleLoss,
                        skeleton = { SkeletonLine(type.display, widthFraction = SKELETON_HERO_FIGURE) },
                    ) {
                        Text(text = it, style = type.display, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                    }
                }
                if (losses == null || losses.change != null || losses.records != null || losses.sharePercent != null) {
                    OneMindLoadingSlot(value = losses, skeleton = { MetaSkeleton() }) { MetaRow(it) }
                }
            }
            OneMindLoadingSlot(
                value = losses?.rows,
                skeleton = {
                    SkeletonBlock(Modifier.fillMaxWidth().height(spacing.shareBarHeight), RoundedCornerShape(OneMindTheme.radius.shareBar))
                },
            ) { ShareBar(it) }
        }
    }
}

/** The change badge beside the records line; on a narrow screen the records line moves under the badge whole. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(losses: LossesUi) {
    val meta =
        listOfNotNull(
            losses.records?.let { pluralStringResource(Res.plurals.sales_losses_records, it, it.toString()) },
            losses.sharePercent?.let { stringResource(Res.string.sales_losses_share, it) },
        ).joinToString(" · ")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap),
        verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.listGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        losses.change?.let { ChangeBadge(losses.period, it) }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = OneMindTheme.typography.caption,
                color = OneMindTheme.colors.textSecondary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The badge (a caption line plus DeltaBadge's 3 above and below, heroGap = 6) beside the records line. */
@Composable
private fun MetaSkeleton() {
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.textGap), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.weight(SKELETON_BADGE).height(type.caption.lineHeightDp() + spacing.heroGap))
        Box(modifier = Modifier.weight(1f - SKELETON_BADGE)) { SkeletonLine(type.caption, widthFraction = SKELETON_RECORDS_LINE) }
    }
}

@Composable
private fun ChangeBadge(
    period: LossesPeriod,
    change: LossChange,
) {
    val text =
        when (period) {
            LossesPeriod.DAY -> stringResource(Res.string.sales_losses_change_vs_day, change.points)
            LossesPeriod.WEEK -> stringResource(Res.string.sales_losses_change_vs_week, change.points)
            LossesPeriod.MONTH -> stringResource(Res.string.sales_losses_change_vs_month, change.points)
        }
    // A higher loss share is bad news.
    val tone =
        when (change.direction) {
            LossChange.Direction.UP -> BadgeTone.Negative
            LossChange.Direction.DOWN -> BadgeTone.Positive
            LossChange.Direction.FLAT -> BadgeTone.Neutral
        }
    DeltaBadge(text = text, tone = tone)
}

/** One segment per component, as wide as its share of the possible loss, in the by-type colours. */
@Composable
private fun ShareBar(rows: List<LossRow>) {
    val shape = RoundedCornerShape(OneMindTheme.radius.shareBar)
    val segments = rows.mapNotNull { row -> row.shareFraction?.takeIf { it > 0f }?.let { row.kind to it } }
    if (segments.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.shareBarGap),
        modifier = Modifier.fillMaxWidth().height(OneMindTheme.spacing.shareBarHeight),
    ) {
        segments.forEach { (kind, fraction) ->
            Box(modifier = Modifier.weight(fraction).fillMaxHeight().background(kind.color(), shape))
        }
    }
}

/**
 * By type: swatch, name, meta with count, amount (no section title, as on the canvas). With [rows] `null`
 * (loading) every kind shows with its real name and swatch and shimmering values.
 */
@Composable
private fun ByType(rows: List<LossRow>?) {
    if (rows?.isEmpty() == true) return
    val shown: List<Pair<LossRowKind, LossRow?>> = rows?.map { it.kind to it } ?: LossRowKind.entries.map { it to null }
    OneMindListCard {
        shown.forEachIndexed { index, (kind, row) ->
            val (title, meta) =
                when (kind) {
                    LossRowKind.DISCOUNTS -> Res.string.sales_losses_discounts to Res.string.sales_losses_discounts_meta
                    LossRowKind.DELETED_BILLS -> Res.string.sales_losses_deleted_bills to Res.string.sales_losses_deleted_bills_meta
                    LossRowKind.REFUNDS -> Res.string.sales_losses_refunds to Res.string.sales_losses_refunds_meta
                }
            key(kind) {
                LossTypeRow(
                    swatch = kind.color(),
                    name = stringResource(title),
                    meta = row?.let { withCount(stringResource(meta), it.records) },
                    amount = row?.amount,
                    showDivider = index < shown.lastIndex,
                    loading = row == null,
                )
            }
        }
    }
}

/** Discounts by POS type, in the canvas's titled-card style (header 15/600, rows like By type). */
@Composable
private fun DiscountTypes(rows: List<DiscountRow>) {
    if (rows.isEmpty()) return
    val colors = OneMindTheme.colors
    OneMindListCard {
        Text(
            text = stringResource(Res.string.sales_losses_discount_types),
            style = OneMindTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier =
                Modifier
                    .padding(top = OneMindTheme.spacing.cardPaddingCompact)
                    .padding(horizontal = OneMindTheme.spacing.cardPaddingCompact)
                    .semantics { heading() },
        )
        rows.forEachIndexed { index, row ->
            val name =
                when (val n = row.name) {
                    DiscountName.Other -> stringResource(Res.string.sales_losses_discount_other)
                    DiscountName.Unspecified -> stringResource(Res.string.sales_losses_discount_unspecified)
                    is DiscountName.Pos -> n.name
                }
            LossTypeRow(
                swatch = null,
                name = name,
                meta = row.records?.let { pluralStringResource(Res.plurals.sales_losses_records, it, it.toString()) },
                amount = row.amount,
                showDivider = index < rows.lastIndex,
                loading = false,
            )
        }
    }
}

/**
 * A by-type row: 60 high, 10 × 10 swatch, name 15/600 over meta 12, amount 15/500, divider. While
 * [loading], meta and amount shimmer in their slots and crossfade to the values.
 */
@Composable
private fun LossTypeRow(
    swatch: Color?,
    name: String,
    meta: String?,
    amount: String?,
    showDivider: Boolean,
    loading: Boolean,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
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
            if (swatch != null) {
                Box(modifier = Modifier.size(spacing.swatch).background(swatch, RoundedCornerShape(OneMindTheme.radius.swatch)))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap)) {
                Text(text = name, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                if (loading || meta != null) {
                    OneMindLoadingSlot(value = meta, skeleton = { SkeletonLine(type.caption, widthFraction = SKELETON_ROW_META) }) {
                        Text(text = it, style = type.caption, color = colors.textSecondary)
                    }
                }
            }
            OneMindLoadingSlot(
                value = amount,
                skeleton = { Box(modifier = Modifier.width(spacing.rowHeightTall)) { SkeletonLine(type.body, widthFraction = 1f) } },
            ) {
                Text(text = it, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = spacing.borderWidth,
                color = colors.divider,
                modifier = Modifier.padding(horizontal = spacing.cardPaddingCompact),
            )
        }
    }
}

/** Swatch colours of the canvas: discounts accent, deleted dark, refunds grey (unpaid red is not in the API yet). */
@Composable
private fun LossRowKind.color(): Color =
    when (this) {
        LossRowKind.DISCOUNTS -> OneMindTheme.colors.accent
        LossRowKind.DELETED_BILLS -> OneMindTheme.colors.textPrimary
        LossRowKind.REFUNDS -> OneMindTheme.colors.iconMuted
    }

@Composable
private fun withCount(
    meta: String,
    records: Int?,
): String = records?.let { stringResource(Res.string.sales_losses_meta_count, meta, it.toString()) } ?: meta

@Composable
private fun UnavailableLossComponent.text(): String =
    stringResource(
        when (this) {
            UnavailableLossComponent.UNPAID_CLOSED -> Res.string.sales_losses_unavailable_unpaid_closed
            UnavailableLossComponent.ITEM_VOIDS -> Res.string.sales_losses_unavailable_item_voids
            UnavailableLossComponent.STOCK_CORRECTIONS -> Res.string.sales_losses_unavailable_stock_corrections
        },
    )

// Skeleton widths as shares of their slot; drawing only.
private const val SKELETON_HERO_FIGURE = 0.5f
private const val SKELETON_BADGE = 0.45f
private const val SKELETON_RECORDS_LINE = 0.6f
private const val SKELETON_ROW_META = 0.7f

@Preview
@Composable
private fun LossesReadyPreview() {
    OneMindTheme { LossesScreen(LossesState(content = LossesContent.Ready(LossesPreviewData.ready)), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun LossesNoDataPreview() {
    OneMindTheme {
        LossesScreen(LossesState(content = LossesContent.Ready(LossesPreviewData.ready.copy(hasData = false))), onAction = {}, onBack = {})
    }
}

@Preview
@Composable
private fun LossesErrorPreview() {
    OneMindTheme {
        LossesScreen(
            LossesState(content = LossesContent.Failed(UiText.Dynamic("Something went wrong."))),
            onAction = {},
            onBack = {},
        )
    }
}
