package com.onemind.core.presentation.format

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exact strings, so the JVM (Android host test) and the iOS simulator must agree; both run in CI.
 * `·` stands for U+202F (thousands), `_` for U+00A0 (other spaces), `−` is U+2212 (METRICS.md).
 */
class MoneyFormatTest {
    private fun czk(minor: Long) = Money(minor, Currency.CZK)

    private fun expect(
        pattern: String,
        actual: String,
    ) = assertEquals(pattern.replace('·', ' ').replace('_', ' '), actual)

    @Test
    fun czkSummaryHasNoDecimalsFromOneHundredUp() {
        expect("48·320_Kč", formatMoney(czk(4_832_000)))
        expect("100_Kč", formatMoney(czk(10_000)))
        expect("99,99_Kč", formatMoney(czk(9_999)))
        expect("45,50_Kč", formatMoney(czk(4_550)))
        expect("0,00_Kč", formatMoney(czk(0)))
    }

    @Test
    fun negativesUseTheMinusSign() {
        expect("−1·870_Kč", formatMoney(czk(-187_000)))
        expect("−22·410_Kč", formatMoney(czk(-2_241_000)))
        expect("−0,50_Kč", formatMoney(czk(-50)))
    }

    @Test
    fun summaryRoundsHalfUp() {
        expect("48·321_Kč", formatMoney(czk(4_832_050)))
        expect("48·320_Kč", formatMoney(czk(4_832_049)))
        expect("−1·871_Kč", formatMoney(czk(-187_050)))
    }

    @Test
    fun largeAmountsStayExact() {
        expect("1·248·900_Kč", formatMoney(czk(124_890_000)))
        expect("12·345·678·901_Kč", formatMoney(czk(1_234_567_890_100)))
        expect("12·345·678·901,23_Kč", formatMoney(czk(1_234_567_890_123), style = MoneyStyle.Precise))
    }

    @Test
    fun preciseShowsTheCurrencyDigits() {
        expect("40,70_Kč", formatMoney(czk(4_070), style = MoneyStyle.Precise))
        expect("48·320,00_Kč", formatMoney(czk(4_832_000), style = MoneyStyle.Precise))
    }

    @Test
    fun otherCurrenciesUseTheirOwnDigitsInThePlaceholderLocale() {
        expect("−€1,234.56", formatMoney(Money(-123_456, Currency("EUR")), style = MoneyStyle.Precise))
        expect("¥1,500", formatMoney(Money(1_500, Currency("JPY")), style = MoneyStyle.Precise))
    }

    @Test
    fun theVenueLocaleWinsOverTheCurrencyDefault() {
        expect("1·234,56_€", formatMoney(Money(123_456, Currency("EUR")), VenueLocale("cs-CZ"), MoneyStyle.Precise))
    }

    @Test
    fun chartLabelsFollowMetrics() {
        assertEquals("840", formatChartLabel(czk(84_000)))
        assertEquals("12.4k", formatChartLabel(czk(1_240_000)))
        assertEquals("12k", formatChartLabel(czk(1_200_000)))
        assertEquals("312.5k", formatChartLabel(czk(31_248_000)))
        assertEquals("312k", formatChartLabel(czk(31_200_000)))
        assertEquals("1.2M", formatChartLabel(czk(124_890_000)))
        assertEquals("1M", formatChartLabel(czk(99_999_000)))
        assertEquals("−22.4k", formatChartLabel(czk(-2_241_000)))
        assertEquals("0", formatChartLabel(czk(-40)))
    }

    @Test
    fun interimLocaleComesFromTheCurrency() {
        assertEquals(VenueLocale.CZECH, VenueLocale.forCurrency(Currency.CZK))
        assertEquals(VenueLocale.FALLBACK, VenueLocale.forCurrency(Currency("EUR")))
    }

    @Test
    fun normalizationOfPlatformSpaces() {
        assertEquals("−1 870 Kč", normalizeMoneyText("-1 870 Kč"))
        assertEquals("−1 870 Kč", normalizeMoneyText("−1 870 Kč"))
    }
}
