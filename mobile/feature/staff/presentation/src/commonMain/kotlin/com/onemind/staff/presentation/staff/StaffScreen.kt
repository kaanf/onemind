package com.onemind.staff.presentation.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.AvatarInitials
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.core.designsystem.components.StatTile
import com.onemind.core.designsystem.components.StatTileStyle
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.staff.presentation.generated.resources.Res
import com.onemind.staff.presentation.generated.resources.staff_data_gaps
import com.onemind.staff.presentation.generated.resources.staff_empty_day
import com.onemind.staff.presentation.generated.resources.staff_guests
import com.onemind.staff.presentation.generated.resources.staff_list_columns
import com.onemind.staff.presentation.generated.resources.staff_list_title
import com.onemind.staff.presentation.generated.resources.staff_loading
import com.onemind.staff.presentation.generated.resources.staff_meta_bills
import com.onemind.staff.presentation.generated.resources.staff_meta_deleted
import com.onemind.staff.presentation.generated.resources.staff_meta_guests
import com.onemind.staff.presentation.generated.resources.staff_no_venue
import com.onemind.staff.presentation.generated.resources.staff_retry
import com.onemind.staff.presentation.generated.resources.staff_sales
import com.onemind.staff.presentation.generated.resources.staff_subtitle
import com.onemind.staff.presentation.generated.resources.staff_tips
import com.onemind.staff.presentation.generated.resources.staff_title
import com.onemind.staff.presentation.generated.resources.staff_unnamed
import com.onemind.staff.presentation.generated.resources.staff_venues_syncing
import com.onemind.staff.presentation.generated.resources.staff_waiting_first_sync
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StaffRoot(viewModel: StaffViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StaffScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun StaffScreen(
    state: StaffState,
    onAction: (StaffAction) -> Unit,
) {
    val title = stringResource(Res.string.staff_title)
    when (val content = state.content) {
        StaffContent.NoVenue -> {
            PlaceholderScreen(title = title, message = stringResource(Res.string.staff_no_venue))
        }

        else -> {
            Refreshable(state, onAction) {
                Header(title)
                when (content) {
                    StaffContent.Loading -> {
                        // The loaded layout with skeleton values, so nothing moves when the figures arrive.
                        val description = stringResource(Res.string.staff_loading)
                        Column(modifier = Modifier.semantics { contentDescription = description }) {
                            Totals(null)
                        }
                    }

                    is StaffContent.Failed -> {
                        Text(
                            text = content.message.asString(),
                            style = OneMindTheme.typography.body,
                            color = OneMindTheme.colors.negativeText,
                        )
                        OutlineButton(text = stringResource(Res.string.staff_retry), onClick = { onAction(StaffAction.Refresh) })
                    }

                    is StaffContent.Ready -> {
                        ReadyContent(content)
                    }

                    StaffContent.NoVenue -> {
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
    state: StaffState,
    onAction: (StaffAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = OneMindTheme.spacing
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(StaffAction.Refresh) },
        modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Canvas AltStaff: 20 at the top and sides, 16 between sections.
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
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

@Composable
private fun Header(title: String) {
    Column {
        Text(
            text = title,
            style = OneMindTheme.typography.title,
            color = OneMindTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.staff_subtitle),
            style = OneMindTheme.typography.bodySmall,
            color = OneMindTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ReadyContent(content: StaffContent.Ready) {
    val staff = content.staff
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    content.refreshError?.let { Text(text = it.asString(), style = type.bodySmall, color = colors.negativeText) }
    if (!staff.hasData) {
        Text(text = stringResource(Res.string.staff_waiting_first_sync), style = type.body, color = colors.textSecondary)
        return
    }
    if (staff.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.staff_venues_syncing, staff.pendingVenueCount, staff.pendingVenueCount),
            style = type.bodySmall,
            color = colors.textSecondary,
        )
    }
    Totals(staff.totals)
    StaffList(staff.staff)
    if (staff.hasDataGaps) {
        Text(text = stringResource(Res.string.staff_data_gaps), style = type.bodySmall, color = colors.warningText)
    }
}

/** SALES · TIPS · GUESTS; `null` draws skeleton values. Tile-size values, so a five-figure total fits a third. */
@Composable
private fun Totals(totals: StaffTotalsUi?) {
    OneMindCard(contentPadding = PaddingValues(OneMindTheme.spacing.cardPaddingCompact)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap), modifier = Modifier.fillMaxWidth()) {
            StatTile(stringResource(Res.string.staff_sales).uppercase(), totals?.sales, Modifier.weight(1f), StatTileStyle.Tile)
            StatTile(stringResource(Res.string.staff_tips).uppercase(), totals?.tips, Modifier.weight(1f), StatTileStyle.Tile)
            StatTile(stringResource(Res.string.staff_guests).uppercase(), totals?.guests, Modifier.weight(1f), StatTileStyle.Tile)
        }
    }
}

@Composable
private fun StaffList(rows: List<StaffRow>) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.staff_list_title),
                style = type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(text = stringResource(Res.string.staff_list_columns), style = type.caption, color = colors.textSecondary)
        }
        if (rows.isEmpty()) {
            Text(text = stringResource(Res.string.staff_empty_day), style = type.body, color = colors.textSecondary)
        } else {
            OneMindListCard {
                rows.forEachIndexed { index, row ->
                    ListRow(
                        title = row.name ?: stringResource(Res.string.staff_unnamed),
                        meta = row.meta(),
                        leading = { AvatarInitials(row.initials) },
                        trailingValue = row.sales,
                        trailingMeta = row.tips,
                        showDivider = index < rows.lastIndex,
                        minHeight = OneMindTheme.spacing.rowHeightTall,
                    )
                }
            }
        }
    }
}

/** "82 guests · 31 bills", plus "· 2 deleted" when there are any. */
@Composable
private fun StaffRow.meta(): String {
    val parts =
        listOfNotNull(
            pluralStringResource(Res.plurals.staff_meta_guests, guests, guests),
            pluralStringResource(Res.plurals.staff_meta_bills, billCount, billCount),
            if (deletedBillCount > 0) pluralStringResource(Res.plurals.staff_meta_deleted, deletedBillCount, deletedBillCount) else null,
        )
    return parts.joinToString(" · ")
}

@Preview
@Composable
private fun StaffReadyPreview() {
    OneMindTheme { StaffScreen(state = StaffState(content = StaffContent.Ready(previewStaff())), onAction = {}) }
}

@Preview
@Composable
private fun StaffEmptyPreview() {
    OneMindTheme {
        StaffScreen(
            state =
                StaffState(
                    content = StaffContent.Ready(previewStaff().copy(totals = StaffTotalsUi("0 Kč", "0 Kč", "0"), staff = emptyList())),
                ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun StaffFailedPreview() {
    OneMindTheme { StaffScreen(state = StaffState(content = StaffContent.Failed(UiText.Dynamic("Network error"))), onAction = {}) }
}

private fun previewStaff() =
    StaffUi(
        hasData = true,
        pendingVenueCount = 0,
        totals = StaffTotalsUi("48 320 Kč", "3 940 Kč", "286"),
        staff =
            listOf(
                StaffRow("1", "Tomáš Novák", "TN", 82, 31, 0, "14 820 Kč", "1 210 Kč"),
                StaffRow("2", "Eliška Petrová", "EP", 74, 28, 2, "12 460 Kč", "1 080 Kč"),
                StaffRow("3", null, "?", 12, 4, 0, "2 100 Kč", "0 Kč"),
            ),
        hasDataGaps = false,
    )
