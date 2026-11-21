package com.onemind.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.onemind.core.designsystem.components.AvatarInitials
import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.designsystem.components.Bar
import com.onemind.core.designsystem.components.BarChart
import com.onemind.core.designsystem.components.DeltaBadge
import com.onemind.core.designsystem.components.DetailTopBar
import com.onemind.core.designsystem.components.FloorPlan
import com.onemind.core.designsystem.components.FloorTile
import com.onemind.core.designsystem.components.HeatCell
import com.onemind.core.designsystem.components.Heatmap
import com.onemind.core.designsystem.components.HeatmapRow
import com.onemind.core.designsystem.components.HintCard
import com.onemind.core.designsystem.components.ListRow
import com.onemind.core.designsystem.components.OneMindCard
import com.onemind.core.designsystem.components.OneMindFilterChip
import com.onemind.core.designsystem.components.OneMindListCard
import com.onemind.core.designsystem.components.OtpInput
import com.onemind.core.designsystem.components.OutlineButton
import com.onemind.core.designsystem.components.PrimaryButton
import com.onemind.core.designsystem.components.PrimaryButtonStyle
import com.onemind.core.designsystem.components.ProgressRow
import com.onemind.core.designsystem.components.ProgressStatus
import com.onemind.core.designsystem.components.SegmentedTabs
import com.onemind.core.designsystem.components.StatTile
import com.onemind.core.designsystem.components.StatTileStyle
import com.onemind.core.designsystem.components.TabBar
import com.onemind.core.designsystem.components.TabBarItem
import com.onemind.core.designsystem.components.TileTone
import com.onemind.core.designsystem.components.WarningCard
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

// Sample values from the design canvas, for previews only; never shown in the app.

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    OneMindTheme {
        Column(
            modifier = Modifier.background(OneMindTheme.colors.background).padding(OneMindTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.sectionGap),
        ) { content() }
    }
}

@Preview
@Composable
private fun NavigationPreview() {
    PreviewSurface {
        DetailTopBar(title = "Products by hour", subtitle = "Thu, 5 Nov", onBack = {}, backContentDescription = "Back to Sales")
        SegmentedTabs(options = listOf("Hour", "Day", "Week", "Month"), selectedIndex = 2, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
            OneMindFilterChip(label = "All", selected = true, onClick = {})
            OneMindFilterChip(label = "Beer", selected = false, onClick = {})
        }
        TabBar(
            items =
                listOf(
                    TabBarItem("Today", OneMindIcons.Today),
                    TabBarItem("Sales", OneMindIcons.Sales),
                    TabBarItem("Staff", OneMindIcons.Staff),
                    TabBarItem("Stock", OneMindIcons.Stock),
                ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun ContentPreview() {
    PreviewSurface {
        OneMindCard {
            StatTile(label = "Paid so far", value = "48 320 Kč", style = StatTileStyle.Overline)
            DeltaBadge(text = "+12.4% vs last Friday", tone = BadgeTone.Positive)
            BarChart(
                bars =
                    listOf(18f, 24f, 31f, 42f, 55f, 70f, 82f).mapIndexed { i, v ->
                        Bar(
                            v,
                            if (i ==
                                6
                            ) {
                                OneMindTheme.colors.accent
                            } else {
                                OneMindTheme.colors.chartMutedStrong
                            },
                        )
                    },
                contentDescription = "Revenue by hour",
            )
        }
        OneMindListCard {
            ListRow(
                title = "Tomáš N.",
                meta = "Bar lead · since 16:00",
                leading = { AvatarInitials("TN") },
                trailingValue = "14 820 Kč",
                trailingMeta = "1 210 Kč",
            )
            ListRow(title = "Possible loss", trailingValue = "1 870 Kč", showChevron = true, showDivider = false, onClick = {})
        }
        HintCard(lead = "Best corner is the window.", body = "T1, T2 and T5 bring 40% of table revenue.")
        WarningCard(lead = "Above usual.", body = "Her typical shift has 2 to 3 deleted items.", onClick = {})
    }
}

@Preview
@Composable
private fun InputPreview() {
    PreviewSurface {
        OtpInput(value = "482", onValueChange = {}, contentDescription = "Code")
        PrimaryButton(text = "Send me a code", onClick = {})
        PrimaryButton(text = "Continue with Apple", onClick = {}, style = PrimaryButtonStyle.Dark)
        PrimaryButton(text = "Verify", onClick = {}, enabled = false)
        OutlineButton(text = "Continue with Google", onClick = {})
        OneMindListCard {
            ProgressRow(name = "Menu and categories", meta = "142 items", status = ProgressStatus.Done)
            ProgressRow(name = "Sales history", meta = "64%", status = ProgressStatus.Running(0.64f))
            ProgressRow(name = "Happy Hour", meta = "waiting", status = ProgressStatus.Waiting, showDivider = false)
        }
    }
}

@Preview
@Composable
private fun ChartsPreview() {
    PreviewSurface {
        Heatmap(
            columnLabels = listOf("20", "21", "22", "23"),
            rows =
                listOf("Fri", "Sat").mapIndexed { r, day ->
                    HeatmapRow(day, List(4) { c -> HeatCell(level = r * 2 + c, label = "$day ${20 + c}:00") })
                },
            selected = 1 to 2,
            onSelect = { _, _ -> },
        )
        FloorPlan(
            tiles =
                listOf(FloorTile("Bar", "2 420", TileTone.Busy, "Bar counter", spanRow = true)) +
                    List(5) { i ->
                        FloorTile(
                            "T${i + 1}",
                            if (i % 2 ==
                                0
                            ) {
                                "1 240"
                            } else {
                                "Free"
                            },
                            if (i % 2 == 0) TileTone.Busy else TileTone.Free,
                            "T${i + 1}",
                        )
                    },
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
