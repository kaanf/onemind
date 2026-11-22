package com.onemind.core.presentation.format

import java.text.NumberFormat
import java.util.Locale

internal actual fun platformFormatInteger(
    value: Long,
    localeTag: String,
): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag(localeTag)).format(value)
