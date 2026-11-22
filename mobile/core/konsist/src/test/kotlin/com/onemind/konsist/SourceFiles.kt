package com.onemind.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File

/** Architectural layer of a source file, taken from its Gradle module path (never from its package). */
enum class Layer {
    /** `core/domain`, `feature/<x>/domain`. */
    Domain,

    /** `core/data`, `feature/<x>/data`. */
    Data,

    /** `core/api`: generated DTOs and their tests. */
    Api,

    /** `feature/<x>/presentation`, `core/presentation`, `core/designsystem`. */
    Ui,

    /** `composeApp`: navigation root and Koin wiring; may see every module. */
    App,

    /** `androidApp`: thin Android host. */
    Host,
}

/**
 * A Kotlin file with the facts the guards need. [module] is the Gradle path below `mobile/`
 * (`feature/sales/domain`), [feature] the feature name for `feature/<x>/…`, [expectedPackage] the
 * package prefix the module's files must use.
 */
data class SourceFile(
    val file: KoFileDeclaration,
    val relativePath: String,
    val module: String,
    val layer: Layer?,
    val feature: String?,
    val expectedPackage: String?,
) {
    val packageName: String get() = file.packagee?.name.orEmpty()

    /** Source with comments and string literals blanked, so line scans see code only. */
    val code: String by lazy { stripCommentsAndStrings(file.text) }

    /** Source with comments blanked but string literals kept (for rules about literals themselves). */
    val codeWithStrings: String by lazy { stripComments(file.text) }

    /** App code, not tests: `src/<sourceSet>Main/…`. */
    val isMainSource: Boolean get() = Regex("/src/[A-Za-z]*Main/").containsMatchIn(relativePath)

    override fun toString() = relativePath
}

object SourceFiles {
    private val mobileDir =
        File(requireNotNull(System.getProperty("onemind.mobileDir")) { "run through Gradle" }).canonicalFile

    /** Feature names from `mobile/feature/<name>`. */
    val features: List<String> =
        mobileDir
            .resolve("feature")
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { it.name }
            .sorted()

    /** App and test sources of every module, without Gradle build logic and these guard tests. */
    val app: List<SourceFile> by lazy {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.endsWith(".kt") && "/build-logic/" !in it.path && "/core/konsist/" !in it.path }
            .map { classify(it, mobileDir) }
    }

    /** Models generated from `docs/api/openapi.yaml` (not committed, built before the tests run). */
    val apiModels: List<SourceFile> by lazy {
        Konsist.scopeFromDirectory("core/api/build/generated/openapi").files.map { classify(it, mobileDir) }
    }

    /** Fixture sources for the guards' own tests, laid out like `mobile/` (not compiled). */
    val fixtures: List<SourceFile> by lazy {
        val root = mobileDir.resolve(FIXTURE_DIR)
        Konsist.scopeFromDirectory(FIXTURE_DIR).files.map { classify(it, root) }
    }

    private const val FIXTURE_DIR = "core/konsist/src/test/fixtures"

    fun classify(
        file: KoFileDeclaration,
        root: File,
    ): SourceFile {
        val relativePath = File(file.path).canonicalFile.relativeTo(root).invariantSeparatorsPath
        val module = relativePath.substringBefore("/src/").substringBefore("/build/")
        val parts = module.split('/')
        val feature = if (parts.size == 3 && parts[0] == "feature") parts[1] else null
        val layer =
            when {
                module == "core/domain" || (feature != null && parts[2] == "domain") -> Layer.Domain
                module == "core/data" || (feature != null && parts[2] == "data") -> Layer.Data
                module == "core/api" -> Layer.Api
                module == "core/presentation" || module == "core/designsystem" -> Layer.Ui
                feature != null && parts[2] == "presentation" -> Layer.Ui
                module == "composeApp" -> Layer.App
                module == "androidApp" -> Layer.Host
                else -> null
            }
        val expectedPackage =
            when {
                parts.size == 2 && parts[0] == "core" -> "com.onemind.core.${parts[1]}"
                feature != null -> "com.onemind.$feature.${parts[2]}"
                module == "composeApp" -> "com.onemind.composeapp"
                module == "androidApp" -> "com.onemind.app"
                else -> null
            }
        return SourceFile(file, relativePath, module, layer, feature, expectedPackage)
    }
}

private val tripleQuoted = Regex("\"\"\"[\\s\\S]*?\"\"\"")
private val blockComment = Regex("""/\*[\s\S]*?\*/""")
private val stringLiteral = Regex(""""(?:\\.|[^"\\\n])*"""")
private val charLiteral = Regex("""'(?:\\.|[^'\\\n])'""")
private val lineComment = Regex("""//[^\n]*""")

private val token = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])'|/\\*[\\s\\S]*?\\*/|//[^\\n]*")

/**
 * Blanks comments only, keeping strings, line breaks and offsets. One left-to-right scan over strings and
 * comments together, so `"//"` inside a string is not a comment and `"…"` inside a comment is not a string.
 */
fun stripComments(text: String): String =
    token.replace(text) { match ->
        if (match.value.startsWith("/")) match.value.replace(Regex("[^\n]"), " ") else match.value
    }

/** Blanks comments and string/char literals, keeping line breaks so line numbers stay right. */
fun stripCommentsAndStrings(text: String): String {
    fun blank(match: MatchResult) = match.value.replace(Regex("[^\n]"), " ")
    return text
        .replace(tripleQuoted, ::blank)
        .replace(stringLiteral, ::blank)
        .replace(charLiteral, ::blank)
        .replace(blockComment, ::blank)
        .replace(lineComment, ::blank)
}
