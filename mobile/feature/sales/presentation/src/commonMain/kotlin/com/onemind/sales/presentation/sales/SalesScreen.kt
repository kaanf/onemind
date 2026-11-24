package com.onemind.sales.presentation.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.core.designsystem.components.SegmentedTabs
import com.onemind.core.designsystem.components.StatTile
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRange
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_all_venues
import com.onemind.sales.presentation.generated.resources.sales_average_bill
import com.onemind.sales.presentation.generated.resources.sales_by_payment
import com.onemind.sales.presentation.generated.resources.sales_cached
import com.onemind.sales.presentation.generated.resources.sales_cached_offline
import com.onemind.sales.presentation.generated.resources.sales_cached_unknown
import com.onemind.sales.presentation.generated.resources.sales_cached_unknown_offline
import com.onemind.sales.presentation.generated.resources.sales_card_share
import com.onemind.sales.presentation.generated.resources.sales_cash_share
import com.onemind.sales.presentation.generated.resources.sales_data_gaps_lead
import com.onemind.sales.presentation.generated.resources.sales_discounts_losses_link
import com.onemind.sales.presentation.generated.resources.sales_guests
import com.onemind.sales.presentation.generated.resources.sales_loading
import com.onemind.sales.presentation.generated.resources.sales_no_card_or_cash
import com.onemind.sales.presentation.generated.resources.sales_no_venue
import com.onemind.sales.presentation.generated.resources.sales_payment_bar_description
import com.onemind.sales.presentation.generated.resources.sales_per_guest
import com.onemind.sales.presentation.generated.resources.sales_products_by_hour_link
import com.onemind.sales.presentation.generated.resources.sales_range_day
import com.onemind.sales.presentation.generated.resources.sales_range_hour
import com.onemind.sales.presentation.generated.resources.sales_range_month
import com.onemind.sales.presentation.generated.resources.sales_range_same_month
import com.onemind.sales.presentation.generated.resources.sales_range_two_months
import com.onemind.sales.presentation.generated.resources.sales_retry
import com.onemind.sales.presentation.generated.resources.sales_revenue
import com.onemind.sales.presentation.generated.resources.sales_tips
import com.onemind.sales.presentation.generated.resources.sales_title
import com.onemind.sales.presentation.generated.resources.sales_updated_at
import com.onemind.sales.presentation.generated.resources.sales_value_empty
import com.onemind.sales.presentation.generated.resources.sales_venues_syncing
import com.onemind.sales.presentation.generated.resources.sales_vs_last_week
import com.onemind.sales.presentation.generated.resources.sales_vs_last_weekday
import com.onemind.sales.presentation.generated.resources.sales_vs_previous_month
import com.onemind.sales.presentation.generated.resources.sales_vs_same_hour
import com.onemind.sales.presentation.generated.resources.sales_waiting_first_sync
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SalesRoot(
    onOpenProductsByHour: () -> Unit,
    onOpenLosses: () -> Unit,
    viewModel: SalesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SalesScreen(state = state, onAction = viewModel::onAction, onOpenProductsByHour = onOpenProductsByHour, onOpenLosses = onOpenLosses)
}

@Composable
fun SalesScreen(
    state: SalesState,
    onAction: (SalesAction) -> Unit,
    onOpenProductsByHour: () -> Unit = {},
    onOpenLosses: () -> Unit = {},
) {
    val title = stringResource(Res.string.sales_title)
    when (val content = state.content) {
        SalesContent.NoVenue -> {
            PlaceholderScreen(title = title, message = stringResource(Res.string.sales_no_venue))
        }

        else -> {
            Refreshable(state, onAction) {
                Header(title = title, state = state, range = (content as? SalesContent.Ready)?.sales?.range)
                PeriodTabs(state.period, onAction)
                when (content) {
                    SalesContent.Loading -> {
                        val description = stringResource(Res.string.sales_loading)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(OneMindTheme.spacing.sectionGap),
                        ) {
                            CircularProgressIndicator(
                                color = OneMindTheme.colors.accent,
                                modifier = Modifier.semantics { contentDescription = description },
                            )
                        }
                    }

                    is SalesContent.Failed -> {
                        Text(
                            text = content.message.asString(),
                            style = OneMindTheme.typography.body,
                            color = OneMindTheme.colors.negativeText,
                        )
                        OutlineButton(text = stringResource(Res.string.sales_retry), onClick = { onAction(SalesAction.Refresh) })
                    }

                    is SalesContent.Ready -> {
                        SalesContentColumn(content, onOpenProductsByHour, onOpenLosses)
                    }

                    SalesContent.NoVenue -> {
                        Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(
    state: SalesState,
    onAction: (SalesAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = OneMindTheme.spacing
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(SalesAction.Refresh) },
        modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.rowGap),
        ) {
            content()
        }
    }
}

/** Venue scope, the period's range label (*2 to 8 Nov*) and the title. */
@Composable
private fun Header(
    title: String,
    state: SalesState,
    range: SalesRange?,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val scope = if (state.isAllVenues) stringResource(Res.string.sales_all_venues) else state.venueName
    val line = listOfNotNull(scope, range?.label()).joinToString(" · ")
    Column {
        if (line.isNotEmpty()) Text(text = line, style = type.bodySmall, color = colors.textSecondary)
        Text(text = title, style = type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun PeriodTabs(
    selected: SalesPeriod,
    onAction: (SalesAction) -> Unit,
) {
    val periods = SalesPeriod.entries
    SegmentedTabs(
        options = periods.map { stringResource(it.labelRes()) },
        selectedIndex = periods.indexOf(selected),
        onSelect = { onAction(SalesAction.SelectPeriod(periods[it])) },
    )
}

@Composable
private fun SalesContentColumn(
    content: SalesContent.Ready,
    onOpenProductsByHour: () -> Unit,
    onOpenLosses: () -> Unit,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val sales = content.sales
    content.cached?.let { marker ->
        Text(text = marker.text(), style = type.caption, color = if (marker.offline) colors.warningText else colors.textSecondary)
    }
    content.refreshError?.let { Text(text = it.asString(), style = type.caption, color = colors.negativeText) }
    if (sales.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.sales_venues_syncing, sales.pendingVenueCount, sales.pendingVenueCount),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
    RevenueCard(sales)
    PaymentCard(sales)
    Links(sales, onOpenProductsByHour, onOpenLosses)
    if (sales.dataGaps.isNotEmpty()) {
        WarningCard(
            lead = stringResource(Res.string.sales_data_gaps_lead),
            body = sales.dataGaps.map { pluralStringResource(it.kind.pluralRes(), it.count, it.count) }.joinToString(" "),
        )
    }
    sales.updatedAt?.let {
        Text(text = stringResource(Res.string.sales_updated_at, it), style = type.caption, color = colors.textSecondary)
    }
}

@Composable
private fun RevenueCard(sales: SalesUi) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.sales_value_empty)
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
                Text(text = stringResource(Res.string.sales_revenue).uppercase(), style = type.overline, color = colors.textSecondary)
                Text(text = sales.revenue ?: empty, style = type.displayLarge, color = colors.textPrimary)
                sales.comparison?.let { DeltaBadge(text = it.text(), tone = it.tone) }
                if (sales.waitingForFirstSync) {
                    Text(text = stringResource(Res.string.sales_waiting_first_sync), style = type.bodySmall, color = colors.textSecondary)
                }
            }
            HorizontalDivider(thickness = spacing.borderWidth, color = colors.divider)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
                StatTile(label = stringResource(Res.string.sales_guests), value = sales.guests ?: empty, modifier = Modifier.weight(1f))
                StatTile(
                    label = stringResource(Res.string.sales_average_bill),
                    value = sales.averageBill ?: empty,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(Res.string.sales_per_guest),
                    value = sales.perGuest ?: empty,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** *By payment*: tips total, then the card/cash split bar with each side's amount. */
@Composable
private fun PaymentCard(sales: SalesUi) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.sales_value_empty)
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.tileGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.sales_by_payment),
                    style = type.headline,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = stringResource(Res.string.sales_tips), style = type.caption, color = colors.textSecondary)
                Text(text = sales.tips ?: empty, style = type.bodySmall, color = colors.textPrimary)
            }
            val payment = sales.payment
            if (payment == null) {
                Text(
                    text = if (sales.waitingForFirstSync) empty else stringResource(Res.string.sales_no_card_or_cash),
                    style = type.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                val description = stringResource(Res.string.sales_payment_bar_description, payment.cardPercent, payment.cashPercent)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(spacing.rowGap)
                            .clip(RoundedCornerShape(OneMindTheme.radius.bar))
                            .background(colors.chartMuted)
                            .semantics { contentDescription = description },
                ) {
                    if (payment.cardPercent > 0) Box(Modifier.weight(payment.cardPercent.toFloat()).fillMaxSize().background(colors.accent))
                    if (payment.cashPercent >
                        0
                    ) {
                        Box(Modifier.weight(payment.cashPercent.toFloat()).fillMaxSize().background(colors.chartMutedStrong))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        label = stringResource(Res.string.sales_card_share, payment.cardPercent),
                        value = payment.card,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(Res.string.sales_cash_share, payment.cashPercent),
                        value = payment.cash,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Entry points to 02a and 02c (02b Hourly patterns isn't built yet). */
@Composable
private fun Links(
    sales: SalesUi,
    onOpenProductsByHour: () -> Unit,
    onOpenLosses: () -> Unit,
) {
    val colors = OneMindTheme.colors
    OneMindListCard {
        ListRow(title = stringResource(Res.string.sales_products_by_hour_link), showChevron = true, onClick = onOpenProductsByHour)
        ListRow(
            title = stringResource(Res.string.sales_discounts_losses_link),
            trailingValue = sales.possibleLoss,
            trailingValueColor = colors.negativeText,
            showChevron = true,
            showDivider = false,
            onClick = onOpenLosses,
        )
    }
}

@Composable
private fun SalesComparisonUi.text(): String =
    when (period) {
        SalesPeriod.HOUR -> stringResource(Res.string.sales_vs_same_hour, delta, stringResource(previousWeekday.nameRes()))
        SalesPeriod.DAY -> stringResource(Res.string.sales_vs_last_weekday, delta, stringResource(previousWeekday.nameRes()))
        SalesPeriod.WEEK -> stringResource(Res.string.sales_vs_last_week, delta)
        SalesPeriod.MONTH -> stringResource(Res.string.sales_vs_previous_month, delta)
    }

@Composable
private fun SalesCachedMarker.text(): String {
    val time = time ?: return stringResource(if (offline) Res.string.sales_cached_unknown_offline else Res.string.sales_cached_unknown)
    return stringResource(if (offline) Res.string.sales_cached_offline else Res.string.sales_cached, time)
}

/** *21:00 to 22:00*, *5 Nov*, *2 to 8 Nov*, *28 Oct to 3 Nov*, *November 2026*. */
@Composable
private fun SalesRange.label(): String =
    when (period) {
        SalesPeriod.HOUR -> {
            val start = hour ?: return from.dayLabel()
            stringResource(Res.string.sales_range_hour, start.hourLabel(), ((start + 1) % HOURS_PER_DAY).hourLabel())
        }

        SalesPeriod.DAY -> {
            from.dayLabel()
        }

        SalesPeriod.WEEK -> {
            if (from.month == to.month) {
                stringResource(Res.string.sales_range_same_month, from.day, to.day, stringResource(to.month.shortNameRes()))
            } else {
                stringResource(
                    Res.string.sales_range_two_months,
                    from.day,
                    stringResource(from.month.shortNameRes()),
                    to.day,
                    stringResource(to.month.shortNameRes()),
                )
            }
        }

        SalesPeriod.MONTH -> {
            stringResource(Res.string.sales_range_month, stringResource(from.month.nameRes()), from.year)
        }
    }

@Composable
private fun LocalDate.dayLabel(): String = stringResource(Res.string.sales_range_day, day, stringResource(month.shortNameRes()))

private fun Int.hourLabel(): String = toString().padStart(2, '0') + ":00"

private const val HOURS_PER_DAY = 24

@Preview
@Composable
private fun SalesReadyPreview() {
    OneMindTheme { SalesScreen(state = SalesState(content = SalesContent.Ready(previewSales())), onAction = {}) }
}

@Preview
@Composable
private fun SalesWaitingPreview() {
    OneMindTheme {
        SalesScreen(
            state =
                SalesState(
                    content =
                        SalesContent.Ready(
                            previewSales().copy(
                                waitingForFirstSync = true,
                                revenue = null,
                                comparison = null,
                                guests = null,
                                averageBill = null,
                                perGuest = null,
                                tips = null,
                                payment = null,
                                possibleLoss = null,
                            ),
                        ),
                ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun SalesFailedPreview() {
    OneMindTheme { SalesScreen(state = SalesState(content = SalesContent.Failed(UiText.Dynamic("Network error"))), onAction = {}) }
}

private fun previewSales() =
    SalesUi(
        range = SalesRange(SalesPeriod.WEEK, LocalDate(2026, 11, 2), LocalDate(2026, 11, 8), hour = null, finished = false),
        waitingForFirstSync = false,
        pendingVenueCount = 0,
        revenue = "312 480 Kč",
        comparison = SalesComparisonUi("+8.1%", BadgeTone.Positive, SalesPeriod.WEEK, kotlinx.datetime.DayOfWeek.MONDAY),
        guests = "1 846",
        averageBill = "412 Kč",
        perGuest = "169 Kč",
        tips = "15 200 Kč",
        payment = PaymentSplitUi(68, "212 490 Kč", "99 990 Kč"),
        possibleLoss = "−22 410 Kč",
        dataGaps = listOf(SalesDataGapLine(SalesDataGapKind.OUTDATED, 3)),
        updatedAt = "21:38",
    )
