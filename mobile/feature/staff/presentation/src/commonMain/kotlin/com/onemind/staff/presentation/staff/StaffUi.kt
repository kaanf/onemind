package com.onemind.staff.presentation.staff

import androidx.compose.runtime.Immutable
import com.onemind.core.presentation.format.VenueLocale
import com.onemind.core.presentation.format.formatCount
import com.onemind.core.presentation.format.formatMoney
import com.onemind.staff.domain.StaffSummary

/** 03 ready to draw: the day's team totals and staff rows, formatted. */
@Immutable
data class StaffUi(
    /** `false` while no venue has data for the day: no figure is drawn (rule 2). */
    val hasData: Boolean,
    val pendingVenueCount: Int,
    val totals: StaffTotalsUi?,
    /** Largest sales first, as sent. */
    val staff: List<StaffRow>,
    val hasDataGaps: Boolean,
)

@Immutable
data class StaffTotalsUi(
    /** "48 320 Kč". */
    val sales: String,
    val tips: String,
    /** "286". */
    val guests: String,
)

@Immutable
data class StaffRow(
    val staffId: String,
    /** `null` when the POS sent no name: the screen says "Unnamed staff". */
    val name: String?,
    /** "TN"; "?" without a name. */
    val initials: String,
    val guests: Int,
    val billCount: Int,
    val deletedBillCount: Int,
    val sales: String,
    val tips: String,
)

internal fun StaffSummary.toUi(): StaffUi {
    val locale = VenueLocale.forCurrency(currency)
    return StaffUi(
        hasData = hasData,
        pendingVenueCount = pendingVenueCount,
        totals =
            totals?.let {
                StaffTotalsUi(
                    sales = formatMoney(it.sales, locale),
                    tips = formatMoney(it.tips, locale),
                    guests = formatCount(it.guests.toLong(), locale),
                )
            },
        staff =
            staff.map {
                StaffRow(
                    staffId = it.staffId,
                    name = it.name,
                    initials = initials(it.name),
                    guests = it.guests,
                    billCount = it.billCount,
                    deletedBillCount = it.deletedBillCount,
                    sales = formatMoney(it.sales, locale),
                    tips = formatMoney(it.tips, locale),
                )
            },
        hasDataGaps = !dataGaps.isEmpty,
    )
}

/** The first letters of the first and last word ("Tomáš Novák" → "TN", "anna" → "A"); "?" without a name. */
internal fun initials(name: String?): String {
    val words =
        name
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    if (words.isEmpty()) return "?"
    val first = words.first().first()
    val last = if (words.size > 1) words.last().first().toString() else ""
    return (first + last).uppercase()
}
