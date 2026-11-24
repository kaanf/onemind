package com.onemind.core.presentation.format

import com.onemind.core.domain.money.Money

/** How much precision an amount shows (METRICS.md, Formatting rules). */
enum class MoneyStyle {
    /**
     * Summaries and dashboards: no decimals from 100 major units up (`48 320 Kč`) or for a whole amount
     * (`0 Kč`, `98 Kč`), else the currency's (`45,50 Kč`).
     */
    Summary,

    /** Always the currency's default fraction digits (`48 320,50 Kč`). */
    Precise,
}

/**
 * Formats [money] in the venue's [locale] with the platform formatter (Android `NumberFormat`, iOS
 * `NSNumberFormatter`; ADR 0012), exactly from minor units (no floating point), rounding half up. The
 * result follows METRICS.md on both platforms: thousands separated by a narrow no-break space (U+202F),
 * other spaces no-break (U+00A0), and the minus sign `−` (U+2212).
 */
fun formatMoney(
    money: Money,
    locale: VenueLocale = VenueLocale.forCurrency(money.currency),
    style: MoneyStyle = MoneyStyle.Summary,
): String {
    val currencyDigits = currencyFractionDigits(money.currency.code)
    val digits =
        when (style) {
            MoneyStyle.Precise -> {
                currencyDigits
            }

            MoneyStyle.Summary -> {
                val unit = pow10(currencyDigits)
                if (money.absMinor() >= HUNDRED * unit || money.absMinor() % unit == 0L) 0 else currencyDigits
            }
        }
    return normalizeMoneyText(platformFormatCurrency(money.amountMinor, money.currency.code, locale.tag, currencyDigits, digits))
}

/**
 * [money] as [formatMoney] writes it in the Summary style, without the currency (`−18 640`, `−890`, `0`):
 * for rows whose header names the currency (02c by type).
 */
fun formatMoneyAmount(
    money: Money,
    locale: VenueLocale = VenueLocale.forCurrency(money.currency),
): String =
    formatMoney(money, locale)
        .filterNot { it.isLetter() || it in CURRENCY_SIGNS }
        .trim(' ', '\u00A0', '\u202F')

private const val CURRENCY_SIGNS = "$€£¥₹₩₺"

/**
 * A compact chart label in major units (METRICS.md: *Large chart labels: `12.4k`*): below 1 000 the
 * whole number (`840`), then one decimal with `k` or `M` (`12.4k`, `312k`, `1.2M`), trailing `.0` dropped.
 * No currency (the axis says it) and the same in every locale, as in the formatting rules.
 */
fun formatChartLabel(money: Money): String {
    val major = roundHalfUpDiv(money.absMinor(), pow10(currencyFractionDigits(money.currency.code)))
    val sign = if (money.isNegative && major > 0) MINUS else ""
    return sign + compact(major)
}

internal fun compact(major: Long): String {
    if (major < THOUSAND) return major.toString()
    var tenths = roundHalfUpDiv(major, THOUSAND / 10)
    var suffix = "k"
    if (tenths >= THOUSAND * 10) {
        tenths = roundHalfUpDiv(major, THOUSAND * THOUSAND / 10)
        suffix = "M"
    }
    return if (tenths % 10 == 0L) "${tenths / 10}$suffix" else "${tenths / 10}.${tenths % 10}$suffix"
}

/** Platform output → METRICS.md: grouping U+202F, other spaces U+00A0, minus U+2212. */
internal fun normalizeMoneyText(text: String): String =
    buildString {
        text.forEachIndexed { index, char ->
            when {
                char == '-' || char == MINUS_CHAR -> {
                    append(MINUS_CHAR)
                }

                char.isSpaceLike() -> {
                    val betweenDigits = text.getOrNull(index - 1)?.isDigit() == true && text.getOrNull(index + 1)?.isDigit() == true
                    append(if (betweenDigits) NARROW_NBSP else NBSP)
                }

                else -> {
                    append(char)
                }
            }
        }
    }

private fun Char.isSpaceLike() = this == ' ' || this == NBSP || this == NARROW_NBSP || this == ' '

private fun Money.absMinor(): Long {
    check(amountMinor != Long.MIN_VALUE) { "Amount out of range" }
    return if (amountMinor < 0) -amountMinor else amountMinor
}

private fun pow10(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10 }
    return result
}

private fun roundHalfUpDiv(
    value: Long,
    divisor: Long,
): Long = value / divisor + if (value % divisor * 2 >= divisor) 1 else 0

/**
 * The platform's currency format of [amountMinor] (scale [currencyDigits]) in [localeTag], with exactly
 * [fractionDigits] decimals, rounding half up. Built from an exact decimal (BigDecimal / NSDecimalNumber).
 */
internal expect fun platformFormatCurrency(
    amountMinor: Long,
    currencyCode: String,
    localeTag: String,
    currencyDigits: Int,
    fractionDigits: Int,
): String

/** Default fraction digits of an ISO 4217 currency (CZK 2, JPY 0). */
internal expect fun currencyFractionDigits(currencyCode: String): Int

private const val HUNDRED = 100L
private const val THOUSAND = 1_000L
private const val MINUS_CHAR = '−'
private const val MINUS = "−"
private const val NBSP = ' '
private const val NARROW_NBSP = ' '
