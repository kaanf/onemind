package com.onemind.konsist

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * The guard rules as functions over source files, returning offenders (`path:line: …`). `GuardsTest`
 * runs them on the app and expects none; `GuardFixturesTest` runs them on fixtures and expects the
 * planted violations, so every rule is proven to bite.
 */
object Guards {
    // --- Architecture (ADR 0011, mobile/AGENTS.md) -------------------------------------------------

    /** Every file's package matches its Gradle module, so path-based layers and packages can't drift. */
    fun packageMatchesModule(files: List<SourceFile>): List<String> =
        files.filter { it.expectedPackage != null && !it.packageName.isUnder(it.expectedPackage) }.map {
            "$it: package '${it.packageName}' is not under '${it.expectedPackage}'"
        }

    /** Features never import another feature; only composeApp wires them together. */
    fun featuresDoNotImportEachOther(
        files: List<SourceFile>,
        features: List<String>,
    ): List<String> =
        files.filter { it.feature != null }.flatMap { file ->
            val others = features - file.feature!!
            file.importsMatching { import -> others.any { import.isUnder("com.onemind.$it") } }
        }

    /** Domain layers depend only on `core:domain`, coroutines and kotlinx-datetime. */
    fun domainImportsNoOuterLayers(
        files: List<SourceFile>,
        features: List<String>,
    ): List<String> {
        val forbidden =
            listOf(
                "com.onemind.core.api",
                "com.onemind.core.data",
                "com.onemind.core.presentation",
                "com.onemind.core.designsystem",
                "com.onemind.composeapp",
                "io.ktor",
                "org.koin",
                "androidx",
                "android",
                "org.jetbrains.compose",
            ) + features.flatMap { listOf("com.onemind.$it.data", "com.onemind.$it.presentation") }
        return files.filter { it.layer == Layer.Domain }.flatMap { file ->
            file.importsMatching { import -> forbidden.any { import.isUnder(it) } }
        }
    }

    /** Presentation never reaches into data (repositories come through domain interfaces and Koin). */
    fun uiImportsNoData(
        files: List<SourceFile>,
        features: List<String>,
    ): List<String> {
        val forbidden = listOf("com.onemind.core.data") + features.map { "com.onemind.$it.data" }
        return files.filter { it.layer == Layer.Ui }.flatMap { file ->
            file.importsMatching { import -> forbidden.any { import.isUnder(it) } }
        }
    }

    /** Generated DTOs never leave data: only data layers (and `core:api` itself) import them. */
    fun apiModelsStayInData(files: List<SourceFile>): List<String> =
        files.filter { it.layer != Layer.Data && it.layer != Layer.Api }.flatMap { file ->
            file.importsMatching { it.isUnder("com.onemind.core.api") }
        }

    // --- Money (product rule 5) -----------------------------------------------------------------

    private val floatingPoint = Regex("""\b(Float|Double)\b""")
    private val floatingPointValue =
        Regex("""\b\d+\.\d+([eE][+-]?\d+)?[fF]?\b|\b\d+[fF]\b|\.to(Float|Double)(OrNull)?\s*\(""")
    private val identifier = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")

    /**
     * Money-named declarations (see [MoneyNames]) are never `Float`/`Double`, in every layer. Covers
     * properties, constructor and function parameters and return types with an explicit type;
     * inferred types are caught in domain and data by [moneyHasNoFloatingPointValues].
     */
    fun moneyIsNotFloatingPoint(files: List<SourceFile>): List<String> =
        files.flatMap { file ->
            typedDeclarations(file.file)
                .filter { (_, name, type) -> MoneyNames.isMoney(name) && floatingPoint.containsMatchIn(type) }
                .map { (location, name, type) -> "$file:${location.split(':').takeLast(2).joinToString(":")} $name: $type" }
        }

    /**
     * In domain and data, a line that names money has no floating-point literal (`0.21`, `100.0`,
     * `1.5f`) or conversion (`toDouble()`). Presentation is exempt: drawing scales money to `Float`
     * (bar heights) and formatting goes through the money formatters.
     */
    fun moneyHasNoFloatingPointValues(files: List<SourceFile>): List<String> =
        files.filter { it.layer == Layer.Domain || it.layer == Layer.Data }.flatMap { file ->
            file.linesMatching { line ->
                floatingPointValue.containsMatchIn(line) && identifier.findAll(line).any { MoneyNames.isMoney(it.value) }
            }
        }

    // --- Time (product rule 6) ------------------------------------------------------------------

    /** The one file allowed to read the system clock: it binds `Clock.System` as `kotlin.time.Clock`. */
    const val CLOCK_SEAM = "core/data/src/commonMain/kotlin/com/onemind/core/data/di/CoreDataModule.kt"

    private val systemClockCode =
        listOf(
            Regex("""\bClock\s*\.\s*System\b"""),
            Regex("""\bSystem\s*\.\s*now\s*\("""),
            Regex("""\bSystem\s*\.\s*(currentTimeMillis|nanoTime)\s*\("""),
            Regex("""\bNSDate\s*(\(\s*\)|\.\s*date\s*\(|\.\s*now\b)"""),
            Regex("""\bCalendar\s*\.\s*getInstance\s*\("""),
            Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\s*\.\s*now\s*\("""),
        )
    private val systemClockImports = listOf("kotlin.time.Clock.System", "kotlinx.datetime.Clock", "java.util.Date", "java.util.Calendar")

    /** Only [CLOCK_SEAM] reads the system clock; everything else injects `kotlin.time.Clock`. */
    fun systemClockOnlyInSeam(files: List<SourceFile>): List<String> =
        files.filter { it.relativePath != CLOCK_SEAM }.flatMap { file ->
            file.importsMatching { import -> systemClockImports.any { import.isUnder(it) } } +
                file.linesMatching { line -> systemClockCode.any { it.containsMatchIn(line) } }
        }

    private val deviceTimeZoneCode =
        listOf(
            Regex("""\bTimeZone\s*\.\s*(currentSystemDefault|getDefault)\s*\("""),
            Regex("""\bZoneId\s*\.\s*systemDefault\s*\("""),
            Regex("""\bNSTimeZone\s*\.\s*(localTimeZone|systemTimeZone|defaultTimeZone)\b"""),
        )

    /** The device time zone is never used: times are shown in the venue's time zone from the API. */
    fun deviceTimeZoneNeverUsed(files: List<SourceFile>): List<String> =
        files.flatMap { file -> file.linesMatching { line -> deviceTimeZoneCode.any { it.containsMatchIn(line) } } }

    // --- helpers ----------------------------------------------------------------------------------

    private fun String.isUnder(prefix: String) = this == prefix || startsWith("$prefix.")

    private fun SourceFile.importsMatching(predicate: (String) -> Boolean): List<String> =
        file.imports.filter { predicate(it.name) }.map { "$this: import ${it.name}" }

    private fun SourceFile.linesMatching(predicate: (String) -> Boolean): List<String> =
        code
            .lineSequence()
            .withIndex()
            .filter { (_, line) -> !line.isHeader() && predicate(line) }
            .map { (index, _) ->
                "$this:${index + 1}: ${file.text.lines()[index].trim()}"
            }.toList()

    /** Imports are checked symbol by symbol in [importsMatching]; don't report them again as lines. */
    private fun String.isHeader() = trimStart().let { it.startsWith("import ") || it.startsWith("package ") }

    private fun typedDeclarations(file: KoFileDeclaration): List<Triple<String, String, String>> {
        val properties = file.properties(includeNested = true).mapNotNull { p -> p.type?.let { Triple(p.location, p.name, it.text) } }
        val constructorParameters =
            file.classes(includeNested = true).flatMap { it.constructors }.flatMap { it.parameters }.map {
                Triple(it.location, it.name, it.type.text)
            }
        val functions = file.functions(includeNested = true)
        val functionParameters = functions.flatMap { it.parameters }.map { Triple(it.location, it.name, it.type.text) }
        val returnTypes = functions.mapNotNull { f -> f.returnType?.let { Triple(f.location, f.name, it.text) } }
        // A constructor `val` is both a property and a parameter: report it once.
        return (properties + constructorParameters + functionParameters + returnTypes).distinctBy {
            it.first.substringBeforeLast(':') to it.second
        }
    }
}

/**
 * Money-named identifiers, matched by whole camelCase / snake_case tokens (the same rule as the
 * backend guards): `amount`, `paidRevenue`, `possibleLoss`, `averageBill` are money; `tooltipWidth`,
 * `multiplier`, `cardRate`, `discountPct`, `guestCount` are not. Ratios, percentages and counts are raw
 * numbers by contract (`docs/api/README.md`), and sizes are drawing values.
 */
object MoneyNames {
    // Same lists as the backend (docs/api/lint/money-words.json, PR #43): singular words, and a plural
    // s/es also matches (`prices`, `losses`, `salesByHour`).
    private val moneyWords =
        setOf(
            "money",
            "amount",
            "price",
            "total",
            "subtotal",
            "revenue",
            "sales",
            "cost",
            "tip",
            "discount",
            "loss",
            "payment",
            "refund",
            "bill",
            "net",
            "gross",
            "minor",
        )
    private val notMoneySuffixes = setOf("rate", "ratio", "percent", "pct", "share", "count", "quantity")

    // UI-only (not in the backend list): drawing values such as `totalHeight` or `revenueFraction`.
    private val drawingSuffixes = setOf("height", "width", "fraction")

    private val tokenBoundary = Regex("""_+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])(?=[0-9])""")

    fun tokens(name: String): List<String> = name.split(tokenBoundary).filter { it.isNotEmpty() }.map { it.lowercase() }

    fun isMoney(name: String): Boolean {
        val tokens = tokens(name)
        val last = tokens.last()
        return tokens.any { it.isWordIn(moneyWords) } && !last.isWordIn(notMoneySuffixes) && !last.isWordIn(drawingSuffixes)
    }

    /** The token is one of [words], or its plural with `s` / `es`. */
    private fun String.isWordIn(words: Set<String>) =
        this in words || (endsWith("s") && dropLast(1) in words) || (endsWith("es") && dropLast(2) in words)
}
