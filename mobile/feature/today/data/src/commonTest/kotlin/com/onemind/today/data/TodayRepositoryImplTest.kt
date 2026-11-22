package com.onemind.today.data

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.data.network.HttpClientFactory
import com.onemind.core.data.session.SecureSessionStore
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.today.domain.DataStatus
import com.onemind.today.domain.HourKind
import com.onemind.today.domain.LossKind
import com.onemind.today.domain.TodayErrorCodes
import com.onemind.today.domain.TodaySummary
import com.onemind.today.domain.UnavailableFigure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class TodayRepositoryImplTest {
    private class NoStore : SecureSessionStore {
        override suspend fun read(): String? = null

        override suspend fun write(value: String) = Unit

        override suspend fun clear() = Unit
    }

    private class Fixture(
        scope: CoroutineScope,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        private val sessions = SessionManager(NoStore(), scope)
        val engine = MockEngine { handler(it) }
        val repository =
            TodayRepositoryImpl(
                HttpClientFactory(
                    ApiConfig(AppEnvironment.DEV, "http://api.test", "1.0.0"),
                    sessions,
                    SessionRefresher(sessions, scope),
                    InMemoryAppUpdateRepository(),
                ).create(engine),
            )
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        etag: String? = null,
    ) = respond(
        body,
        status,
        if (etag == null) {
            headersOf(HttpHeaders.ContentType, "application/json")
        } else {
            headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag))
        },
    )

    private val czk = Currency.CZK

    @Test
    fun mapsEveryFieldOfAReadyAnswer() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            val summary = assertIs<Result.Success<TodaySummary>>(fixture.repository.getToday(listOf(VENUE_A))).data

            assertEquals(DataStatus.READY, summary.status)
            assertEquals(czk, summary.currency)
            val venue = summary.venues.single()
            assertEquals(VENUE_A, venue.venueId)
            assertEquals(LocalDate(2026, 11, 6), venue.businessDay)
            assertEquals(TimeZone.of("Europe/Prague"), venue.timeZone)
            assertEquals(Instant.parse("2026-11-06T20:38:00Z"), venue.dataAsOf)
            assertEquals(Money(4_832_000, czk), summary.paidSoFar)
            val comparison = summary.comparison!!
            assertEquals(LocalDate(2026, 10, 30), comparison.previousBusinessDay)
            assertEquals(DecimalValue.parse("12.4"), comparison.deltaPercent)
            assertEquals(Money(4_299_000, czk), comparison.previous)
            assertEquals(Money(394_000, czk), summary.tips)
            assertEquals(286, summary.guests)
            assertEquals(Money(16_900, czk), summary.perGuest)
            val loss = summary.possibleLoss!!
            assertEquals(Money(187_000, czk), loss.total)
            assertEquals(listOf(LossKind.DISCOUNTS, LossKind.REFUNDS), loss.components.map { it.kind })
            val hourly = summary.hourly!!
            assertEquals(21, hourly.currentHour)
            assertEquals(listOf(HourKind.PAST, HourKind.CURRENT, HourKind.FORECAST), hourly.hours.map { it.kind })
            assertNull(hourly.hours.last().amount)
            assertEquals(setOf(UnavailableFigure.OPEN_TABS, UnavailableFigure.OCCUPANCY_NOW), summary.unavailable)
            assertEquals(2, summary.dataGaps.quarantinedBillCount)
            assertEquals(false, summary.dataGaps.isEmpty)
        }

    @Test
    fun noDataYetHasNullFiguresNotZeros() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val summary = assertIs<Result.Success<TodaySummary>>(fixture.repository.getToday(listOf(VENUE_A))).data

            assertEquals(DataStatus.NO_DATA_YET, summary.status)
            assertEquals(listOf(VENUE_A), summary.pendingVenueIds)
            assertNull(summary.paidSoFar)
            assertNull(summary.guests)
            assertNull(summary.hourly)
            assertNull(summary.venues.single().dataAsOf)
            assertEquals(true, summary.dataGaps.isEmpty)
        }

    @Test
    fun sendsTheVenueIdsSortedAsOneCommaSeparatedParameter() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            fixture.repository.getToday(listOf(VENUE_B, VENUE_A))

            val request = fixture.engine.requestHistory.single()
            assertEquals("/api/v1/dashboard/today", request.url.encodedPath)
            assertEquals("$VENUE_A,$VENUE_B", request.url.parameters["venueIds"])
            assertNull(request.headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun pollingSendsTheEtagAndA304ReturnsThePreviousAnswer() =
        runTest {
            var calls = 0
            val fixture =
                Fixture(backgroundScope) {
                    if (calls++ == 0) json(READY_JSON, etag = "\"v1\"") else respond("", HttpStatusCode.NotModified)
                }

            val first = assertIs<Result.Success<TodaySummary>>(fixture.repository.getToday(listOf(VENUE_A))).data
            val second = assertIs<Result.Success<TodaySummary>>(fixture.repository.getToday(listOf(VENUE_A))).data

            assertSame(first, second)
            assertEquals("\"v1\"", fixture.engine.requestHistory[1].headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun anotherVenueSetDoesNotReuseTheEtag() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            fixture.repository.getToday(listOf(VENUE_A))
            fixture.repository.getToday(listOf(VENUE_A, VENUE_B))
            // The same set in another order is the same answer.
            fixture.repository.getToday(listOf(VENUE_B, VENUE_A))

            assertNull(fixture.engine.requestHistory[1].headers[HttpHeaders.IfNoneMatch])
            assertEquals("\"v1\"", fixture.engine.requestHistory[2].headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun noVenueIsAValidationErrorWithoutARequest() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val error = assertIs<Result.Failure<DataError>>(fixture.repository.getToday(emptyList())).error

            assertEquals("VALIDATION_FAILED", assertIs<DataError.Api>(error).code)
            assertTrue(fixture.engine.requestHistory.isEmpty())
        }

    @Test
    fun problemsKeepTheirCode() =
        runTest {
            val fixture =
                Fixture(backgroundScope) {
                    json(
                        """{"type":"about:blank","title":"Unprocessable","status":422,"code":"CURRENCIES_DIFFER"}""",
                        HttpStatusCode.UnprocessableEntity,
                    )
                }

            val error = assertIs<Result.Failure<DataError>>(fixture.repository.getToday(listOf(VENUE_A, VENUE_B))).error

            assertEquals(TodayErrorCodes.CURRENCIES_DIFFER, assertIs<DataError.Api>(error).code)
        }

    @Test
    fun anAnswerThatBreaksTheContractIsUnreadableNotGuessed() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON.replace("Europe/Prague", "Mars/Olympus")) }

            val error = assertIs<Result.Failure<DataError>>(fixture.repository.getToday(listOf(VENUE_A))).error

            assertEquals(DataError.Network.SERIALIZATION, error)
        }

    private companion object {
        const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
        const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"

        val READY_JSON =
            """
            {
              "status": "READY",
              "currency": "CZK",
              "venues": [{"venueId": "$VENUE_A", "businessDay": "2026-11-06", "timeZone": "Europe/Prague",
                          "status": "READY", "dataAsOf": "2026-11-06T20:38:00Z"}],
              "pendingVenueIds": [],
              "paidSoFar": {"amountMinor": 4832000, "currency": "CZK"},
              "comparison": {"previousBusinessDay": "2026-10-30", "venueIds": ["$VENUE_A"],
                             "current": {"amountMinor": 4832000, "currency": "CZK"},
                             "previous": {"amountMinor": 4299000, "currency": "CZK"}, "deltaPercent": "12.4"},
              "tips": {"amountMinor": 394000, "currency": "CZK"},
              "guests": 286,
              "perGuest": {"amountMinor": 16900, "currency": "CZK"},
              "possibleLoss": {"total": {"amountMinor": 187000, "currency": "CZK"}, "components": [
                {"kind": "DISCOUNTS", "amount": {"amountMinor": 150000, "currency": "CZK"}},
                {"kind": "REFUNDS", "amount": {"amountMinor": 37000, "currency": "CZK"}}]},
              "hourly": {"currentHour": 21, "hours": [
                {"hour": 20, "kind": "PAST", "amount": {"amountMinor": 910000, "currency": "CZK"}},
                {"hour": 21, "kind": "CURRENT", "amount": {"amountMinor": 420000, "currency": "CZK"}},
                {"hour": 22, "kind": "FORECAST"}]},
              "unavailable": ["OPEN_TABS", "OCCUPANCY_NOW"],
              "dataGaps": {"quarantinedBillCount": 2, "linelessBillCount": 0, "outdatedBillCount": 0,
                           "paymentMismatchBillCount": 0}
            }
            """.trimIndent()

        val NO_DATA_JSON =
            """
            {
              "status": "NO_DATA_YET",
              "currency": "CZK",
              "venues": [{"venueId": "$VENUE_A", "businessDay": "2026-11-06", "timeZone": "Europe/Prague",
                          "status": "NO_DATA_YET", "dataAsOf": null}],
              "pendingVenueIds": ["$VENUE_A"],
              "unavailable": ["OPEN_TABS", "OCCUPANCY_NOW", "STAFF_ON_SHIFT", "TIPS_CARD_SHARE"],
              "dataGaps": {"quarantinedBillCount": 0, "linelessBillCount": 0, "outdatedBillCount": 0,
                           "paymentMismatchBillCount": 0}
            }
            """.trimIndent()
    }
}
