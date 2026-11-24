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
import com.onemind.sales.presentation.generated.resources.sales_detail_month_1
import com.onemind.sales.presentation.generated.resources.sales_detail_month_10
import com.onemind.sales.presentation.generated.resources.sales_detail_month_11
import com.onemind.sales.presentation.generated.resources.sales_detail_month_12
import com.onemind.sales.presentation.generated.resources.sales_detail_month_2
import com.onemind.sales.presentation.generated.resources.sales_detail_month_3
import com.onemind.sales.presentation.generated.resources.sales_detail_month_4
import com.onemind.sales.presentation.generated.resources.sales_detail_month_5
import com.onemind.sales.presentation.generated.resources.sales_detail_month_6
import com.onemind.sales.presentation.generated.resources.sales_detail_month_7
import com.onemind.sales.presentation.generated.resources.sales_detail_month_8
import com.onemind.sales.presentation.generated.resources.sales_detail_month_9
import com.onemind.sales.presentation.generated.resources.sales_detail_range
import com.onemind.sales.presentation.generated.resources.sales_detail_retry
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
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.rowGap),
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
    stringResource(Res.string.sales_detail_date, date.day, stringResource(monthNames[date.month.ordinal]))

/** "2 to 8 Nov" style range, or one date for a single day. */
@Composable
internal fun dateRange(
    from: LocalDate,
    to: LocalDate,
): String = if (from == to) shortDate(from) else stringResource(Res.string.sales_detail_range, shortDate(from), shortDate(to))

private val monthNames =
    listOf(
        Res.string.sales_detail_month_1,
        Res.string.sales_detail_month_2,
        Res.string.sales_detail_month_3,
        Res.string.sales_detail_month_4,
        Res.string.sales_detail_month_5,
        Res.string.sales_detail_month_6,
        Res.string.sales_detail_month_7,
        Res.string.sales_detail_month_8,
        Res.string.sales_detail_month_9,
        Res.string.sales_detail_month_10,
        Res.string.sales_detail_month_11,
        Res.string.sales_detail_month_12,
    )
