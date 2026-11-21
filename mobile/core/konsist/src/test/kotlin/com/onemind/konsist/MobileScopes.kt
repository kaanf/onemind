package com.onemind.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File

/** Scopes shared by the guard tests. The Konsist project root is `mobile/`. */
object MobileScopes {
    private val mobileDir = File(requireNotNull(System.getProperty("onemind.mobileDir")) { "run through Gradle" })

    /** Feature names from `mobile/feature/<name>`; their code lives in `com.onemind.<name>..`. */
    val features: List<String> =
        mobileDir
            .resolve("feature")
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { it.name }
            .sorted()

    /** App and test sources of every module, without Gradle build logic and these guard tests. */
    val app: KoScope by lazy {
        Konsist.scopeFromProject().slice { file ->
            !file.path.contains("/build-logic/") && !file.path.contains("/core/konsist/") && file.path.endsWith(".kt")
        }
    }

    /** Models generated from `docs/api/openapi.yaml` (not committed, built before the tests run). */
    val apiModels: KoScope by lazy { Konsist.scopeFromDirectory("core/api/build/generated/openapi") }

    fun KoFileDeclaration.inPackage(prefix: String): Boolean = packagee?.name?.let { it == prefix || it.startsWith("$prefix.") } == true

    /** `core.domain` and every feature's `domain` layer. */
    fun KoFileDeclaration.isDomain(): Boolean = inPackage("com.onemind.core.domain") || features.any { inPackage("com.onemind.$it.domain") }

    /** `core.data` and every feature's `data` layer. */
    fun KoFileDeclaration.isData(): Boolean = inPackage("com.onemind.core.data") || features.any { inPackage("com.onemind.$it.data") }

    fun KoFileDeclaration.isPresentation(): Boolean = features.any { inPackage("com.onemind.$it.presentation") }
}
