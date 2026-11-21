package com.onemind.composeapp.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.onemind.composeapp.generated.resources.Res
import com.onemind.composeapp.generated.resources.tab_sales
import com.onemind.composeapp.generated.resources.tab_staff
import com.onemind.composeapp.generated.resources.tab_stock
import com.onemind.composeapp.generated.resources.tab_today
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.sales.presentation.navigation.SalesGraphRoutes
import com.onemind.staff.presentation.navigation.StaffGraphRoutes
import com.onemind.today.presentation.navigation.TodayGraphRoutes
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

/**
 * The tabs of the first release, in tab bar order. Assistant is hidden (`docs/product/MVP_SCOPE.md`).
 * [graph] is the feature graph route; every screen inside it keeps this tab highlighted.
 */
enum class TopLevelDestination(
    val graph: Any,
    val label: StringResource,
    val icon: ImageVector,
) {
    Today(TodayGraphRoutes.Graph, Res.string.tab_today, OneMindIcons.Today),
    Sales(SalesGraphRoutes.Graph, Res.string.tab_sales, OneMindIcons.Sales),
    Staff(StaffGraphRoutes.Graph, Res.string.tab_staff, OneMindIcons.Staff),
    Stock(StockGraphRoutes.Graph, Res.string.tab_stock, OneMindIcons.Stock),
    ;

    val graphClass: KClass<*> get() = graph::class
}
