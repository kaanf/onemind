package com.onemind.sales.data

import com.onemind.core.data.cache.CachedScreen
import com.onemind.core.data.cache.ScreenCache
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
import com.onemind.sales.domain.SalesDataStatus
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesSummary
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class SalesRepositoryImplTest {
    private class NoStore : SecureSessionStore {
        override suspend fun read(): String? = null

        override suspend fun write(value: String) = Unit

        override suspend fun clear() = Unit
    }

    private class MemoryCache : ScreenCache {
        val entries = mutableMapOf<String, CachedScreen>()
        override var generation = 0L

        override suspend fun read(key: String) = entries[key]

        override suspend fun write(
            key: String,
            body: String,
            etag: String?,
            generation: Long,
        ) {
            if (generation == this.generation) entries[key] = CachedScreen(body, etag, SAVED_AT)
        }

        override suspend fun clear() {
            generation++
            entries.clear()
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        val cache: MemoryCache = MemoryCache(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        private val sessions = SessionManager(NoStore(), scope)
        val requests = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine {
                requests += it
                handler(it)
            }
        val repository =
            SalesRepositoryImpl(
                HttpClientFactory(
                    ApiConfig(AppEnvironment.DEV, "http://api.test", "1.0.0"),
                    sessions,
                    SessionRefresher(sessions, scope),
                    InMemoryAppUpdateRepository(),
                ).create(engine),
                cache,
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
    fun mapsEveryFieldOfAReadyAnswerAndSendsThePeriod() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            val summary =
                assertIs<Result.Success<SalesSummary>>(fixture.repository.getSales(listOf(VENUE_B, VENUE_A), SalesPeriod.WEEK)).data

            val url = fixture.requests.single().url
            assertEquals("/api/v1/dashboard/sales", url.encodedPath)
            assertEquals("$VENUE_A,$VENUE_B", url.parameters["venueIds"])
            assertEquals("WEEK", url.parameters["period"])
            assertEquals(SalesDataStatus.READY, summary.status)
            assertEquals(SalesPeriod.WEEK, summary.range.period)
            assertEquals(LocalDate(2026, 11, 2), summary.range.from)
            assertEquals(LocalDate(2026, 11, 8), summary.range.to)
            assertEquals(Money(31_248_000, czk), summary.revenue)
            val comparison = assertNotNull(summary.comparison)
            assertEquals(DecimalValue.parse("8.1"), comparison.deltaPercent)
            assertEquals(LocalDate(2026, 10, 26), comparison.previousFrom)
            assertEquals(1846, summary.guests)
            assertEquals(Money(41_200, czk), summary.averageBill)
            assertEquals(Money(16_900, czk), summary.perGuest)
            assertEquals(Money(1_520_000, czk), summary.tips)
            val split = assertNotNull(summary.byPayment)
            assertEquals(68, split.cardPercent)
            assertEquals(Money(21_249_000, czk), split.card)
            assertEquals(Money(2_241_000, czk), summary.possibleLoss)
            assertEquals(Instant.parse("2026-11-06T20:38:00Z"), summary.venues.single().dataAsOf)
            assertEquals(3, summary.dataGaps.outdatedBillCount)
        }

    @Test
    fun noDataYetHasNullFigures() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val summary = assertIs<Result.Success<SalesSummary>>(fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.DAY)).data

            assertEquals(SalesDataStatus.NO_DATA_YET, summary.status)
            assertNull(summary.revenue)
            assertNull(summary.guests)
            assertNull(summary.byPayment)
        }

    @Test
    fun notModifiedReturnsTheLastAnswerOfTheSamePeriod() =
        runTest {
            var calls = 0
            val fixture =
                Fixture(backgroundScope) { request ->
                    calls++
                    if (request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"") {
                        respond("", HttpStatusCode.NotModified)
                    } else {
                        json(READY_JSON, etag = "\"v1\"")
                    }
                }

            val first = assertIs<Result.Success<SalesSummary>>(fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.WEEK)).data
            val second = assertIs<Result.Success<SalesSummary>>(fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.WEEK)).data

            assertEquals(first, second)
            assertEquals("\"v1\"", fixture.requests[1].headers[HttpHeaders.IfNoneMatch])
            assertEquals(2, calls)
        }

    @Test
    fun anotherPeriodIsAnotherRequestAndCacheEntry() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.WEEK)
            fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.MONTH)

            assertNull(fixture.requests[1].headers[HttpHeaders.IfNoneMatch])
            assertEquals(2, fixture.cache.entries.size)
            assertNotNull(fixture.repository.cachedSales(listOf(VENUE_A), SalesPeriod.WEEK))
            assertNull(fixture.repository.cachedSales(listOf(VENUE_A), SalesPeriod.HOUR))
        }

    @Test
    fun unknownDataStatusAsksForAnAppUpdate() =
        runTest {
            val fixture =
                Fixture(
                    backgroundScope,
                ) { json(READY_JSON.replace("\"status\": \"READY\",\n  \"currency\"", "\"status\": \"LATER\",\n  \"currency\"")) }

            val result = fixture.repository.getSales(listOf(VENUE_A), SalesPeriod.WEEK)

            assertEquals(DataError.Network.NEEDS_APP_UPDATE, assertIs<Result.Failure<DataError>>(result).error)
        }

    private companion object {
        const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
        const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"
        val SAVED_AT = Instant.parse("2026-11-06T20:40:00Z")

        val READY_JSON =
            """
            {
              "status": "READY",
              "currency": "CZK",
              "range": {"period": "WEEK", "from": "2026-11-02", "to": "2026-11-08", "finished": false},
              "venues": [{"venueId": "$VENUE_A", "businessDay": "2026-11-06", "timeZone": "Europe/Prague",
                          "status": "READY", "dataAsOf": "2026-11-06T20:38:00Z"}],
              "pendingVenueIds": [],
              "revenue": {"amountMinor": 31248000, "currency": "CZK"},
              "comparison": {"previous": {"from": "2026-10-26", "to": "2026-11-01"},
                             "compared": {"from": "2026-10-26", "to": "2026-10-30"}, "upToSameTime": true,
                             "venueIds": ["$VENUE_A"],
                             "current": {"amountMinor": 31248000, "currency": "CZK"},
                             "previousRevenue": {"amountMinor": 28906000, "currency": "CZK"}, "deltaPercent": "8.1"},
              "guests": 1846,
              "averageBill": {"amountMinor": 41200, "currency": "CZK"},
              "perGuest": {"amountMinor": 16900, "currency": "CZK"},
              "tips": {"amountMinor": 1520000, "currency": "CZK"},
              "byPayment": {"card": {"amountMinor": 21249000, "currency": "CZK"},
                            "cash": {"amountMinor": 9999000, "currency": "CZK"},
                            "other": {"amountMinor": 0, "currency": "CZK"}, "cardPercent": 68},
              "possibleLoss": {"total": {"amountMinor": 2241000, "currency": "CZK"}, "components": []},
              "unavailable": ["TIPS_CARD_SHARE"],
              "dataGaps": {"quarantinedBillCount": 0, "linelessBillCount": 0, "outdatedBillCount": 3,
                           "paymentMismatchBillCount": 0}
            }
            """.trimIndent()

        val NO_DATA_JSON =
            """
            {
              "status": "NO_DATA_YET",
              "currency": "CZK",
              "range": {"period": "DAY", "from": "2026-11-06", "to": "2026-11-06", "finished": false},
              "venues": [{"venueId": "$VENUE_A", "businessDay": "2026-11-06", "timeZone": "Europe/Prague",
                          "status": "NO_DATA_YET"}],
              "pendingVenueIds": ["$VENUE_A"],
              "unavailable": [],
              "dataGaps": {"quarantinedBillCount": 0, "linelessBillCount": 0, "outdatedBillCount": 0,
                           "paymentMismatchBillCount": 0}
            }
            """.trimIndent()
    }
}
