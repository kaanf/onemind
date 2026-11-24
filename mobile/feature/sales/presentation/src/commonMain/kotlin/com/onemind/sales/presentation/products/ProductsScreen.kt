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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.onemind.core.designsystem.theme.OneMindMotion
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.presentation.detail.DetailLazyScaffold
import com.onemind.sales.presentation.detail.DetailMessage
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
    DetailLazyScaffold(
        title = stringResource(Res.string.sales_products_title),
        subtitle = ready?.products?.let { stringResource(Res.string.sales_products_subtitle, weekdayDate(it.businessDay)) },
        onBack = onBack,
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(ProductsAction.Refresh) },
    ) {
        when (val content = state.content) {
            ProductsContent.Loading -> {
                section(SKELETON_KEY) { ProductsSkeleton() }
            }

            ProductsContent.NoVenue -> {
                section(MESSAGE_KEY) { DetailMessage(stringResource(Res.string.sales_detail_no_venue), isError = false) }
            }

            is ProductsContent.Failed -> {
                section(MESSAGE_KEY) {
                    DetailMessage(content.message.asString(), isError = true, onRetry = { onAction(ProductsAction.Refresh) })
                }
            }

            is ProductsContent.Ready -> {
                readyItems(state, content, onAction)
            }
        }
    }
}

/**
 * The loaded screen as lazy items with stable keys: a chip or product change recomposes only the chart
 * and the two rows whose selection changed; a chip change keeps the rows that stay (same keys).
 */
private fun LazyListScope.readyItems(
    state: ProductsState,
    content: ProductsContent.Ready,
    onAction: (ProductsAction) -> Unit,
) {
    val products = content.products
    if (content.refreshError != null || products.pendingVenueCount > 0) {
        section(NOTICES_KEY) {
            Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap)) {
                content.refreshError?.let {
                    Text(
                        text = it.asString(),
                        style = OneMindTheme.typography.caption,
                        color = OneMindTheme.colors.negativeText,
                    )
                }
                if (products.pendingVenueCount > 0) {
                    Text(
                        text =
                            pluralStringResource(
                                Res.plurals.sales_detail_venues_syncing,
                                products.pendingVenueCount,
                                products.pendingVenueCount,
                            ),
                        style = OneMindTheme.typography.caption,
                        color = OneMindTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
    if (!products.hasData) {
        section(MESSAGE_KEY) { DetailMessage(stringResource(Res.string.sales_detail_no_data_yet), isError = false) }
        return
    }
    if (products.products.isEmpty()) {
        section(MESSAGE_KEY) { DetailMessage(stringResource(Res.string.sales_products_empty), isError = false) }
        return
    }
    section(CHIPS_KEY) { Chips(products.chips, state.categoryId) { onAction(ProductsAction.SelectCategory(it)) } }
    val rows = state.visibleProducts
    val selected = state.selectedProduct
    if (selected == null) {
        section(MESSAGE_KEY) { DetailMessage(stringResource(Res.string.sales_products_empty_category), isError = false) }
    } else {
        section(CHART_KEY) { Chart(products, selected) }
        itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, _ -> ROW_TYPE }) { index, row ->
            ProductRowItem(
                row = row,
                selected = row.key == selected.key,
                first = index == 0,
                last = index == rows.lastIndex,
                onSelect = { onAction(ProductsAction.SelectProduct(it)) },
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = OneMindMotion.medium(),
                        placementSpec = null,
                        fadeOutSpec = OneMindMotion.medium(),
                    ),
            )
        }
    }
    if (products.hasDataGaps) {
        section(GAPS_KEY) {
            Text(
                text = stringResource(Res.string.sales_detail_data_gaps),
                style = OneMindTheme.typography.caption,
                color = OneMindTheme.colors.warningText,
            )
        }
    }
}

/** A block of the screen with the gap after it; fades in place when it replaces another (skeleton → content). */
private fun LazyListScope.section(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = key) {
        Box(
            modifier =
                Modifier
                    .animateItem(fadeInSpec = OneMindMotion.medium(), placementSpec = null, fadeOutSpec = OneMindMotion.medium())
                    .padding(bottom = OneMindTheme.spacing.sectionGapTight),
        ) { content() }
    }
}

private const val SKELETON_KEY = "skeleton"
private const val MESSAGE_KEY = "message"
private const val NOTICES_KEY = "notices"
private const val CHIPS_KEY = "chips"
private const val CHART_KEY = "chart"
private const val GAPS_KEY = "gaps"
private const val ROW_TYPE = "row"

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
internal fun Modifier.bleed(gutter: Dp): Modifier =
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

/**
 * One item row, drawn as its slice of the items card (rounded on the first and last row), so the list can
 * be lazy. The ripple fills the whole slice. Marker, name 15/600 over *category · n sold* 12, gross 14/500.
 */
@Composable
private fun ProductRowItem(
    row: ProductRow,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val spacing = OneMindTheme.spacing
    val sold = stringResource(Res.string.sales_products_sold, row.sold)
    val prefix = listOfNotNull(row.venueName, row.categoryName).joinToString(" · ")
    val meta = if (prefix.isEmpty()) sold else stringResource(Res.string.sales_products_row_meta, prefix, sold)
    Column(
        modifier =
            modifier
                .then(if (last) Modifier.padding(bottom = spacing.sectionGapTight) else Modifier)
                .cardSlice(first = first, last = last)
                .selectable(selected = selected, role = Role.Button, onClick = { onSelect(row.key) }),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.listRowHeight)
                    .padding(horizontal = spacing.cardPaddingCompact),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = spacing.selectionMarkerWidth, height = spacing.selectionMarkerHeight)
                        .background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(OneMindTheme.radius.marker)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairlineGap)) {
                Text(text = row.name, style = type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                Text(text = meta, style = type.caption, color = colors.textSecondary)
            }
            Text(text = row.gross, style = type.labelLarge, color = colors.textPrimary)
        }
        if (!last) {
            HorizontalDivider(
                thickness = spacing.borderWidth,
                color = colors.divider,
                modifier = Modifier.padding(horizontal = spacing.cardPaddingCompact),
            )
        }
    }
}

/**
 * A slice of a card (surface, 1 px border, radius 16 at the ends): clipped to its own shape so a ripple
 * stays inside, with the border drawn as part of the whole card's outline (sides always, top/bottom only
 * on the first/last slice).
 */
@Composable
private fun Modifier.cardSlice(
    first: Boolean,
    last: Boolean,
): Modifier {
    val colors = OneMindTheme.colors
    val radius = OneMindTheme.radius.card
    val borderWidth = OneMindTheme.spacing.borderWidth
    val top = if (first) CornerSize(radius) else ZeroCornerSize
    val bottom = if (last) CornerSize(radius) else ZeroCornerSize
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
    return fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
        .drawWithContent {
            drawContent()
            val stroke = borderWidth.toPx()
            val corner = radius.toPx()
            // Extend past the open ends so only the outline that belongs to this slice is inside the clip.
            val outlineTop = if (first) stroke / 2 else -corner * 2
            val outlineBottom = if (last) size.height - stroke / 2 else size.height + corner * 2
            drawRoundRect(
                color = colors.border,
                topLeft = Offset(stroke / 2, outlineTop),
                size = Size(size.width - stroke, outlineBottom - outlineTop),
                cornerRadius = CornerRadius(corner),
                style = Stroke(stroke),
            )
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
