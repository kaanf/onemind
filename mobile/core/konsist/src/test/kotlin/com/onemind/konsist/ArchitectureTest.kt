package com.onemind.konsist

import com.lemonappdev.konsist.api.verify.assertFalse
import com.onemind.konsist.MobileScopes.inPackage
import com.onemind.konsist.MobileScopes.isData
import com.onemind.konsist.MobileScopes.isDomain
import kotlin.test.Test

/** Layer and module rules of ADR 0011 and `mobile/AGENTS.md`. */
class ArchitectureTest {
    private val files = MobileScopes.app.files

    @Test
    fun featuresDoNotImportEachOther() {
        // Only composeApp wires features together (cross-feature transitions are callbacks).
        MobileScopes.features.forEach { feature ->
            val others = MobileScopes.features - feature
            files.filter { it.inPackage("com.onemind.$feature") }.assertFalse(testName = "featuresDoNotImportEachOther/$feature") { file ->
                file.imports.any { import -> others.any { import.name.startsWith("com.onemind.$it.") } }
            }
        }
    }

    @Test
    fun domainImportsNoDataPresentationOrFrameworks() {
        val forbidden =
            listOf("com.onemind.core.api.", "com.onemind.core.data.", "io.ktor.", "org.koin.", "androidx.datastore.", "androidx.compose.") +
                MobileScopes.features.flatMap { listOf("com.onemind.$it.data.", "com.onemind.$it.presentation.") }
        files.filter { it.isDomain() }.assertFalse { file ->
            file.imports.any { import -> forbidden.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun generatedApiModelsStayInData() {
        // DTOs never leave data: presentation, domain and composeApp use domain models.
        files.filterNot { it.isData() || it.inPackage("com.onemind.core.api") }.assertFalse { file ->
            file.imports.any { it.name.startsWith("com.onemind.core.api.") }
        }
    }
}
