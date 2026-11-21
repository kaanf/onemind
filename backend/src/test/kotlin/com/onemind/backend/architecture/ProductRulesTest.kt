package com.onemind.backend.architecture

import com.onemind.archfixture.account.infra.client.AccountPosting
import com.onemind.archfixture.common.time.BusinessDays
import com.onemind.archfixture.common.time.ClockConfig
import com.onemind.archfixture.pos.infra.client.DeletingExchange
import com.onemind.archfixture.pos.infra.client.GetOnlyClient
import com.onemind.archfixture.pos.infra.client.HttpMethodFromString
import com.onemind.archfixture.pos.infra.client.HttpMethodUsing
import com.onemind.archfixture.pos.infra.client.JdkHttpPutting
import com.onemind.archfixture.pos.infra.client.PostingExchange
import com.onemind.archfixture.pos.infra.client.ReadingExchange
import com.onemind.archfixture.pos.infra.client.RequestEntityPosting
import com.onemind.archfixture.pos.infra.client.RestClientPosting
import com.onemind.archfixture.pos.infra.client.RestTemplateDeleting
import com.onemind.archfixture.pos.infra.client.RestTemplateExchanging
import com.onemind.archfixture.pos.infra.client.RestTemplateSubclassPosting
import com.onemind.archfixture.pos.infra.client.TypeLevelPostingExchange
import com.onemind.archfixture.pos.infra.client.UrlConnectionPosting
import com.onemind.archfixture.reporting.domain.Charger
import com.onemind.archfixture.reporting.domain.Converter
import com.onemind.archfixture.reporting.domain.Dashboard
import com.onemind.archfixture.reporting.domain.DayFigures
import com.onemind.archfixture.reporting.domain.InlineRounding
import com.onemind.archfixture.reporting.domain.Ledger
import com.onemind.archfixture.reporting.domain.Occupancy
import com.onemind.archfixture.reporting.domain.PaymentSplit
import com.onemind.archfixture.reporting.domain.PriceCalculator
import com.onemind.archfixture.reporting.domain.ReceiptLine
import com.onemind.archfixture.reporting.domain.Rounding
import com.onemind.archfixture.reporting.domain.ShiftSummary
import com.onemind.archfixture.stock.application.BucketsByCalendarDate
import com.onemind.archfixture.stock.application.BucketsByOfInstant
import com.onemind.archfixture.stock.application.BucketsByOffsetHour
import com.onemind.archfixture.stock.application.MapsToOffsetDateTime
import com.onemind.archfixture.stock.application.UsesCurrentTimeMillis
import com.onemind.archfixture.stock.application.UsesGregorianCalendar
import com.onemind.archfixture.stock.application.UsesInjectedClock
import com.onemind.archfixture.stock.application.UsesInstantNow
import com.onemind.archfixture.stock.application.UsesKotlinSystemClock
import com.onemind.archfixture.stock.application.UsesNowInZone
import com.onemind.archfixture.stock.application.UsesSystemClock
import com.onemind.archfixture.stock.application.UsesSystemZone
import com.onemind.archfixture.stock.application.UsesTickingSystemClock
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves every product-rule gate catches its violation and lets the allowed shape pass (fixtures in `com.onemind.archfixture`). */
class ProductRulesTest {
    private val root = "com.onemind.archfixture"

    @Test
    fun `money names match whole words, singular or plural, and skip ratios and counts`() {
        assertEquals(listOf("get", "total", "price"), ProductRules.tokens("getTotalPrice"))
        assertEquals(listOf("amount", "minor"), ProductRules.tokens("amount_minor"))
        assertEquals(listOf("bill", "row", "companion"), ProductRules.tokens("BillRow\$Companion"))
        assertEquals(listOf("charge"), ProductRules.tokens("charge-NetTip4"))
        listOf(
            "totalPrice",
            "amountMinor",
            "PriceCalculator",
            "tips",
            "losses",
            "net_revenue",
            "averageBill",
            "refunds",
            "netSales",
            "subtotal",
        ).forEach { assertTrue(ProductRules.isMoneyName(it), it) }
        listOf("multiple", "costume", "tooltipOffset", "discountRate", "totalCount", "tipShare", "lossPct", "itemQuantity")
            .forEach { assertFalse(ProductRules.isMoneyName(it), it) }
    }

    @Test
    fun `the word lists equal the spec lint's (docs-api-lint-money-words json)`() {
        val words = JsonMapper.builder().build().readTree(File("../docs/api/lint/money-words.json"))
        assertEquals(ProductRules.MONEY_WORDS, words["moneyWords"].values().map { it.asString() }.toSet())
        assertEquals(ProductRules.NOT_MONEY_SUFFIXES, words["notMoneySuffixes"].values().map { it.asString() }.toSet())
    }

    @Test
    fun `rule 5 catches Float or Double money in fields, signatures, parameters and BigDecimal conversions`() {
        val rule = ProductRules.noFloatingPointMoney(root)
        listOf(
            ReceiptLine::class,
            PriceCalculator::class,
            Converter::class,
            Rounding::class,
            ShiftSummary::class,
            Dashboard::class,
            DayFigures::class,
            Charger::class,
            Ledger::class,
            InlineRounding::class,
        ).forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
    }

    @Test
    fun `rule 5 allows ratios, counts, value-class data classes and non-money doubles`() {
        assertFalse(ProductRules.noFloatingPointMoney(root).violatedBy(PaymentSplit::class, Occupancy::class))
    }

    @Test
    fun `rule 6 catches every read of the system clock or zone`() {
        val rule = ProductRules.noSystemTime(root)
        listOf(
            UsesInstantNow::class,
            UsesNowInZone::class,
            UsesSystemZone::class,
            UsesCurrentTimeMillis::class,
            UsesSystemClock::class,
            UsesTickingSystemClock::class,
            UsesGregorianCalendar::class,
            UsesKotlinSystemClock::class,
        ).forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
    }

    @Test
    fun `rule 6 allows the injected Clock and the one clock source`() {
        assertFalse(ProductRules.noSystemTime(root).violatedBy(UsesInjectedClock::class, ClockConfig::class))
    }

    @Test
    fun `rule 6 catches local dates from instants outside common time`() {
        val rule = ProductRules.localDatesOnlyInCommonTime(root)
        listOf(
            BucketsByCalendarDate::class,
            BucketsByOfInstant::class,
            BucketsByOffsetHour::class,
        ).forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
        assertFalse(rule.violatedBy(BusinessDays::class, UsesInjectedClock::class, MapsToOffsetDateTime::class))
    }

    @Test
    fun `rule 1 catches every mutating HTTP call in the pos module`() {
        val rule = ProductRules.posIsReadOnly(root)
        listOf(
            RestClientPosting::class,
            RestTemplateDeleting::class,
            RestTemplateExchanging::class,
            RestTemplateSubclassPosting::class,
            HttpMethodUsing::class,
            HttpMethodFromString::class,
            RequestEntityPosting::class,
            JdkHttpPutting::class,
            UrlConnectionPosting::class,
            PostingExchange::class,
            DeletingExchange::class,
            TypeLevelPostingExchange::class,
        ).forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
    }

    @Test
    fun `rule 1 allows GET in pos and leaves other modules alone`() {
        assertFalse(ProductRules.posIsReadOnly(root).violatedBy(GetOnlyClient::class, ReadingExchange::class, AccountPosting::class))
    }
}
