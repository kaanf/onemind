package com.onemind.core.api.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Enum values added to the API after this app version read as `UNKNOWN` (templates in
 * `core/api/templates`), so one new value doesn't fail the whole response.
 */
class UnknownEnumTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun knownValuesStillDecode() {
        assertEquals(DataStatus.READY, json.decodeFromString<DataStatus>("\"READY\""))
        assertEquals(HourlyBar.Kind.FORECAST, json.decodeFromString<HourlyBar>("""{"hour": 22, "kind": "FORECAST"}""").kind)
    }

    @Test
    fun anUnknownTopLevelEnumValueIsUnknown() {
        assertEquals(DataStatus.UNKNOWN, json.decodeFromString<DataStatus>("\"PAUSED\""))
    }

    @Test
    fun anUnknownValueInAListKeepsTheOthers() {
        val figures = json.decodeFromString<List<UnavailableFigure>>("""["OPEN_TABS", "SEATED_NOW"]""")

        assertEquals(listOf(UnavailableFigure.OPEN_TABS, UnavailableFigure.UNKNOWN), figures)
    }

    @Test
    fun anUnknownValueOfAnEnumInsideAModelIsUnknown() {
        val bar = json.decodeFromString<HourlyBar>("""{"hour": 22, "kind": "ESTIMATE"}""")
        val loss = json.decodeFromString<LossComponent>("""{"kind": "VOIDS", "amount": {"amountMinor": 100, "currency": "CZK"}}""")

        assertEquals(HourlyBar.Kind.UNKNOWN, bar.kind)
        assertEquals(LossComponent.Kind.UNKNOWN, loss.kind)
    }

    @Test
    fun theWireValueIsNotTheUnknownName() {
        // A literal "UNKNOWN" from the server is just another unknown value, never a match by name.
        assertEquals(DataStatus.UNKNOWN, json.decodeFromString<DataStatus>("\"UNKNOWN\""))
        assertEquals("\"PAST\"", json.encodeToString(HourlyBar.Kind.serializer(), HourlyBar.Kind.PAST))
    }

    @Test
    fun unknownIsNeverSent() {
        assertFailsWith<SerializationException> { json.encodeToString(DataStatus.serializer(), DataStatus.UNKNOWN) }
        assertFailsWith<SerializationException> { json.encodeToString(HourlyBar.Kind.serializer(), HourlyBar.Kind.UNKNOWN) }
    }
}
