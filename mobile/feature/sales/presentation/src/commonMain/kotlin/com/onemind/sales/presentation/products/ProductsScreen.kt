package com.onemind.sales.presentation.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.Bar
import com.onemind.core.designsystem.components.BarChart
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindFilterChip
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.presentation.detail.DetailLoading
import com.onemind.sales.presentation.detail.DetailMessage
import com.onemind.sales.presentation.detail.DetailScaffold
import com.onemind.sales.presentation.detail.shortDate
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_data_gaps
import com.onemind.sales.presentation.generated.resources.sales_detail_no_data_yet
import com.onemind.sales.presentation.generated.resources.sales_detail_no_venue
import com.onemind.sales.presentation.generated.resources.sales_detail_venues_syncing
import com.onemind.sales.presentation.generated.resources.sales_products_all
import com.onemind.sales.presentation.generated.resources.sales_products_caption
import com.onemind.sales.presentation.generated.resources.sales_products_chart_description
import com.onemind.sales.presentation.generated.resources.sales_products_chart_description_no_peak
import com.onemind.sales.presentation.generated.resources.sales_products_empty
import com.onemind.sales.presentation.generated.resources.sales_products_empty_category
import com.onemind.sales.presentation.generated.resources.sales_products_peak
import com.onemind.sales.presentation.generated.resources.sales_products_row_meta
import com.onemind.sales.presentation.generated.resources.sales_products_sold
import com.onemind.sales.presentation.generated.resources.sales_products_subtitle
import com.onemind.sales.presentation.generated.resources.sales_products_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProductsRoot(
    onBack: () -> Unit,
    viewModel: ProductsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductsScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun ProductsScreen(
    state: ProductsState,
    onAction: (ProductsAction) -> Unit,
    onBack: () -> Unit,
) {
    val ready = state.content as? ProductsContent.Ready
    DetailScaffold(
        title = stringResource(Res.string.sales_products_title),
        subtitle = ready?.products?.let { stringResource(Res.string.sales_products_subtitle, shortDate(it.businessDay)) },
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(ProductsAction.Refresh) },
    ) {
        when (val content = state.content) {
            ProductsContent.Loading -> {
                DetailLoading()
            }

            ProductsContent.NoVenue -> {
                DetailMessage(stringResource(Res.string.sales_detail_no_venue), isError = false)
            }

            is ProductsContent.Failed -> {
                DetailMessage(
                    content.message.asString(),
                    isError = true,
                    onRetry = { onAction(ProductsAction.Refresh) },
                )
            }

            is ProductsContent.Ready -> {
                ProductsContentColumn(state, content, onAction)
            }
        }
    }
}

@Composable
private fun ProductsContentColumn(
    state: ProductsState,
    content: ProductsContent.Ready,
    onAction: (ProductsAction) -> Unit,
) {
    val products = content.products
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    content.refreshError?.let { Text(text = it.asString(), style = type.caption, color = colors.negativeText) }
    if (products.pendingVenueCount > 0) {
        Text(
            text = pluralStringResource(Res.plurals.sales_detail_venues_syncing, products.pendingVenueCount, products.pendingVenueCount),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
    if (!products.hasData) {
        DetailMessage(stringResource(Res.string.sales_detail_no_data_yet), isError = false)
        return
    }
    if (products.products.isEmpty()) {
        DetailMessage(stringResource(Res.string.sales_products_empty), isError = false)
        return
    }
    Chips(products.chips, state.categoryId) { onAction(ProductsAction.SelectCategory(it)) }
    val selected = state.selectedProduct
    if (selected == null) {
        DetailMessage(stringResource(Res.string.sales_products_empty_category), isError = false)
    } else {
        Chart(products, selected)
        ProductList(state.visibleProducts, selected.key) { onAction(ProductsAction.SelectProduct(it)) }
    }
    if (products.hasDataGaps) {
        Text(text = stringResource(Res.string.sales_detail_data_gaps), style = type.caption, color = colors.warningText)
    }
}

@Composable
private fun Chips(
    chips: List<CategoryChip>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (chips.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        OneMindFilterChip(label = stringResource(Res.string.sales_products_all), selected = selected == null, onClick = { onSelect(null) })
        chips.forEach { chip ->
            OneMindFilterChip(label = chip.name, selected = selected == chip.id, onClick = { onSelect(chip.id) })
        }
    }
}

@Composable
private fun Chart(
    products: ProductsUi,
    product: ProductRow,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val bars = products.bars(product)
    OneMindCard {
        Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap)) {
            Text(text = product.name, style = type.title, color = colors.textPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(Res.string.sales_products_sold, product.sold), style = type.body, color = colors.textSecondary)
                product.peakHour?.let {
                    DeltaBadge(
                        text = stringResource(Res.string.sales_products_peak, it.hourLabel()),
                        tone = BadgeTone.Neutral,
                    )
                }
            }
            val description =
                product.peakHour?.let { stringResource(Res.string.sales_products_chart_description, product.name, it.hourLabel()) }
                    ?: stringResource(Res.string.sales_products_chart_description_no_peak, product.name)
            BarChart(
                bars =
                    bars.map {
                        Bar(
                            value = it.heightFraction,
                            color = if (it.isPeak) colors.accent else colors.chartMutedStrong,
                            valueLabel = it.quantity,
                        )
                    },
                contentDescription = description,
            )
            HourAxis(products.hours)
            Text(text = stringResource(Res.string.sales_products_caption), style = type.caption, color = colors.textSecondary)
        }
    }
}

/** First, middle and last hour under the chart (16:00 · 21:00 · 02:00). */
@Composable
private fun HourAxis(hours: List<Int>) {
    if (hours.isEmpty()) return
    val labels = listOf(hours.first(), hours[hours.size / 2], hours.last()).distinct()
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        labels.forEach { Text(text = it.hourLabel(), style = OneMindTheme.typography.chartLabel, color = OneMindTheme.colors.textTertiary) }
    }
}

@Composable
private fun ProductList(
    rows: List<ProductRow>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    OneMindListCard {
        rows.forEachIndexed { index, row ->
            val sold = stringResource(Res.string.sales_products_sold, row.sold)
            val prefix = listOfNotNull(row.venueName, row.categoryName).joinToString(" · ")
            val meta = if (prefix.isEmpty()) sold else stringResource(Res.string.sales_products_row_meta, prefix, sold)
            ListRow(
                title = row.name,
                meta = meta,
                trailingValue = row.gross,
                trailingValueColor = if (row.key == selectedKey) OneMindTheme.colors.accent else OneMindTheme.colors.textPrimary,
                showDivider = index < rows.lastIndex,
                onClick = { onSelect(row.key) },
            )
        }
    }
}

private fun Int.hourLabel(): String = toString().padStart(2, '0') + ":00"

@Preview
@Composable
private fun ProductsReadyPreview() {
    OneMindTheme { ProductsScreen(ProductsState(content = ProductsContent.Ready(ProductsPreviewData.ready)), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ProductsNoDataPreview() {
    OneMindTheme {
        ProductsScreen(
            ProductsState(content = ProductsContent.Ready(ProductsPreviewData.ready.copy(hasData = false))),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ProductsErrorPreview() {
    OneMindTheme {
        ProductsScreen(ProductsState(content = ProductsContent.Failed(UiText.Dynamic("Something went wrong."))), onAction = {}, onBack = {})
    }
}
