package com.onemind.konsist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The money guard reads its words from `docs/api/lint/money-words.json`, not from a copy in Kotlin. */
class MoneyWordsTest {
    private val fixture by lazy {
        val mobileDir = File(requireNotNull(System.getProperty("onemind.mobileDir")) { "run through Gradle" })
        MoneyWords.parse(mobileDir.resolve("core/konsist/src/test/fixtures/money-words.json").readText())
    }

    @Test
    fun aWordOnlyInTheFileIsMoney() {
        assertTrue(MoneyNames.isMoney("zzqmoneyTotal", fixture))
        assertTrue(MoneyNames.isMoney("zzqmoneys", fixture))
        assertTrue(!MoneyNames.isMoney("zzqmoney"), "'zzqmoney' is not in the repo's list")
    }

    @Test
    fun aSuffixOnlyInTheFileExcludes() {
        assertTrue(!MoneyNames.isMoney("amountZzqsuffix", fixture))
        assertTrue(MoneyNames.isMoney("amountZzqsuffix"), "'zzqsuffix' is not a suffix in the repo's list")
    }

    @Test
    fun everyEntryOfTheSharedFileIsEnforced() {
        val shared = MoneyWords.shared
        shared.moneyWords.forEach { word ->
            listOf(word, word + "s", "paid_$word").forEach { assertTrue(MoneyNames.isMoney(it), it) }
            shared.notMoneySuffixes.forEach { suffix -> assertTrue(!MoneyNames.isMoney(word + suffix.capitalize()), "$word$suffix") }
        }
    }

    @Test
    fun anUnknownListFails() {
        val json = """{"moneyWords": ["amount"], "notMoneySuffixes": ["rate"], "notMoneyLastWords": ["id"]}"""
        assertFailsWith<IllegalArgumentException> { MoneyWords.parse(json) }
        val nested = """{"moneyWords": ["amount"], "notMoneySuffixes": ["rate"], "more": {"suffixes": ["id"]}}"""
        assertFailsWith<IllegalArgumentException> { MoneyWords.parse(nested) }
        val flag = """{"moneyWords": ["amount"], "notMoneySuffixes": ["rate"], "strict": true}"""
        assertFailsWith<IllegalArgumentException> { MoneyWords.parse(flag) }
    }

    @Test
    fun aStringNoteIsAllowed() =
        assertEquals(
            setOf("rate"),
            MoneyWords.parse("""{"comment": "x", "moneyWords": ["amount"], "notMoneySuffixes": ["rate"]}""").notMoneySuffixes,
        )

    @Test
    fun aMissingOrEmptyListFails() {
        assertFailsWith<IllegalArgumentException> { MoneyWords.parse("""{"moneyWords": ["amount"]}""") }
        assertFailsWith<IllegalArgumentException> { MoneyWords.parse("""{"moneyWords": [], "notMoneySuffixes": ["rate"]}""") }
    }

    @Test
    fun theFixtureParses() = assertEquals(setOf("amount", "total", "zzqmoney"), fixture.moneyWords)

    private fun String.capitalize() = replaceFirstChar { it.uppercase() }
}
