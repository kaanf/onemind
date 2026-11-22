package com.onemind.core.presentation.format

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp

internal actual fun platformFormatCurrency(
    amountMinor: Long,
    currencyCode: String,
    localeTag: String,
    currencyDigits: Int,
    fractionDigits: Int,
): String {
    check(amountMinor != Long.MIN_VALUE) { "Amount out of range" }
    val formatter =
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
            this.currencyCode = currencyCode
            minimumFractionDigits = fractionDigits.toULong()
            maximumFractionDigits = fractionDigits.toULong()
            roundingMode = NSNumberFormatterRoundHalfUp
        }
    val magnitude = if (amountMinor < 0) -amountMinor else amountMinor
    val number = NSDecimalNumber(mantissa = magnitude.toULong(), exponent = (-currencyDigits).toShort(), isNegative = amountMinor < 0)
    return checkNotNull(formatter.stringFromNumber(number)) { "Could not format $currencyCode" }
}

internal actual fun currencyFractionDigits(currencyCode: String): Int =
    NSNumberFormatter()
        .apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            this.currencyCode = currencyCode
        }.maximumFractionDigits
        .toInt()
