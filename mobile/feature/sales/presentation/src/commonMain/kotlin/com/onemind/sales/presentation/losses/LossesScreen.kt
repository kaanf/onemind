package com.onemind.sales.presentation.losses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.SegmentedTabs
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.UnavailableLossComponent
import com.onemind.sales.presentation.detail.DetailLoading
import com.onemind.sales.presentation.detail.DetailMessage
import com.onemind.sales.presentation.detail.DetailScaffold
import com.onemind.sales.presentation.detail.dateRange
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_data_gaps
import com.onemind.sales.presentation.generated.resources.sales_detail_no_data_yet
import com.onemind.sales.presentation.generated.resources.sales_detail_no_venue
import com.onemind.sales.presentation.generated.resources.sales_detail_venues_syncing
import com.onemind.sales.presentation.generated.resources.sales_losses_by_type
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
import com.onemind.sales.presentation.generated.resources.sales_losses_period_day
import com.onemind.sales.presentation.generated.resources.sales_losses_period_month
import com.onemind.sales.presentation.generated.resources.sales_losses_period_week
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
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(LossesAction.Refresh) },
    ) {
        PeriodTabs(state.period) { onAction(LossesAction.SelectPeriod(it)) }
        when (val content = state.content) {
            LossesContent.Loading -> {
                DetailLoading()
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

            is LossesContent.Ready -> {
                LossesContentColumn(content)
            }
        }
    }
}

@Composable
private fun PeriodTabs(
    selected: LossesPeriod,
    onSelect: (LossesPeriod) -> Unit,
) {
    val periods = LossesPeriod.entries
    SegmentedTabs(
        options =
            periods.map {
                when (it) {
                    LossesPeriod.DAY -> stringResource(Res.string.sales_losses_period_day)
                    LossesPeriod.WEEK -> stringResource(Res.string.sales_losses_period_week)
                    LossesPeriod.MONTH -> stringResource(Res.string.sales_losses_period_month)
                }
            },
        selectedIndex = periods.indexOf(selected),
        onSelect = { onSelect(periods[it]) },
    )
}

@Composable
private fun LossesContentColumn(content: LossesContent.Ready) {
    val losses = content.losses
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    content.refreshError?.let { Text(text = it.asString(), style = type.caption, color = colors.negativeText) }
    if (losses.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.sales_detail_venues_syncing, losses.pendingVenueCount, losses.pendingVenueCount),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
    if (!losses.hasData) {
        DetailMessage(stringResource(Res.string.sales_detail_no_data_yet), isError = false)
        return
    }
    Hero(losses)
    if (losses.rows.isEmpty() && losses.discountTypes.isEmpty()) {
        DetailMessage(stringResource(Res.string.sales_losses_empty), isError = false)
    } else {
        ByType(losses.rows)
        DiscountTypes(losses.discountTypes)
    }
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

@Composable
private fun Hero(losses: LossesUi) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap)) {
            Text(text = stringResource(Res.string.sales_losses_possible_loss), style = type.overline, color = colors.textSecondary)
            losses.possibleLoss?.let {
                Text(text = it, style = type.displayLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            }
            losses.change?.let { change ->
                val text =
                    when (losses.period) {
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
            val meta =
                listOfNotNull(
                    losses.records?.let { pluralStringResource(Res.plurals.sales_losses_records, it, it.toString()) },
                    losses.sharePercent?.let { stringResource(Res.string.sales_losses_share, it) },
                ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(text = meta, style = type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ByType(rows: List<LossRow>) {
    if (rows.isEmpty()) return
    SectionTitle(stringResource(Res.string.sales_losses_by_type))
    OneMindListCard {
        rows.forEachIndexed { index, row ->
            val (title, meta) =
                when (row.kind) {
                    LossRowKind.DISCOUNTS -> Res.string.sales_losses_discounts to Res.string.sales_losses_discounts_meta
                    LossRowKind.DELETED_BILLS -> Res.string.sales_losses_deleted_bills to Res.string.sales_losses_deleted_bills_meta
                    LossRowKind.REFUNDS -> Res.string.sales_losses_refunds to Res.string.sales_losses_refunds_meta
                }
            ListRow(
                title = stringResource(title),
                meta = withCount(stringResource(meta), row.records),
                trailingValue = row.amount,
                showDivider = index < rows.lastIndex,
            )
        }
    }
}

@Composable
private fun DiscountTypes(rows: List<DiscountRow>) {
    if (rows.isEmpty()) return
    SectionTitle(stringResource(Res.string.sales_losses_discount_types))
    OneMindListCard {
        rows.forEachIndexed { index, row ->
            val name =
                when (val n = row.name) {
                    DiscountName.Other -> stringResource(Res.string.sales_losses_discount_other)
                    DiscountName.Unspecified -> stringResource(Res.string.sales_losses_discount_unspecified)
                    is DiscountName.Pos -> n.name
                }
            ListRow(
                title = name,
                meta = row.records?.let { pluralStringResource(Res.plurals.sales_losses_records, it, it.toString()) },
                trailingValue = row.amount,
                showDivider = index < rows.lastIndex,
            )
        }
    }
}

@Composable
private fun withCount(
    meta: String,
    records: Int?,
): String = records?.let { stringResource(Res.string.sales_losses_meta_count, meta, it.toString()) } ?: meta

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = OneMindTheme.typography.headline,
            color = OneMindTheme.colors.textPrimary,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
    }
}

@Composable
private fun UnavailableLossComponent.text(): String =
    stringResource(
        when (this) {
            UnavailableLossComponent.UNPAID_CLOSED -> Res.string.sales_losses_unavailable_unpaid_closed
            UnavailableLossComponent.ITEM_VOIDS -> Res.string.sales_losses_unavailable_item_voids
            UnavailableLossComponent.STOCK_CORRECTIONS -> Res.string.sales_losses_unavailable_stock_corrections
        },
    )

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
