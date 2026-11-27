package com.onemind.composeapp.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.onemind.composeapp.generated.resources.Res
import com.onemind.composeapp.generated.resources.stock_action_count
import com.onemind.composeapp.generated.resources.stock_action_recipes
import com.onemind.composeapp.generated.resources.stock_action_scan_invoice
import com.onemind.composeapp.generated.resources.stock_action_transfer
import com.onemind.composeapp.generated.resources.stock_add
import com.onemind.composeapp.generated.resources.stock_attention_delivery
import com.onemind.composeapp.generated.resources.stock_attention_delivery_meta
import com.onemind.composeapp.generated.resources.stock_attention_mark_ordered
import com.onemind.composeapp.generated.resources.stock_attention_ordered
import com.onemind.composeapp.generated.resources.stock_attention_ordered_meta
import com.onemind.composeapp.generated.resources.stock_attention_pull
import com.onemind.composeapp.generated.resources.stock_attention_pull_list
import com.onemind.composeapp.generated.resources.stock_attention_pull_meta
import com.onemind.composeapp.generated.resources.stock_attention_reorder
import com.onemind.composeapp.generated.resources.stock_attention_reorder_meta
import com.onemind.composeapp.generated.resources.stock_attention_review
import com.onemind.composeapp.generated.resources.stock_demo_badge
import com.onemind.composeapp.generated.resources.stock_location_below_par
import com.onemind.composeapp.generated.resources.stock_location_items
import com.onemind.composeapp.generated.resources.stock_location_receiving
import com.onemind.composeapp.generated.resources.stock_locations
import com.onemind.composeapp.generated.resources.stock_needs_attention
import com.onemind.composeapp.generated.resources.stock_on_hand
import com.onemind.composeapp.generated.resources.stock_title
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.format.formatMoney
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Stock tab landing (canvas `StockPlan`) over [overview]. Dev builds only, with a *Demo data* badge:
 * the figures are [DemoStock] until the stock API exists (phase 6). The action tiles and rows don't open
 * screens yet; *Mark ordered* only toggles on the device.
 */
@Composable
fun StockDemoScreen(overview: StockOverview = DemoStock.overview) {
    val spacing = OneMindTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGapTight),
        modifier =
            Modifier
                .fillMaxSize()
                .background(OneMindTheme.colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = spacing.cardPadding,
                    bottom = spacing.rowGap,
                ),
    ) {
        Header(overview)
        Actions()
        NeedsAttention(overview.attention)
        Locations(overview.locations)
    }
}

@Composable
private fun Header(overview: StockOverview) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.stock_title),
                style = type.title,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            DeltaBadge(text = stringResource(Res.string.stock_demo_badge), tone = BadgeTone.Neutral)
        }
        Text(
            text =
                pluralStringResource(
                    Res.plurals.stock_on_hand,
                    overview.locations.size,
                    formatMoney(overview.onHand),
                    overview.locations.size,
                ),
            style = type.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun Actions() {
    Row(horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap), modifier = Modifier.fillMaxWidth()) {
        ActionTile(OneMindIcons.Receipt, Res.string.stock_action_scan_invoice, Modifier.weight(1f))
        ActionTile(OneMindIcons.Transfer, Res.string.stock_action_transfer, Modifier.weight(1f))
        ActionTile(OneMindIcons.Count, Res.string.stock_action_count, Modifier.weight(1f))
        ActionTile(OneMindIcons.Brand, Res.string.stock_action_recipes, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: StringResource,
    modifier: Modifier,
) {
    val spacing = OneMindTheme.spacing
    OneMindCard(modifier = modifier, contentPadding = PaddingValues(vertical = spacing.cardPaddingCompact)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.tileGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OneMindTheme.colors.accent,
                modifier =
                    Modifier.size(spacing.iconBoxSize - spacing.tileGap),
            )
            Text(
                text = stringResource(label),
                style = OneMindTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = OneMindTheme.colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = OneMindTheme.spacing.minTouchTarget),
    ) {
        Text(
            text = text,
            style = OneMindTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = OneMindTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
private fun NeedsAttention(items: List<StockAttention>) {
    Column(verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
        SectionTitle(stringResource(Res.string.stock_needs_attention))
        OneMindListCard {
            items.forEachIndexed { index, item ->
                AttentionRow(item, showDivider = index < items.lastIndex)
            }
        }
    }
}

@Composable
private fun AttentionRow(
    item: StockAttention,
    showDivider: Boolean,
) {
    val colors = OneMindTheme.colors
    when (item) {
        is StockAttention.DeliveryToApprove -> {
            ListRow(
                title = pluralStringResource(Res.plurals.stock_attention_delivery, 1, 1),
                meta =
                    pluralStringResource(
                        Res.plurals.stock_attention_delivery_meta,
                        item.lineCount,
                        item.supplier,
                        item.scannedBy,
                        item.lineCount,
                    ),
                leading = { Dot(colors.accent) },
                trailingContent = { ActionLabel(stringResource(Res.string.stock_attention_review)) },
                showDivider = showDivider,
                minHeight = OneMindTheme.spacing.rowHeightTall,
            )
        }

        is StockAttention.PullList -> {
            ListRow(
                title = pluralStringResource(Res.plurals.stock_attention_pull, item.itemCount, item.itemCount, item.location),
                meta = stringResource(Res.string.stock_attention_pull_meta, item.from),
                leading = { Dot(colors.warningText) },
                trailingContent = { ActionLabel(stringResource(Res.string.stock_attention_pull_list)) },
                showDivider = showDivider,
                minHeight = OneMindTheme.spacing.rowHeightTall,
            )
        }

        is StockAttention.Reorder -> {
            var ordered by remember { mutableStateOf(false) }
            ListRow(
                title = stringResource(Res.string.stock_attention_reorder, item.item),
                meta =
                    if (ordered) {
                        stringResource(Res.string.stock_attention_ordered_meta)
                    } else {
                        stringResource(Res.string.stock_attention_reorder_meta, item.left, item.reorderAt)
                    },
                leading = { Dot(if (ordered) colors.iconMuted else colors.negative) },
                trailingContent = {
                    OutlineButton(
                        text =
                            if (ordered) {
                                stringResource(Res.string.stock_attention_ordered)
                            } else {
                                stringResource(Res.string.stock_attention_mark_ordered)
                            },
                        onClick = { ordered = !ordered },
                        height = OneMindTheme.spacing.minTouchTarget,
                    )
                },
                showDivider = showDivider,
                minHeight = OneMindTheme.spacing.rowHeightTall,
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(modifier = Modifier.size(OneMindTheme.spacing.statusDot).background(color, CircleShape))
}

@Composable
private fun ActionLabel(text: String) {
    Text(text = text, style = OneMindTheme.typography.label, color = OneMindTheme.colors.accent)
}

@Composable
private fun Locations(locations: List<StockLocation>) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.tileGap)) {
        SectionTitle(stringResource(Res.string.stock_locations)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.listGap)) {
                Icon(
                    imageVector = OneMindIcons.Plus,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier =
                        Modifier.size(spacing.chevronSize + spacing.listGap),
                )
                ActionLabel(stringResource(Res.string.stock_add))
            }
        }
        OneMindListCard {
            locations.forEachIndexed { index, location ->
                val parts =
                    listOfNotNull(
                        location.kind,
                        pluralStringResource(Res.plurals.stock_location_items, location.itemCount, location.itemCount),
                        if (location.belowParCount >
                            0
                        ) {
                            stringResource(Res.string.stock_location_below_par, location.belowParCount)
                        } else {
                            null
                        },
                        if (location.isReceiving) stringResource(Res.string.stock_location_receiving) else null,
                    )
                ListRow(
                    title = location.name,
                    meta = parts.joinToString(" · "),
                    leading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(
                                        spacing.iconBoxSize,
                                    ).background(colors.surfaceMuted, RoundedCornerShape(OneMindTheme.radius.tag)),
                        ) {
                            Icon(
                                imageVector = OneMindIcons.Stock,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(spacing.iconBoxIconSize),
                            )
                        }
                    },
                    trailingValue = formatMoney(location.value),
                    trailingValueColor = colors.textSecondary,
                    showDivider = index < locations.lastIndex,
                    minHeight = spacing.rowHeightTall,
                )
            }
        }
    }
}

@Preview
@Composable
private fun StockDemoPreview() {
    OneMindTheme { StockDemoScreen() }
}
