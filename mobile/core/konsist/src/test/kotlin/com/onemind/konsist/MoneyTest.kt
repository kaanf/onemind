package com.onemind.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.onemind.konsist.MobileScopes.isData
import com.onemind.konsist.MobileScopes.isDomain
import com.onemind.konsist.MobileScopes.isPresentation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Product rule 5, money is exact: minor units (`Long`) or decimal strings, never `Float`/`Double`.
 * Domain, data and the generated API models have no floating-point types or conversions at all;
 * presentation may use them for drawing (chart heights) but not on money-named declarations.
 */
class MoneyTest {
    private val floatingPoint = Regex("""\b(Float|Double)\b""")
    private val floatingPointUse = Regex("""\b(Float|Double)\b|\.to(Float|Double)(OrNull)?\s*\(""")
    private val moneyName = Regex("amount|price|revenue|total|cost|tip|money|minor", RegexOption.IGNORE_CASE)

    @Test
    fun domainDataAndApiModelsHaveNoFloatingPoint() {
        // Line scan, so local variables, generics and conversions are caught too.
        val files = MobileScopes.app.files.filter { it.isDomain() || it.isData() } + MobileScopes.apiModels.files
        val offenders =
            files.flatMap { file ->
                file.text.lineSequence().mapIndexedNotNull { index, line ->
                    if (floatingPointUse.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }
        assertTrue(offenders.isEmpty(), "Float/Double in domain, data or API models:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun presentationMoneyIsNotFloatingPoint() {
        val offenders =
            typedDeclarations(MobileScopes.app.slice { it.isPresentation() })
                .filter { moneyName.containsMatchIn(it.name) && floatingPoint.containsMatchIn(it.type) }
        assertTrue(offenders.isEmpty(), "Money-named Float/Double in presentation:\n" + offenders.joinToString("\n"))
    }

    private data class Typed(
        val location: String,
        val name: String,
        val type: String,
    ) {
        override fun toString() = "$location $name: $type"
    }

    /** Properties, constructor and function parameters and function return types. */
    private fun typedDeclarations(scope: KoScope): List<Typed> {
        val properties =
            scope.properties(includeNested = true).mapNotNull { property ->
                property.type?.let { Typed(property.location, property.name, it.text) }
            }
        val constructorParameters =
            scope.classes(includeNested = true).flatMap { it.constructors }.flatMap { it.parameters }.map {
                Typed(it.location, it.name, it.type.text)
            }
        val functions = scope.functions(includeNested = true)
        val functionParameters = functions.flatMap { it.parameters }.map { Typed(it.location, it.name, it.type.text) }
        val returnTypes = functions.mapNotNull { function -> function.returnType?.let { Typed(function.location, function.name, it.text) } }
        return properties + constructorParameters + functionParameters + returnTypes
    }
}
