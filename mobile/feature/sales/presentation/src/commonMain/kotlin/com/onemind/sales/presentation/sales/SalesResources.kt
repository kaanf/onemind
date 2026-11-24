package com.onemind.sales.presentation.sales

import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_gap_outdated
import com.onemind.sales.presentation.generated.resources.sales_gap_payment_mismatch
import com.onemind.sales.presentation.generated.resources.sales_gap_unreadable
import com.onemind.sales.presentation.generated.resources.sales_gap_without_items
import com.onemind.sales.presentation.generated.resources.sales_month_april
import com.onemind.sales.presentation.generated.resources.sales_month_august
import com.onemind.sales.presentation.generated.resources.sales_month_december
import com.onemind.sales.presentation.generated.resources.sales_month_february
import com.onemind.sales.presentation.generated.resources.sales_month_january
import com.onemind.sales.presentation.generated.resources.sales_month_july
import com.onemind.sales.presentation.generated.resources.sales_month_june
import com.onemind.sales.presentation.generated.resources.sales_month_march
import com.onemind.sales.presentation.generated.resources.sales_month_may
import com.onemind.sales.presentation.generated.resources.sales_month_november
import com.onemind.sales.presentation.generated.resources.sales_month_october
import com.onemind.sales.presentation.generated.resources.sales_month_september
import com.onemind.sales.presentation.generated.resources.sales_month_short_april
import com.onemind.sales.presentation.generated.resources.sales_month_short_august
import com.onemind.sales.presentation.generated.resources.sales_month_short_december
import com.onemind.sales.presentation.generated.resources.sales_month_short_february
import com.onemind.sales.presentation.generated.resources.sales_month_short_january
import com.onemind.sales.presentation.generated.resources.sales_month_short_july
import com.onemind.sales.presentation.generated.resources.sales_month_short_june
import com.onemind.sales.presentation.generated.resources.sales_month_short_march
import com.onemind.sales.presentation.generated.resources.sales_month_short_may
import com.onemind.sales.presentation.generated.resources.sales_month_short_november
import com.onemind.sales.presentation.generated.resources.sales_month_short_october
import com.onemind.sales.presentation.generated.resources.sales_month_short_september
import com.onemind.sales.presentation.generated.resources.sales_period_day
import com.onemind.sales.presentation.generated.resources.sales_period_hour
import com.onemind.sales.presentation.generated.resources.sales_period_month
import com.onemind.sales.presentation.generated.resources.sales_period_week
import com.onemind.sales.presentation.generated.resources.sales_weekday_friday
import com.onemind.sales.presentation.generated.resources.sales_weekday_monday
import com.onemind.sales.presentation.generated.resources.sales_weekday_saturday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_friday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_monday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_saturday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_sunday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_thursday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_tuesday
import com.onemind.sales.presentation.generated.resources.sales_weekday_short_wednesday
import com.onemind.sales.presentation.generated.resources.sales_weekday_sunday
import com.onemind.sales.presentation.generated.resources.sales_weekday_thursday
import com.onemind.sales.presentation.generated.resources.sales_weekday_tuesday
import com.onemind.sales.presentation.generated.resources.sales_weekday_wednesday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

internal fun SalesPeriod.labelRes(): StringResource =
    when (this) {
        SalesPeriod.HOUR -> Res.string.sales_period_hour
        SalesPeriod.DAY -> Res.string.sales_period_day
        SalesPeriod.WEEK -> Res.string.sales_period_week
        SalesPeriod.MONTH -> Res.string.sales_period_month
    }

internal fun DayOfWeek.nameRes(): StringResource =
    when (this) {
        DayOfWeek.MONDAY -> Res.string.sales_weekday_monday
        DayOfWeek.TUESDAY -> Res.string.sales_weekday_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.sales_weekday_wednesday
        DayOfWeek.THURSDAY -> Res.string.sales_weekday_thursday
        DayOfWeek.FRIDAY -> Res.string.sales_weekday_friday
        DayOfWeek.SATURDAY -> Res.string.sales_weekday_saturday
        DayOfWeek.SUNDAY -> Res.string.sales_weekday_sunday
    }

internal fun DayOfWeek.shortNameRes(): StringResource =
    when (this) {
        DayOfWeek.MONDAY -> Res.string.sales_weekday_short_monday
        DayOfWeek.TUESDAY -> Res.string.sales_weekday_short_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.sales_weekday_short_wednesday
        DayOfWeek.THURSDAY -> Res.string.sales_weekday_short_thursday
        DayOfWeek.FRIDAY -> Res.string.sales_weekday_short_friday
        DayOfWeek.SATURDAY -> Res.string.sales_weekday_short_saturday
        DayOfWeek.SUNDAY -> Res.string.sales_weekday_short_sunday
    }

internal fun Month.shortNameRes(): StringResource =
    when (this) {
        Month.JANUARY -> Res.string.sales_month_short_january
        Month.FEBRUARY -> Res.string.sales_month_short_february
        Month.MARCH -> Res.string.sales_month_short_march
        Month.APRIL -> Res.string.sales_month_short_april
        Month.MAY -> Res.string.sales_month_short_may
        Month.JUNE -> Res.string.sales_month_short_june
        Month.JULY -> Res.string.sales_month_short_july
        Month.AUGUST -> Res.string.sales_month_short_august
        Month.SEPTEMBER -> Res.string.sales_month_short_september
        Month.OCTOBER -> Res.string.sales_month_short_october
        Month.NOVEMBER -> Res.string.sales_month_short_november
        Month.DECEMBER -> Res.string.sales_month_short_december
    }

internal fun Month.nameRes(): StringResource =
    when (this) {
        Month.JANUARY -> Res.string.sales_month_january
        Month.FEBRUARY -> Res.string.sales_month_february
        Month.MARCH -> Res.string.sales_month_march
        Month.APRIL -> Res.string.sales_month_april
        Month.MAY -> Res.string.sales_month_may
        Month.JUNE -> Res.string.sales_month_june
        Month.JULY -> Res.string.sales_month_july
        Month.AUGUST -> Res.string.sales_month_august
        Month.SEPTEMBER -> Res.string.sales_month_september
        Month.OCTOBER -> Res.string.sales_month_october
        Month.NOVEMBER -> Res.string.sales_month_november
        Month.DECEMBER -> Res.string.sales_month_december
    }

internal fun SalesDataGapKind.pluralRes(): PluralStringResource =
    when (this) {
        SalesDataGapKind.UNREADABLE -> Res.plurals.sales_gap_unreadable
        SalesDataGapKind.WITHOUT_ITEMS -> Res.plurals.sales_gap_without_items
        SalesDataGapKind.OUTDATED -> Res.plurals.sales_gap_outdated
        SalesDataGapKind.PAYMENT_MISMATCH -> Res.plurals.sales_gap_payment_mismatch
    }
