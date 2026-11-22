package com.onemind.core.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `format: date` fields are generated as `kotlinx.datetime.LocalDate` (`dateLibrary = kotlinx-datetime`).
 * This shape mirrors such a model, so the dependency and its default serializer are proven before the
 * first dated schema (the business day) lands in the spec.
 */
class DateFieldTest {
    @Serializable
    private data class Dated(
        val businessDay: LocalDate,
    )

    @Test
    fun isoDatesRoundTrip() {
        val json = """{"businessDay":"2026-11-06"}"""
        val decoded = Json.decodeFromString<Dated>(json)

        assertEquals(LocalDate(2026, 11, 6), decoded.businessDay)
        assertEquals(json, Json.encodeToString(Dated.serializer(), decoded))
    }
}
