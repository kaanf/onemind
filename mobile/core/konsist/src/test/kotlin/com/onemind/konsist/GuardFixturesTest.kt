package com.onemind.konsist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every guard on planted violations (`src/test/fixtures`, laid out like `mobile/`, not compiled):
 * it must report exactly the violations and none of the compliant lookalikes next to them.
 */
class GuardFixturesTest {
    private val fixtures = SourceFiles.fixtures
    private val features = listOf("auth", "sales", "staff", "today")

    @Test
    fun packageMatchesModuleCatchesAMisplacedFile() =
        assertOffenders(Guards.packageMatchesModule(fixtures), "MisplacedPackage.kt: package 'com.onemind.feature.sales.domain'")

    @Test
    fun featuresDoNotImportEachOther() =
        assertOffenders(Guards.featuresDoNotImportEachOther(fixtures, features), "CrossLayer.kt: import com.onemind.sales.presentation")

    @Test
    fun domainLayerComesFromThePathNotThePackage() =
        assertOffenders(Guards.domainImportsNoOuterLayers(fixtures, features), "MisplacedPackage.kt: import io.ktor.client.HttpClient")

    @Test
    fun uiImportsNoData() = assertOffenders(Guards.uiImportsNoData(fixtures, features), "CrossLayer.kt: import com.onemind.today.data")

    @Test
    fun apiModelsStayInData() =
        assertOffenders(Guards.apiModelsStayInData(fixtures), "CrossLayer.kt: import com.onemind.core.api.model.Problem")

    @Test
    fun moneyIsNotFloatingPointInEveryLayer() =
        assertOffenders(
            Guards.moneyIsNotFloatingPoint(fixtures),
            "MisplacedPackage.kt:8:5 amount: Double",
            "MoneyNames.kt:5:17 amount: Double",
            "MoneyNames.kt:7:1 possibleLoss: Double",
            "MoneyNames.kt:9:1 averageBill: Float",
            "ChartTotal.kt:5:5 revenue: Double",
        )

    @Test
    fun moneyHasNoFloatingPointValuesInDomainAndData() =
        assertOffenders(
            Guards.moneyHasNoFloatingPointValues(fixtures),
            "Vat.kt:4: fun vatMinor",
            "Vat.kt:6: fun toMajor",
            "Vat.kt:8: fun tipRatio",
        )

    @Test
    fun systemClockOnlyInSeam() =
        assertOffenders(
            Guards.systemClockOnlyInSeam(fixtures),
            "SystemTime.kt: import kotlin.time.Clock.System",
            "SystemTime.kt:9: fun viaImport",
            "SystemTime.kt:11: fun viaClass",
        )

    @Test
    fun deviceTimeZoneNeverUsed() = assertOffenders(Guards.deviceTimeZoneNeverUsed(fixtures), "SystemTime.kt:13: fun deviceZone")

    @Test
    fun moneyNamesMatchWholeTokens() {
        listOf("amount", "paidRevenue", "possibleLoss", "averageBill", "tips", "amountMinor", "net_total", "PRICE").forEach {
            assertTrue(MoneyNames.isMoney(it), it)
        }
        listOf("tooltipWidth", "multiplier", "totalHeight", "cardRate", "discountPct", "tipsShare", "guestCount", "network").forEach {
            assertTrue(!MoneyNames.isMoney(it), it)
        }
    }

    private fun assertOffenders(
        actual: List<String>,
        vararg expected: String,
    ) {
        val unmatched = expected.filter { e -> actual.none { e in it } }
        assertTrue(unmatched.isEmpty(), "Missing offenders $unmatched in:\n" + actual.joinToString("\n"))
        assertEquals(expected.size, actual.size, "Unexpected offenders:\n" + actual.joinToString("\n"))
    }
}
