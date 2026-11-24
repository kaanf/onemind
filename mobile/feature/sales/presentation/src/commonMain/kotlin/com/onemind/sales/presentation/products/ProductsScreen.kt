package com.onemind.sales.presentation.products

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.Bar
import com.onemind.core.designsystem.components.BarChart
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindFilterChip
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.presentation.detail.DetailLoading
import com.onemind.sales.presentation.detail.DetailMessage
import com.onemind.sales.presentation.detail.DetailScaffold
import com.onemind.sales.presentation.detail.weekdayDate
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
        subtitle = ready?.products?.let { stringResource(Res.string.sales_products_subtitle, weekdayDate(it.businessDay)) },
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

/** Category chips, scrolling edge to edge under the screen gutter as on the canvas. */
@Composable
private fun Chips(
    chips: List<CategoryChip>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (chips.isEmpty()) return
    val gutter = OneMindTheme.spacing.screenHorizontal
    Row(
        horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap),
        modifier =
            Modifier
                .bleed(gutter)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = gutter),
    ) {
        OneMindFilterChip(label = stringResource(Res.string.sales_products_all), selected = selected == null, onClick = { onSelect(null) })
        chips.forEach { chip ->
            OneMindFilterChip(label = chip.name, selected = selected == chip.id, onClick = { onSelect(chip.id) })
        }
    }
}

/** Widens a child of a padded column by [gutter] on both sides (the canvas's `margin: 0 -20px`). */
private fun Modifier.bleed(gutter: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = (gutter * 2).roundToPx()
        val placeable =
            measurable.measure(
                constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra),
            )
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }

/** Sold-by-hour card: product name as overline, *n sold* figure, Peak tag, bars, hour axis, caption. */
@Composable
private fun Chart(
    products: ProductsUi,
    product: ProductRow,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val bars = products.bars(product)
    OneMindCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.cardPadding, vertical = spacing.cardPaddingTight),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.listGap), modifier = Modifier.weight(1f)) {
                    Text(text = product.name.uppercase(), style = type.overline, color = colors.textSecondary, maxLines = 1)
                    Text(
                        text = stringResource(Res.string.sales_products_sold, product.sold),
                        style = type.displaySmall,
                        color = colors.textPrimary,
                    )
                }
                product.peakHour?.let {
                    DeltaBadge(
                        text = stringResource(Res.string.sales_products_peak, it.hourLabel()),
                        tone = BadgeTone.Neutral,
                        weight = FontWeight.Medium,
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
                            // Canvas: peak dark, other hours grey (the Happy Hour accent band isn't in the first release).
                            color = if (it.isPeak) colors.textPrimary else colors.chartMuted,
                            valueLabel = it.quantity,
                        )
                    },
                contentDescription = description,
                height = spacing.chartHeightTall,
                gap = spacing.barGapWide,
            )
            HourAxis(products.hours)
            Text(text = stringResource(Res.string.sales_products_caption), style = type.caption, color = colors.textSecondary)
        }
    }
}

/** Every second hour of the day, two digits (16 18 20 22 00 02), spread across the chart. */
@Composable
private fun HourAxis(hours: List<Int>) {
    if (hours.isEmpty()) return
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        hours.filterIndexed { index, _ -> index % 2 == 0 }.forEach {
            Text(
                text = it.twoDigits(),
                style = OneMindTheme.typography.tabLabel.copy(fontWeight = FontWeight.Normal),
                color = OneMindTheme.colors.textSecondary,
            )
        }
    }
}

/** Items: selection marker, name 15/600 over *category · n sold* 12, gross 14/500; tapping a row charts it. */
@Composable
private fun ProductList(
    rows: List<ProductRow>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    OneMindListCard {
        rows.forEachIndexed { index, row ->
            val selected = row.key == selectedKey
            val sold = stringResource(Res.string.sales_products_sold, row.sold)
            val prefix = listOfNotNull(row.venueName, row.categoryName).joinToString(" · ")
            val meta = if (prefix.isEmpty()) sold else stringResource(Res.string.sales_products_row_meta, prefix, sold)
            Column(
                modifier =
                    Modifier.selectable(selected = selected, role = Role.Button, onClick = { onSelect(row.key) }),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = spacing.listRowHeight),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = spacing.selectionMarkerWidth, height = spacing.selectionMarkerHeight)
                                .background(
                                    if (selected) colors.accent else Color.Transparent,
                                    RoundedCornerShape(OneMindTheme.radius.marker),
                                ),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap)) {
                        Text(text = row.name, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        Text(text = meta, style = type.caption, color = colors.textSecondary)
                    }
                    Text(text = row.gross, style = type.labelLarge, color = colors.textPrimary)
                }
                if (index < rows.lastIndex) HorizontalDivider(thickness = spacing.borderWidth, color = colors.divider)
            }
        }
    }
}

private fun Int.hourLabel(): String = twoDigits() + ":00"

private fun Int.twoDigits(): String = toString().padStart(2, '0')

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
