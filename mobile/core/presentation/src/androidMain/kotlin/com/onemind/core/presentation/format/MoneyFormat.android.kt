package com.onemind.core.presentation.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal actual fun platformFormatCurrency(
    amountMinor: Long,
    currencyCode: String,
    localeTag: String,
    currencyDigits: Int,
    fractionDigits: Int,
): String {
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(localeTag))
    format.currency = Currency.getInstance(currencyCode)
    format.minimumFractionDigits = fractionDigits
    format.maximumFractionDigits = fractionDigits
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(BigDecimal.valueOf(amountMinor, currencyDigits))
}

/** Must match the iOS actual (NSNumberFormatter's currency digits); see `MoneyFormatTest`. */
internal actual fun currencyFractionDigits(currencyCode: String): Int =
    Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
