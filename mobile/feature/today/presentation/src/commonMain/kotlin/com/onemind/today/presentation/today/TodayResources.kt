package com.onemind.today.presentation.today

import com.onemind.today.domain.LossKind
import com.onemind.today.presentation.generated.resources.Res
import com.onemind.today.presentation.generated.resources.today_gap_outdated
import com.onemind.today.presentation.generated.resources.today_gap_payment_mismatch
import com.onemind.today.presentation.generated.resources.today_gap_unreadable
import com.onemind.today.presentation.generated.resources.today_gap_without_items
import com.onemind.today.presentation.generated.resources.today_loss_deleted_bills
import com.onemind.today.presentation.generated.resources.today_loss_discounts
import com.onemind.today.presentation.generated.resources.today_loss_refunds
import com.onemind.today.presentation.generated.resources.today_month_april
import com.onemind.today.presentation.generated.resources.today_month_august
import com.onemind.today.presentation.generated.resources.today_month_december
import com.onemind.today.presentation.generated.resources.today_month_february
import com.onemind.today.presentation.generated.resources.today_month_january
import com.onemind.today.presentation.generated.resources.today_month_july
import com.onemind.today.presentation.generated.resources.today_month_june
import com.onemind.today.presentation.generated.resources.today_month_march
import com.onemind.today.presentation.generated.resources.today_month_may
import com.onemind.today.presentation.generated.resources.today_month_november
import com.onemind.today.presentation.generated.resources.today_month_october
import com.onemind.today.presentation.generated.resources.today_month_september
import com.onemind.today.presentation.generated.resources.today_weekday_friday
import com.onemind.today.presentation.generated.resources.today_weekday_monday
import com.onemind.today.presentation.generated.resources.today_weekday_saturday
import com.onemind.today.presentation.generated.resources.today_weekday_sunday
import com.onemind.today.presentation.generated.resources.today_weekday_thursday
import com.onemind.today.presentation.generated.resources.today_weekday_tuesday
import com.onemind.today.presentation.generated.resources.today_weekday_wednesday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

internal fun DayOfWeek.nameRes(): StringResource =
    when (this) {
        DayOfWeek.MONDAY -> Res.string.today_weekday_monday
        DayOfWeek.TUESDAY -> Res.string.today_weekday_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.today_weekday_wednesday
        DayOfWeek.THURSDAY -> Res.string.today_weekday_thursday
        DayOfWeek.FRIDAY -> Res.string.today_weekday_friday
        DayOfWeek.SATURDAY -> Res.string.today_weekday_saturday
        DayOfWeek.SUNDAY -> Res.string.today_weekday_sunday
    }

internal fun Month.nameRes(): StringResource =
    when (this) {
        Month.JANUARY -> Res.string.today_month_january
        Month.FEBRUARY -> Res.string.today_month_february
        Month.MARCH -> Res.string.today_month_march
        Month.APRIL -> Res.string.today_month_april
        Month.MAY -> Res.string.today_month_may
        Month.JUNE -> Res.string.today_month_june
        Month.JULY -> Res.string.today_month_july
        Month.AUGUST -> Res.string.today_month_august
        Month.SEPTEMBER -> Res.string.today_month_september
        Month.OCTOBER -> Res.string.today_month_october
        Month.NOVEMBER -> Res.string.today_month_november
        Month.DECEMBER -> Res.string.today_month_december
    }

internal fun LossKind.nameRes(): StringResource =
    when (this) {
        LossKind.DISCOUNTS -> Res.string.today_loss_discounts
        LossKind.REFUNDS -> Res.string.today_loss_refunds
        LossKind.DELETED_BILLS -> Res.string.today_loss_deleted_bills
    }

internal fun DataGapKind.pluralRes(): PluralStringResource =
    when (this) {
        DataGapKind.UNREADABLE -> Res.plurals.today_gap_unreadable
        DataGapKind.WITHOUT_ITEMS -> Res.plurals.today_gap_without_items
        DataGapKind.OUTDATED -> Res.plurals.today_gap_outdated
        DataGapKind.PAYMENT_MISMATCH -> Res.plurals.today_gap_payment_mismatch
    }
