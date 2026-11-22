package com.onemind.core.presentation.format

/**
 * A whole count (*1 846 guests*) in the venue's [locale] with the platform formatter, grouped like money
 * (METRICS.md: U+202F between thousands in Czech, `−` for negatives). Counts are not amounts: use
 * [formatMoney] for money.
 */
fun formatCount(
    count: Long,
    locale: VenueLocale,
): String = normalizeMoneyText(platformFormatInteger(count, locale.tag))

/** The platform's grouped integer format of [value] in [localeTag] (Android `NumberFormat`, iOS `NSNumberFormatter`). */
internal expect fun platformFormatInteger(
    value: Long,
    localeTag: String,
): String
