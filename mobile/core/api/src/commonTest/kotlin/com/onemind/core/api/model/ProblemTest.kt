package com.onemind.core.api.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ProblemTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesProblemWithFieldErrorsParamsAndUnknownMembers() {
        val body =
            """
            {
              "type": "https://onemind.app/problems/validation-failed",
              "title": "Validation failed",
              "status": 400,
              "code": "VALIDATION_FAILED",
              "detail": "One or more fields are invalid.",
              "params": { "max": 10, "unit": "pcs" },
              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
              "errors": [ { "field": "lines[2].quantity", "code": "POSITIVE" } ],
              "addedLater": true
            }
            """.trimIndent()

        val problem = json.decodeFromString<Problem>(body)

        assertEquals("VALIDATION_FAILED", problem.code)
        assertEquals(400, problem.status)
        assertEquals(listOf(FieldError(field = "lines[2].quantity", code = "POSITIVE")), problem.errors)
        assertEquals(JsonPrimitive(10), problem.params?.get("max"))
    }

    @Test
    fun decodesMoneyAsExactMinorUnits() {
        val money = json.decodeFromString<Money>("""{ "amountMinor": 4832000, "currency": "CZK" }""")

        assertEquals(Money(amountMinor = 4_832_000L, currency = "CZK"), money)
    }
}
