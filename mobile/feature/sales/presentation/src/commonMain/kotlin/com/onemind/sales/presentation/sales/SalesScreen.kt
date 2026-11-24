package com.onemind.sales.presentation.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OneMindLoadingSlot
import com.onemind.core.designsystem.components.OneMindSkeleton
import com.onemind.core.designsystem.components.OneMindSkeletonText
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.core.designsystem.components.SegmentedTabs
import com.onemind.core.designsystem.components.StatTile
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRange
import com.onemind.sales.presentation.generated.resources.Res
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
import com.onemind.sales.presentation.generated.resources.sales_range_today
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
                Header(title = title, range = (content as? SalesContent.Ready)?.sales?.range)
                PeriodTabs(state.period, onAction)
                when (content) {
                    SalesContent.Loading -> {
                        // The loaded layout with skeleton value slots, so nothing moves when the figures arrive.
                        val description = stringResource(Res.string.sales_loading)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.sectionGapTight),
                            modifier = Modifier.semantics { contentDescription = description },
                        ) {
                            CompositionLocalProvider(LocalLoading provides true) {
                                SalesContentColumn(
                                    SalesContent.Ready(loadingSales(state.period)),
                                    onOpenProductsByHour = {},
                                    onOpenLosses = {},
                                )
                            }
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
        // Canvas AltSales: 20 at the top and sides, 14 between sections.
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.cardPadding,
                        bottom = spacing.rowGap,
                    ),
        ) {
            content()
        }
    }
}

/** Title on the left, the period's range in a pill on the right (*2 to 8 Nov*). */
@Composable
private fun Header(
    title: String,
    range: SalesRange?,
) {
    val loading = LocalLoading.current
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    // As high as the range pill, so the content below doesn't move when the pill appears.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.minTouchTarget - spacing.tileGap / 2),
    ) {
        Text(
            text = title,
            style = OneMindTheme.typography.title,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (range == null && loading) {
            OneMindSkeleton(
                modifier = Modifier.size(SKELETON_PILL_WIDTH, spacing.minTouchTarget - spacing.tileGap / 2),
                shape = RoundedCornerShape(OneMindTheme.radius.chip),
            )
        }
        range?.let {
            // "Choose dates" in the canvas; picking a date isn't built yet, so the pill only shows the range.
            val shape = RoundedCornerShape(OneMindTheme.radius.chip)
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.tileGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .heightIn(min = spacing.minTouchTarget - spacing.tileGap / 2)
                        .background(colors.surface, shape)
                        .border(spacing.borderWidth, colors.borderStrong, shape)
                        .padding(horizontal = spacing.rowGap),
            ) {
                Icon(
                    imageVector = OneMindIcons.Calendar,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(spacing.chevronSize + spacing.listGap),
                )
                Text(text = it.label(), style = OneMindTheme.typography.label, color = colors.textPrimary)
            }
        }
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
    // The age of cached figures takes the Updated line's place at the bottom (nothing above moves).
    val cached = content.cached
    if (cached != null) {
        Text(text = cached.text(), style = type.caption, color = if (cached.offline) colors.warningText else colors.textSecondary)
    } else {
        sales.updatedAt?.let {
            Text(text = stringResource(Res.string.sales_updated_at, it), style = type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RevenueCard(sales: SalesUi) {
    val loading = LocalLoading.current
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.sales_value_empty)
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.heroGap)) {
                Text(text = stringResource(Res.string.sales_revenue).uppercase(), style = type.overline, color = colors.textSecondary)
                OneMindLoadingSlot(
                    value = sales.revenue ?: empty.takeUnless { loading },
                    skeleton = { OneMindSkeletonText(type.display, width = SKELETON_HERO) },
                ) { Text(text = it, style = type.display, color = colors.textPrimary) }
                if (loading) {
                    OneMindSkeletonText(
                        type.caption,
                        width = SKELETON_BADGE,
                        modifier = Modifier.padding(vertical = BADGE_PADDING),
                    )
                }
                sales.comparison?.let { DeltaBadge(text = it.text(), tone = it.tone) }
                if (sales.waitingForFirstSync) {
                    Text(text = stringResource(Res.string.sales_waiting_first_sync), style = type.bodySmall, color = colors.textSecondary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight)) {
                HorizontalDivider(thickness = spacing.borderWidth, color = colors.divider)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
                    SalesStat(
                        label = stringResource(Res.string.sales_guests),
                        value = sales.guests ?: empty,
                        modifier = Modifier.weight(1f),
                    )
                    SalesStat(
                        label = stringResource(Res.string.sales_average_bill),
                        value = sales.averageBill ?: empty,
                        modifier = Modifier.weight(1f),
                    )
                    SalesStat(
                        label = stringResource(Res.string.sales_per_guest),
                        value = sales.perGuest ?: empty,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** *By payment*: title and tips total, the card/cash share bar, then each side with its swatch and amount. */
@Composable
private fun PaymentCard(sales: SalesUi) {
    val loading = LocalLoading.current
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.sales_value_empty)
    OneMindCard(contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.cardPaddingTight)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.sales_by_payment),
                    style = type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    OneMindSkeletonText(type.bodySmall, width = SKELETON_VALUE)
                } else {
                    Text(
                        text = stringResource(Res.string.sales_tips, sales.tips ?: empty),
                        style = type.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            val payment = sales.payment
            if (loading) {
                OneMindSkeleton(
                    modifier = Modifier.fillMaxWidth().height(spacing.shareBarHeight),
                    shape = RoundedCornerShape(OneMindTheme.radius.shareBar),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.rowGap), modifier = Modifier.fillMaxWidth()) {
                    repeat(2) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap), modifier = Modifier.weight(1f)) {
                            OneMindSkeletonText(type.caption, width = SKELETON_VALUE)
                            OneMindSkeletonText(type.bodyLarge, width = SKELETON_VALUE)
                        }
                    }
                }
            } else if (payment == null) {
                Text(
                    text = if (sales.waitingForFirstSync) empty else stringResource(Res.string.sales_no_card_or_cash),
                    style = type.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                val description = stringResource(Res.string.sales_payment_bar_description, payment.cardPercent, payment.cashPercent)
                val segment = RoundedCornerShape(OneMindTheme.radius.shareBar)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.shareBarGap),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(spacing.shareBarHeight)
                            .semantics { contentDescription = description },
                ) {
                    if (payment.cardPercent > 0) {
                        Box(Modifier.weight(payment.cardPercent.toFloat()).fillMaxSize().background(colors.accent, segment))
                    }
                    if (payment.cashPercent > 0) {
                        Box(Modifier.weight(payment.cashPercent.toFloat()).fillMaxSize().background(colors.chartMuted, segment))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.rowGap), modifier = Modifier.fillMaxWidth()) {
                    PaymentShare(
                        label = stringResource(Res.string.sales_card_share, payment.cardPercent),
                        value = payment.card,
                        swatch = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    PaymentShare(
                        label = stringResource(Res.string.sales_cash_share, payment.cashPercent),
                        value = payment.cash,
                        swatch = colors.chartMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** An 8 px swatch and *Card · 68%* over the amount (16/600). */
@Composable
private fun PaymentShare(
    label: String,
    value: String,
    swatch: Color,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.heroGap)) {
            Box(Modifier.size(spacing.swatchSmall).background(swatch, RoundedCornerShape(OneMindTheme.radius.swatchSmall)))
            Text(text = label, style = OneMindTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
        }
        Text(
            text = value,
            style = OneMindTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** Entry points to 02a and 02c (02b Hourly patterns isn't built yet, so its row is left out). */
@Composable
private fun Links(
    sales: SalesUi,
    onOpenProductsByHour: () -> Unit,
    onOpenLosses: () -> Unit,
) {
    val colors = OneMindTheme.colors
    val rowHeight = OneMindTheme.spacing.rowHeightLink
    OneMindListCard {
        ListRow(
            title = stringResource(Res.string.sales_products_by_hour_link),
            showChevron = true,
            minHeight = rowHeight,
            onClick = onOpenProductsByHour,
        )
        ListRow(
            title = stringResource(Res.string.sales_discounts_losses_link),
            trailingValue = sales.possibleLoss,
            trailingValueColor = colors.textSecondary,
            trailingValueWeight = FontWeight.Normal,
            showChevron = true,
            showDivider = false,
            minHeight = rowHeight,
            onClick = onOpenLosses,
        )
    }
}

/** *+8.1% vs last week*, *+3.6% vs October*, *+12.4% vs last Friday*, *+9.2% vs last Fri, same hour*. */
@Composable
private fun SalesComparisonUi.text(): String =
    when (period) {
        SalesPeriod.HOUR -> stringResource(Res.string.sales_vs_same_hour, delta, stringResource(previousWeekday.shortNameRes()))
        SalesPeriod.DAY -> stringResource(Res.string.sales_vs_last_weekday, delta, stringResource(previousWeekday.nameRes()))
        SalesPeriod.WEEK -> stringResource(Res.string.sales_vs_last_week, delta)
        SalesPeriod.MONTH -> stringResource(Res.string.sales_vs_previous_month, delta, stringResource(previousMonth.nameRes()))
    }

@Composable
private fun SalesCachedMarker.text(): String {
    val time = time ?: return stringResource(if (offline) Res.string.sales_cached_unknown_offline else Res.string.sales_cached_unknown)
    return stringResource(if (offline) Res.string.sales_cached_offline else Res.string.sales_cached, time)
}

/** *21:00 to 22:00*, *Today*, *2 to 8 Nov*, *28 Oct to 3 Nov*, *November*. */
@Composable
private fun SalesRange.label(): String =
    when (period) {
        SalesPeriod.HOUR -> {
            val start = hour ?: return from.dayLabel()
            stringResource(Res.string.sales_range_hour, start.hourLabel(), ((start + 1) % HOURS_PER_DAY).hourLabel())
        }

        // No date is chosen yet (Choose dates), so the day is always the current business day.
        SalesPeriod.DAY -> {
            stringResource(Res.string.sales_range_today)
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
            stringResource(Res.string.sales_range_month, stringResource(from.month.nameRes()))
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
        comparison =
            SalesComparisonUi(
                "+8.1%",
                BadgeTone.Positive,
                SalesPeriod.WEEK,
                kotlinx.datetime.DayOfWeek.MONDAY,
                kotlinx.datetime.Month.OCTOBER,
            ),
        guests = "1 846",
        averageBill = "412 Kč",
        perGuest = "169 Kč",
        tips = "15 200 Kč",
        payment = PaymentSplitUi(68, "212 490 Kč", "99 990 Kč"),
        possibleLoss = "−22 410 Kč",
        dataGaps = listOf(SalesDataGapLine(SalesDataGapKind.OUTDATED, 3)),
        updatedAt = "21:38",
    )

/** Loading draws the loaded layout with skeleton value slots (DESIGN_SYSTEM.md, Motion and loading). */
private val LocalLoading = staticCompositionLocalOf { false }

/** Sales while loading: no figure known; the range is never drawn (the header has none while loading). */
private fun loadingSales(period: SalesPeriod) =
    SalesUi(
        range = SalesRange(period, LocalDate.fromEpochDays(0), LocalDate.fromEpochDays(0), hour = null, finished = false),
        waitingForFirstSync = false,
        pendingVenueCount = 0,
        revenue = null,
        comparison = null,
        guests = null,
        averageBill = null,
        perGuest = null,
        tips = null,
        payment = null,
        possibleLoss = null,
        dataGaps = emptyList(),
        updatedAt = null,
    )

/** A stat (12 + 16/600) whose value crossfades in from its skeleton. */
@Composable
private fun SalesStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    OneMindLoadingSlot(
        value = value.takeUnless { LocalLoading.current },
        modifier = modifier,
        skeleton = {
            Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.hairlineGap)) {
                Text(text = label, style = OneMindTheme.typography.caption, color = OneMindTheme.colors.textSecondary, maxLines = 1)
                OneMindSkeletonText(OneMindTheme.typography.bodyLarge, width = SKELETON_VALUE)
            }
        },
    ) { StatTile(label = label, value = it) }
}

private val SKELETON_PILL_WIDTH = 120.dp
private val SKELETON_HERO = 200.dp
private val SKELETON_BADGE = 140.dp
private val SKELETON_VALUE = 72.dp
private val BADGE_PADDING = 3.dp
