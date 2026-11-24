package com.onemind.sales.presentation.losses

import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.presentation.format.VenueLocale
import com.onemind.core.presentation.format.formatMoney
import com.onemind.core.presentation.format.formatMoneyAmount
import com.onemind.sales.domain.losses.DiscountTypeLoss
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesSummary
import com.onemind.sales.domain.losses.UnavailableLossComponent
import kotlinx.datetime.LocalDate

/** 02c ready to draw: amounts formatted in the venue's currency, nothing computed but signs and order. */
data class LossesUi(
    val period: LossesPeriod,
    val from: LocalDate,
    val to: LocalDate,
    /** `false` while no venue has had its first sync: the figures are not drawn (rule 2). */
    val hasData: Boolean,
    val pendingVenueCount: Int,
    /** "22 410 Kč"; `null` when the server sends none. */
    val possibleLoss: String?,
    val change: LossChange?,
    val records: Int?,
    /** "6.7", as sent. */
    val sharePercent: String?,
    /** By type: discounts, deleted bills, refunds (only those the server sends). */
    val rows: List<LossRow>,
    val discountTypes: List<DiscountRow>,
    val unavailable: List<UnavailableLossComponent>,
    val hasDataGaps: Boolean,
)

/** Change of the loss share in points against the previous period. A higher share is bad news. */
data class LossChange(
    /** "+0.8", "−1.2", "0.0" as sent with a sign. */
    val points: String,
    val direction: Direction,
) {
    enum class Direction { UP, DOWN, FLAT }
}

data class LossRow(
    val kind: LossRowKind,
    val records: Int?,
    /** "−18 640 Kč". */
    val amount: String,
    /** Its share of the possible loss for the stacked bar, 0–1 (drawing only); `null` without a total. */
    val shareFraction: Float? = null,
)

enum class LossRowKind { DISCOUNTS, DELETED_BILLS, REFUNDS }

data class DiscountRow(
    val name: DiscountName,
    val records: Int?,
    val amount: String,
)

sealed interface DiscountName {
    /** *Other discounts*: bill-level discounts above the typed ones. */
    data object Other : DiscountName

    /** An item discount without a type in the POS. */
    data object Unspecified : DiscountName

    /** The POS discount type, made readable ("HAPPY_HOUR" → "Happy Hour"). */
    data class Pos(
        val name: String,
    ) : DiscountName
}

internal fun LossesSummary.toUi(): LossesUi {
    val locale = VenueLocale.forCurrency(currency)

    // By-type rows show the amount without the currency, as on the canvas (*−18 640*).
    fun Money.asLoss() = formatMoneyAmount(if (isZero) this else -this, locale)
    val total = possibleLoss?.amountMinor?.takeIf { it > 0 }

    fun Money.share(): Float? = total?.let { amountMinor.toFloat() / it.toFloat() }
    return LossesUi(
        period = period,
        from = from,
        to = to,
        hasData = hasData,
        pendingVenueCount = pendingVenueCount,
        possibleLoss = possibleLoss?.let { formatMoney(it, locale) },
        change = comparison?.changePoints?.toChange(),
        records = records,
        sharePercent = sharePercent?.toString(),
        rows =
            listOfNotNull(
                // No count: bill-wide discounts (*Other discounts*) have none, so a sum of the typed ones would undercount.
                discounts?.let { LossRow(LossRowKind.DISCOUNTS, records = null, amount = it.asLoss(), shareFraction = it.share()) },
                deletedBills?.let { LossRow(LossRowKind.DELETED_BILLS, it.records, it.amount.asLoss(), it.amount.share()) },
                refunds?.let { LossRow(LossRowKind.REFUNDS, it.records, it.amount.asLoss(), it.amount.share()) },
            ),
        discountTypes = discountsByType.map { DiscountRow(it.type.toName(), it.records, it.amount.asLoss()) },
        unavailable = unavailable.sortedBy { it.ordinal },
        hasDataGaps = !dataGaps.isEmpty,
    )
}

private fun DecimalValue.toChange(): LossChange {
    val text = toString()
    return when {
        isZero -> LossChange(text, LossChange.Direction.FLAT)
        isNegative -> LossChange(MINUS + text.removePrefix("-"), LossChange.Direction.DOWN)
        else -> LossChange("+$text", LossChange.Direction.UP)
    }
}

internal fun String.toName(): DiscountName =
    when (this) {
        DiscountTypeLoss.UNTYPED -> {
            DiscountName.Other
        }

        DiscountTypeLoss.UNSPECIFIED -> {
            DiscountName.Unspecified
        }

        else -> {
            DiscountName.Pos(
                lowercase()
                    .split('_')
                    .filter {
                        it.isNotEmpty()
                    }.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } },
            )
        }
    }

private const val MINUS = "−"
