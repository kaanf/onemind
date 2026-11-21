package com.onemind.konsist

import com.onemind.konsist.MobileScopes.isData
import com.onemind.konsist.MobileScopes.isDomain
import com.onemind.konsist.MobileScopes.isPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guards pass trivially on an empty scope, so prove the scopes see the code they guard. */
class ScopeTest {
    @Test
    fun featuresAreFound() {
        assertEquals(listOf("auth", "sales", "staff", "today"), MobileScopes.features)
    }

    @Test
    fun appScopeContainsEveryLayer() {
        val files = MobileScopes.app.files
        assertTrue(files.any { it.path.endsWith("/composeapp/navigation/NavigationRoot.kt") }, "composeApp")
        assertTrue(files.any { it.isDomain() }, "domain")
        assertTrue(files.any { it.isData() }, "data")
        assertTrue(files.any { it.isPresentation() }, "presentation")
        assertTrue(files.none { it.path.contains("/build/") || it.path.contains("/core/konsist/") }, "no build output or guards")
    }

    @Test
    fun apiScopeContainsGeneratedModels() {
        assertTrue(MobileScopes.apiModels.classes().any { it.name == "Problem" }, "core:api models")
    }
}
