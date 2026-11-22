package com.onemind.core.presentation.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CountFormatTest {
    // `·` = U+202F (narrow no-break space), as in MoneyFormatTest.
    private fun expect(
        pattern: String,
        actual: String,
    ) = assertEquals(pattern.replace('·', '\u202F'), actual)

    @Test
    fun czechGroupsThousandsWithANarrowNoBreakSpace() {
        expect("286", formatCount(286, VenueLocale.CZECH))
        expect("1·846", formatCount(1_846, VenueLocale.CZECH))
        expect("1·234·567", formatCount(1_234_567, VenueLocale.CZECH))
    }

    @Test
    fun negativeCountsUseTheMinusSign() = expect("−1·846", formatCount(-1_846, VenueLocale.CZECH))

    @Test
    fun theFallbackLocaleUsesItsOwnGrouping() = expect("1,846", formatCount(1_846, VenueLocale.FALLBACK))
}
