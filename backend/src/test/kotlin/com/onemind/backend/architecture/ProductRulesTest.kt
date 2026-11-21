package com.onemind.backend.architecture

import com.onemind.archfixture.account.infra.client.AccountPosting
import com.onemind.archfixture.common.time.ClockConfig
import com.onemind.archfixture.pos.infra.client.DeletingExchange
import com.onemind.archfixture.pos.infra.client.GetOnlyClient
import com.onemind.archfixture.pos.infra.client.HttpMethodUsing
import com.onemind.archfixture.pos.infra.client.JdkHttpPutting
import com.onemind.archfixture.pos.infra.client.PostingExchange
import com.onemind.archfixture.pos.infra.client.ReadingExchange
import com.onemind.archfixture.pos.infra.client.RequestEntityPosting
import com.onemind.archfixture.pos.infra.client.RestClientPosting
import com.onemind.archfixture.pos.infra.client.RestTemplateDeleting
import com.onemind.archfixture.pos.infra.client.RestTemplateExchanging
import com.onemind.archfixture.reporting.domain.BillRow
import com.onemind.archfixture.reporting.domain.Converter
import com.onemind.archfixture.reporting.domain.Occupancy
import com.onemind.archfixture.reporting.domain.PaymentSplit
import com.onemind.archfixture.reporting.domain.PriceCalculator
import com.onemind.archfixture.reporting.domain.Rounding
import com.onemind.archfixture.stock.application.UsesCurrentTimeMillis
import com.onemind.archfixture.stock.application.UsesInjectedClock
import com.onemind.archfixture.stock.application.UsesInstantNow
import com.onemind.archfixture.stock.application.UsesNowInZone
import com.onemind.archfixture.stock.application.UsesSystemClock
import com.onemind.archfixture.stock.application.UsesSystemZone
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves every product-rule gate catches its violation and lets the allowed shape pass (fixtures in `com.onemind.archfixture`). */
class ProductRulesTest {
    private val root = "com.onemind.archfixture"

    @Test
    fun `money names match whole tokens and skip ratios and counts`() {
        assertEquals(listOf("get", "total", "price"), ProductRules.tokens("getTotalPrice"))
        assertEquals(listOf("amount", "minor"), ProductRules.tokens("amount_minor"))
        assertEquals(listOf("bill", "row", "companion"), ProductRules.tokens("BillRow\$Companion"))
        listOf(
            "totalPrice",
            "amountMinor",
            "PriceCalculator",
            "tip",
            "net_revenue",
        ).forEach { assertTrue(ProductRules.isMoneyName(it), it) }
        listOf("multiple", "costume", "discountRate", "totalCount", "tipShare", "lossPct", "itemQuantity").forEach {
            assertFalse(ProductRules.isMoneyName(it), it)
        }
    }

    @Test
    fun `rule 5 catches Float or Double money in fields, signatures and BigDecimal conversions`() {
        val rule = ProductRules.noFloatingPointMoney(root)
        listOf(BillRow::class, PriceCalculator::class, Converter::class, Rounding::class).forEach {
            assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}")
        }
    }

    @Test
    fun `rule 5 allows ratios, counts and non-money doubles`() {
        assertFalse(ProductRules.noFloatingPointMoney(root).violatedBy(PaymentSplit::class, Occupancy::class))
    }

    @Test
    fun `rule 6 catches every read of the system clock or zone`() {
        val rule = ProductRules.noSystemTime(root)
        listOf(UsesInstantNow::class, UsesNowInZone::class, UsesSystemZone::class, UsesCurrentTimeMillis::class, UsesSystemClock::class)
            .forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
    }

    @Test
    fun `rule 6 allows the injected Clock and the one clock source`() {
        assertFalse(ProductRules.noSystemTime(root).violatedBy(UsesInjectedClock::class, ClockConfig::class))
    }

    @Test
    fun `rule 1 catches every mutating HTTP call in the pos module`() {
        val rule = ProductRules.posIsReadOnly(root)
        listOf(
            RestClientPosting::class,
            RestTemplateDeleting::class,
            RestTemplateExchanging::class,
            HttpMethodUsing::class,
            RequestEntityPosting::class,
            JdkHttpPutting::class,
            PostingExchange::class,
            DeletingExchange::class,
        ).forEach { assertTrue(rule.violatedBy(it), "not caught: ${it.simpleName}") }
    }

    @Test
    fun `rule 1 allows GET in pos and leaves other modules alone`() {
        assertFalse(ProductRules.posIsReadOnly(root).violatedBy(GetOnlyClient::class, ReadingExchange::class, AccountPosting::class))
    }

    private fun ArchRule.violatedBy(vararg types: KClass<*>): Boolean {
        val classes = ClassFileImporter().importClasses(*types.map { it.java }.toTypedArray())
        return evaluate(classes).hasViolation()
    }
}
