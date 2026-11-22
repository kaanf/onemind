package com.onemind.core.presentation.format

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

internal actual fun platformFormatInteger(
    value: Long,
    localeTag: String,
): String {
    val formatter =
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
            maximumFractionDigits = 0u
        }
    return checkNotNull(formatter.stringFromNumber(NSNumber(longLong = value))) { "Could not format $value" }
}
