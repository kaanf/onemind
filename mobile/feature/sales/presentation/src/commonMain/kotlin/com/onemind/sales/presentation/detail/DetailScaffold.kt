package com.onemind.sales.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.onemind.core.designsystem.components.DetailTopBar
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_back
import com.onemind.sales.presentation.generated.resources.sales_detail_date
import com.onemind.sales.presentation.generated.resources.sales_detail_loading
import com.onemind.sales.presentation.generated.resources.sales_detail_retry
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_1
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_2
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_3
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_4
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_5
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_6
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_7
import com.onemind.sales.presentation.generated.resources.sales_detail_weekday_date
import com.onemind.sales.presentation.generated.resources.sales_range_same_month
import com.onemind.sales.presentation.generated.resources.sales_range_two_months
import com.onemind.sales.presentation.sales.shortNameRes
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** A Sales detail screen: back bar, then a pull-to-refresh column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = OneMindTheme.spacing
    Column(modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        DetailTopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            backContentDescription = stringResource(Res.string.sales_detail_back),
        )
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = spacing.screenHorizontal,
                            end = spacing.screenHorizontal,
                            top = spacing.textGap,
                            bottom = spacing.sectionGap,
                        ),
                content = content,
            )
        }
    }
}

@Composable
internal fun DetailLoading() {
    val description = stringResource(Res.string.sales_detail_loading)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            color = OneMindTheme.colors.accent,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
internal fun DetailMessage(
    message: String,
    isError: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    val colors = OneMindTheme.colors
    Text(text = message, style = OneMindTheme.typography.body, color = if (isError) colors.negativeText else colors.textSecondary)
    onRetry?.let { OutlineButton(text = stringResource(Res.string.sales_detail_retry), onClick = it) }
}

/** "5 Nov". */
@Composable
internal fun shortDate(date: LocalDate): String =
    stringResource(Res.string.sales_detail_date, date.day, stringResource(date.month.shortNameRes()))

/** "Thu, 5 Nov". */
@Composable
internal fun weekdayDate(date: LocalDate): String =
    stringResource(Res.string.sales_detail_weekday_date, stringResource(weekdayNames[date.dayOfWeek.ordinal]), shortDate(date))

/** "2 to 8 Nov", "30 Nov to 6 Dec", or one date for a single day: the same wording as 02 Sales. */
@Composable
internal fun dateRange(
    from: LocalDate,
    to: LocalDate,
): String =
    when {
        from == to -> {
            shortDate(from)
        }

        from.month == to.month -> {
            stringResource(Res.string.sales_range_same_month, from.day, to.day, stringResource(to.month.shortNameRes()))
        }

        else -> {
            stringResource(
                Res.string.sales_range_two_months,
                from.day,
                stringResource(from.month.shortNameRes()),
                to.day,
                stringResource(to.month.shortNameRes()),
            )
        }
    }

private val weekdayNames =
    listOf(
        Res.string.sales_detail_weekday_1,
        Res.string.sales_detail_weekday_2,
        Res.string.sales_detail_weekday_3,
        Res.string.sales_detail_weekday_4,
        Res.string.sales_detail_weekday_5,
        Res.string.sales_detail_weekday_6,
        Res.string.sales_detail_weekday_7,
    )
