package com.onemind.today.presentation.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.AccountButton
import com.onemind.core.designsystem.components.Bar
import com.onemind.core.designsystem.components.BarChart
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.core.designsystem.components.StatTile
import com.onemind.core.designsystem.components.VenueButton
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindColors
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.today.domain.HourKind
import com.onemind.today.presentation.generated.resources.Res
import com.onemind.today.presentation.generated.resources.today_account_description
import com.onemind.today.presentation.generated.resources.today_all_venues
import com.onemind.today.presentation.generated.resources.today_data_gaps_lead
import com.onemind.today.presentation.generated.resources.today_date_time
import com.onemind.today.presentation.generated.resources.today_guests
import com.onemind.today.presentation.generated.resources.today_hourly_description
import com.onemind.today.presentation.generated.resources.today_hourly_description_no_peak
import com.onemind.today.presentation.generated.resources.today_loading
import com.onemind.today.presentation.generated.resources.today_loss_includes
import com.onemind.today.presentation.generated.resources.today_loss_includes_and_other
import com.onemind.today.presentation.generated.resources.today_loss_includes_other_only
import com.onemind.today.presentation.generated.resources.today_no_venue
import com.onemind.today.presentation.generated.resources.today_not_available_from_pos
import com.onemind.today.presentation.generated.resources.today_occupancy
import com.onemind.today.presentation.generated.resources.today_open_tabs
import com.onemind.today.presentation.generated.resources.today_paid_so_far
import com.onemind.today.presentation.generated.resources.today_per_guest
import com.onemind.today.presentation.generated.resources.today_possible_loss
import com.onemind.today.presentation.generated.resources.today_retry
import com.onemind.today.presentation.generated.resources.today_tips
import com.onemind.today.presentation.generated.resources.today_title
import com.onemind.today.presentation.generated.resources.today_updated_at
import com.onemind.today.presentation.generated.resources.today_value_empty
import com.onemind.today.presentation.generated.resources.today_venue_description
import com.onemind.today.presentation.generated.resources.today_venues_syncing
import com.onemind.today.presentation.generated.resources.today_vs_last_weekday
import com.onemind.today.presentation.generated.resources.today_waiting_first_sync
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoot(viewModel: TodayViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
) {
    val title = stringResource(Res.string.today_title)
    when (val content = state.content) {
        TodayContent.Loading -> {
            val description = stringResource(Res.string.today_loading)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background),
            ) {
                CircularProgressIndicator(
                    color = OneMindTheme.colors.accent,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }

        TodayContent.NoVenue -> {
            PlaceholderScreen(title = title, message = stringResource(Res.string.today_no_venue))
        }

        is TodayContent.Failed -> {
            Refreshable(state, onAction) {
                HeaderRow(state.header)
                ScreenTitle(title = title, dateLine = null)
                Text(text = content.message.asString(), style = OneMindTheme.typography.body, color = OneMindTheme.colors.negativeText)
                OutlineButton(text = stringResource(Res.string.today_retry), onClick = { onAction(TodayAction.Refresh) })
            }
        }

        is TodayContent.Ready -> {
            Refreshable(state, onAction) {
                HeaderRow(state.header)
                TodayContentColumn(title = title, today = content.today, refreshError = content.refreshError)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = OneMindTheme.spacing
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(TodayAction.Refresh) },
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

@Composable
private fun TodayContentColumn(
    title: String,
    today: TodayUi,
    refreshError: UiText?,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    ScreenTitle(title = title, dateLine = today.dateLine())
    if (refreshError != null) {
        Text(text = refreshError.asString(), style = type.caption, color = colors.negativeText)
    }
    if (today.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.today_venues_syncing, today.pendingVenueCount, today.pendingVenueCount),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
    RevenueCard(today)
    LiveFigures(today)
    PossibleLossRow(today)
    if (today.dataGaps.isNotEmpty()) {
        WarningCard(
            lead = stringResource(Res.string.today_data_gaps_lead),
            body = today.dataGaps.map { it.text() }.joinToString(" "),
        )
    }
    today.updatedAt?.let {
        Text(text = stringResource(Res.string.today_updated_at, it), style = type.caption, color = colors.textSecondary)
    }
}

/** Venue button (inert until the switcher, P7) and account initials; each is drawn only when known. */
@Composable
private fun HeaderRow(header: TodayHeader) {
    if (header.scopeName == null && header.initials == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        header.scopeName?.let { scope ->
            val name =
                when (scope) {
                    is ScopeName.Venue -> scope.name
                    ScopeName.AllVenues -> stringResource(Res.string.today_all_venues)
                }
            VenueButton(name = name, contentDescription = stringResource(Res.string.today_venue_description, name))
        }
        Spacer(Modifier.weight(1f))
        header.initials?.let { AccountButton(initials = it, contentDescription = stringResource(Res.string.today_account_description)) }
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    dateLine: String?,
) {
    Column {
        dateLine?.let { Text(text = it, style = OneMindTheme.typography.bodySmall, color = OneMindTheme.colors.textSecondary) }
        Text(
            text = title,
            style = OneMindTheme.typography.title,
            color = OneMindTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun TodayUi.dateLine(): String? {
    val day = businessDay ?: return null
    val time = clockTime ?: return null
    return stringResource(
        Res.string.today_date_time,
        stringResource(day.dayOfWeek.nameRes()),
        day.day,
        stringResource(day.month.nameRes()),
        time,
    )
}

@Composable
private fun RevenueCard(today: TodayUi) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.today_value_empty)
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
                Text(text = stringResource(Res.string.today_paid_so_far).uppercase(), style = type.overline, color = colors.textSecondary)
                Text(text = today.paidSoFar ?: empty, style = type.displayLarge, color = colors.textPrimary)
                today.comparison?.let { comparison ->
                    DeltaBadge(
                        text =
                            stringResource(
                                Res.string.today_vs_last_weekday,
                                comparison.delta,
                                stringResource(comparison.previousWeekday.nameRes()),
                            ),
                        tone = comparison.tone,
                    )
                }
                if (today.waitingForFirstSync) {
                    Text(text = stringResource(Res.string.today_waiting_first_sync), style = type.bodySmall, color = colors.textSecondary)
                }
            }
            today.hourly?.let { HourlyChart(it) }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight)) {
                HorizontalDivider(thickness = spacing.borderWidth, color = colors.divider)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
                    if (today.openTabsUnavailable) {
                        CardFigure(label = stringResource(Res.string.today_open_tabs), value = null, modifier = Modifier.weight(1f))
                    }
                    CardFigure(label = stringResource(Res.string.today_tips), value = today.tips ?: empty, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Caption label + 17/600 value inside the revenue card; a `null` value is the "not available" state. */
@Composable
private fun CardFigure(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    Column(modifier = modifier) {
        Text(text = label, style = type.caption, color = colors.textSecondary)
        if (value == null) {
            Text(text = stringResource(Res.string.today_not_available_from_pos), style = type.bodySmall, color = colors.textSecondary)
        } else {
            Text(text = value, style = type.headline, color = colors.textPrimary)
        }
    }
}

@Composable
private fun HourlyChart(hourly: HourlyUi) {
    val colors = OneMindTheme.colors
    val description =
        hourly.peakHour?.let { stringResource(Res.string.today_hourly_description, it.hourLabel()) }
            ?: stringResource(Res.string.today_hourly_description_no_peak)
    BarChart(bars = hourly.chartBars(colors), contentDescription = description)
}

/** The bars [BarChart] draws: past `chartMutedStrong`, current `accent`, forecast `chartFuture`; no height → empty slot. */
internal fun HourlyUi.chartBars(colors: OneMindColors): List<Bar> =
    bars.map { bar ->
        Bar(
            value = bar.heightFraction,
            color =
                when (bar.kind) {
                    HourKind.PAST -> colors.chartMutedStrong
                    HourKind.CURRENT -> colors.accent
                    HourKind.FORECAST, HourKind.UNKNOWN -> colors.chartFuture
                },
        )
    }

@Composable
private fun LiveFigures(today: TodayUi) {
    val spacing = OneMindTheme.spacing
    val empty = stringResource(Res.string.today_value_empty)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
        if (today.occupancyUnavailable) {
            // 01a Tables (This week) opens from here once it is built.
            FigureTile(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.today_occupancy),
                    style = OneMindTheme.typography.caption,
                    color = OneMindTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(Res.string.today_not_available_from_pos),
                    style = OneMindTheme.typography.caption,
                    color = OneMindTheme.colors.textSecondary,
                )
            }
        }
        FigureTile(modifier = Modifier.weight(1f)) {
            StatTile(label = stringResource(Res.string.today_guests), value = today.guests ?: empty)
        }
        FigureTile(modifier = Modifier.weight(1f)) {
            StatTile(label = stringResource(Res.string.today_per_guest), value = today.perGuest ?: empty)
        }
    }
}

/** A live-figure tile: `surface`, 1 px `border`, panel radius (canvas: 14). */
@Composable
private fun FigureTile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(OneMindTheme.radius.panel)
    Column(
        modifier =
            modifier
                .background(OneMindTheme.colors.surface, shape)
                .border(OneMindTheme.spacing.borderWidth, OneMindTheme.colors.border, shape)
                .padding(OneMindTheme.spacing.rowGap),
    ) {
        content()
    }
}

@Composable
private fun PossibleLossRow(today: TodayUi) {
    val colors = OneMindTheme.colors
    val loss = today.possibleLoss
    val named =
        loss
            ?.includes
            ?.map { stringResource(it.nameRes()) }
            ?.joinToString(", ")
            .orEmpty()
    val meta =
        when {
            loss == null -> null
            loss.includesOther && named.isEmpty() -> stringResource(Res.string.today_loss_includes_other_only)
            loss.includesOther -> stringResource(Res.string.today_loss_includes_and_other, named)
            named.isNotEmpty() -> stringResource(Res.string.today_loss_includes, named)
            else -> null
        }
    // 02c Discounts & losses opens from here once it is built.
    OneMindListCard {
        ListRow(
            title = stringResource(Res.string.today_possible_loss),
            meta = meta,
            leading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(OneMindTheme.spacing.iconBoxSize)
                            .background(colors.negativeSurface, RoundedCornerShape(OneMindTheme.radius.iconBox)),
                ) {
                    Icon(
                        imageVector = OneMindIcons.Warning,
                        contentDescription = null,
                        tint = colors.negativeText,
                        modifier = Modifier.size(OneMindTheme.spacing.iconBoxIconSize),
                    )
                }
            },
            trailingValue = loss?.total ?: stringResource(Res.string.today_value_empty),
            trailingValueColor = if (loss != null) colors.negativeText else colors.textSecondary,
            showDivider = false,
        )
    }
}

@Composable
private fun DataGapLine.text(): String = pluralStringResource(kind.pluralRes(), count, count)

private fun Int.hourLabel(): String = toString().padStart(2, '0') + ":00"

@Preview
@Composable
private fun TodayReadyPreview() {
    OneMindTheme { TodayScreen(TodayState(TodayContent.Ready(TodayPreviewData.ready), header = TodayPreviewData.header), onAction = {}) }
}

@Preview
@Composable
private fun TodayWaitingForFirstSyncPreview() {
    OneMindTheme { TodayScreen(TodayState(TodayContent.Ready(TodayPreviewData.waitingForFirstSync)), onAction = {}) }
}

@Preview
@Composable
private fun TodayDataGapsPreview() {
    OneMindTheme {
        TodayScreen(
            TodayState(TodayContent.Ready(TodayPreviewData.withGaps, refreshError = UiText.Dynamic("No internet connection."))),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun TodayNoVenuePreview() {
    OneMindTheme { TodayScreen(TodayState(TodayContent.NoVenue), onAction = {}) }
}

@Preview
@Composable
private fun TodayFailedPreview() {
    OneMindTheme { TodayScreen(TodayState(TodayContent.Failed(UiText.Dynamic("No internet connection."))), onAction = {}) }
}
