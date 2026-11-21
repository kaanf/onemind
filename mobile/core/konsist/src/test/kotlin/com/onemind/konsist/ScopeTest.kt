package com.onemind.konsist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guards pass trivially on an empty scope, so prove the scopes see the code they guard. */
class ScopeTest {
    @Test
    fun featuresAreFound() {
        assertEquals(listOf("auth", "sales", "staff", "today"), SourceFiles.features)
    }

    @Test
    fun everyAppFileHasALayerAndAPackageRule() {
        val unclassified = SourceFiles.app.filter { it.layer == null || it.expectedPackage == null }
        assertTrue(unclassified.isEmpty(), "Add the module to SourceFiles.classify:\n" + unclassified.joinToString("\n"))
    }

    @Test
    fun appScopeContainsEveryLayer() {
        val layers = SourceFiles.app.mapNotNull { it.layer }.toSet()
        assertEquals(Layer.entries.toSet(), layers)
        assertTrue(SourceFiles.app.any { it.relativePath == Guards.CLOCK_SEAM }, "clock seam moved: update Guards.CLOCK_SEAM")
        assertTrue(SourceFiles.app.none { "/build/" in it.relativePath || it.relativePath.startsWith("core/konsist/") })
    }

    @Test
    fun apiScopeContainsGeneratedModels() {
        assertTrue(SourceFiles.apiModels.any { file -> file.file.classes().any { it.name == "Problem" } }, "core:api models")
        assertTrue(SourceFiles.apiModels.all { it.layer == Layer.Api })
    }
}
