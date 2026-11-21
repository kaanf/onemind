package com.onemind.konsist

import kotlin.test.Test
import kotlin.test.assertTrue

/** The guards on the real mobile sources (and the generated API models): no offenders allowed. */
class GuardsTest {
    private val app = SourceFiles.app
    private val features = SourceFiles.features

    @Test
    fun packageMatchesModule() = assertNone(Guards.packageMatchesModule(app))

    @Test
    fun featuresDoNotImportEachOther() = assertNone(Guards.featuresDoNotImportEachOther(app, features))

    @Test
    fun domainImportsNoOuterLayers() = assertNone(Guards.domainImportsNoOuterLayers(app, features))

    @Test
    fun uiImportsNoData() = assertNone(Guards.uiImportsNoData(app, features))

    @Test
    fun apiModelsStayInData() = assertNone(Guards.apiModelsStayInData(app))

    @Test
    fun moneyIsNotFloatingPoint() = assertNone(Guards.moneyIsNotFloatingPoint(app + SourceFiles.apiModels))

    @Test
    fun moneyHasNoFloatingPointValues() = assertNone(Guards.moneyHasNoFloatingPointValues(app))

    @Test
    fun systemClockOnlyInSeam() = assertNone(Guards.systemClockOnlyInSeam(app))

    @Test
    fun deviceTimeZoneNeverUsed() = assertNone(Guards.deviceTimeZoneNeverUsed(app))

    private fun assertNone(offenders: List<String>) =
        assertTrue(offenders.isEmpty(), "Guard violations (never weaken the guard to pass):\n" + offenders.joinToString("\n"))
}
