package com.onemind.konsist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * The money word lists shared with the backend (`docs/api/lint/money-words.json`, product rule 5).
 * Entries are read as data, so a new word or suffix needs no Kotlin change. An array key this class
 * doesn't know fails the guards, so a new kind of list can't be ignored silently on mobile.
 */
data class MoneyWords(
    val moneyWords: Set<String>,
    val notMoneySuffixes: Set<String>,
) {
    companion object {
        private val knownArrays = setOf("moneyWords", "notMoneySuffixes")

        /** The repo's file; Gradle passes its path and declares it as a test input. */
        val shared: MoneyWords by lazy {
            val path = requireNotNull(System.getProperty("onemind.moneyWords")) { "run through Gradle" }
            parse(File(path).readText())
        }

        fun parse(json: String): MoneyWords {
            val root = Json.parseToJsonElement(json).jsonObject
            val unknown = root.filter { (key, value) -> value is JsonArray && key !in knownArrays }.keys
            require(unknown.isEmpty()) { "money-words.json has lists the mobile guard doesn't read: $unknown" }
            return MoneyWords(root.words("moneyWords"), root.words("notMoneySuffixes"))
        }

        private fun JsonObject.words(key: String): Set<String> {
            val array = requireNotNull(this[key] as? JsonArray) { "money-words.json has no '$key' list" }
            val words = array.map { requireNotNull((it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content) { "'$key': $it" } }
            require(words.isNotEmpty()) { "money-words.json '$key' is empty" }
            return words.map { it.lowercase() }.toSet()
        }
    }
}
