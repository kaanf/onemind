package com.onemind.sales.data.losses

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
import com.onemind.sales.domain.losses.DiscountTypeLoss
import com.onemind.sales.domain.losses.LossRecords
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesSummary
import com.onemind.sales.domain.losses.UnavailableLossComponent
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LossesRepositoryImplTest {
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
            LossesRepositoryImpl(
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

    private fun czk(minor: Long) = Money(minor, Currency.CZK)

    @Test
    fun mapsEveryFieldOfAReadyAnswer() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            val summary = assertIs<Result.Success<LossesSummary>>(fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)).data

            assertTrue(summary.hasData)
            assertEquals(Currency.CZK, summary.currency)
            assertEquals(LossesPeriod.WEEK, summary.period)
            assertEquals(LocalDate(2026, 11, 2), summary.from)
            assertEquals(LocalDate(2026, 11, 8), summary.to)
            assertEquals(czk(2_241_000), summary.possibleLoss)
            assertEquals(czk(1_864_000), summary.discounts)
            assertEquals(DecimalValue.parse("6.7"), summary.sharePercent)
            assertEquals(240, summary.records)
            assertEquals(DecimalValue.parse("0.8"), summary.comparison?.changePoints)
            assertEquals(LocalDate(2026, 10, 26), summary.comparison?.previousFrom)
            assertEquals(
                listOf(
                    DiscountTypeLoss("HAPPY_HOUR", 120, czk(987_000)),
                    DiscountTypeLoss(DiscountTypeLoss.UNTYPED, null, czk(877_000)),
                ),
                summary.discountsByType,
            )
            assertEquals(LossRecords(4, czk(89_000)), summary.refunds)
            assertEquals(LossRecords(2, czk(288_000)), summary.deletedBills)
            // The component this version doesn't know is left out.
            assertEquals(setOf(UnavailableLossComponent.ITEM_VOIDS), summary.unavailable)
            assertEquals(1, summary.dataGaps.discountMismatchDayCount)
            assertEquals(1, summary.pendingVenueCount)
        }

    @Test
    fun sendsSortedVenuesAndThePeriod() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON) }

            fixture.repository.getLosses(listOf(VENUE_B, VENUE_A, VENUE_B), LossesPeriod.MONTH)

            val url =
                fixture.engine.requestHistory
                    .single()
                    .url
            assertEquals("/api/v1/dashboard/losses", url.encodedPath)
            assertEquals("$VENUE_A,$VENUE_B", url.parameters["venueIds"])
            assertEquals("MONTH", url.parameters["period"])
        }

    @Test
    fun anUnchangedAnswerReturnsThePreviousSummary() =
        runTest {
            var calls = 0
            val fixture =
                Fixture(backgroundScope) {
                    if (calls++ == 0) json(READY_JSON, etag = "\"v1\"") else respond("", HttpStatusCode.NotModified)
                }

            val first = assertIs<Result.Success<LossesSummary>>(fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)).data
            val second = assertIs<Result.Success<LossesSummary>>(fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)).data

            assertSame(first, second)
            assertEquals("\"v1\"", fixture.engine.requestHistory[1].headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun anotherPeriodDoesNotSendTheEtag() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON, etag = "\"v1\"") }

            fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)
            fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.DAY)

            assertNull(fixture.engine.requestHistory[1].headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun noDataYetHasNoFigures() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val summary = assertIs<Result.Success<LossesSummary>>(fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)).data

            assertEquals(false, summary.hasData)
            assertNull(summary.possibleLoss)
            assertNull(summary.records)
            assertTrue(summary.discountsByType.isEmpty())
        }

    @Test
    fun anUnknownStatusAsksForAnUpdate() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON.replace("NO_DATA_YET", "PAUSED")) }

            val result = fixture.repository.getLosses(listOf(VENUE_A), LossesPeriod.WEEK)

            assertEquals(Result.Failure(DataError.Network.NEEDS_APP_UPDATE), result)
        }

    @Test
    fun noVenueFailsWithoutARequest() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON) }

            val result = assertIs<Result.Failure<DataError>>(fixture.repository.getLosses(emptyList(), LossesPeriod.WEEK))

            assertEquals("VALIDATION_FAILED", (result.error as DataError.Api).code)
            assertTrue(fixture.engine.requestHistory.isEmpty())
        }

    private companion object {
        const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
        const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"

        const val VENUE_JSON =
            """{"venueId":"$VENUE_A","businessDay":"2026-11-06","timeZone":"Europe/Prague","status":"READY","dataAsOf":"2026-11-06T20:38:00Z"}"""

        const val GAPS_JSON =
            """{"quarantinedBillCount":0,"linelessBillCount":0,"outdatedBillCount":0,"paymentMismatchBillCount":0,"discountMismatchDayCount":1}"""

        val READY_JSON =
            """
            {
              "status": "READY",
              "currency": "CZK",
              "range": {"period": "WEEK", "from": "2026-11-02", "to": "2026-11-08", "finished": false},
              "venues": [$VENUE_JSON],
              "pendingVenueIds": ["$VENUE_B"],
              "possibleLoss": {
                "total": {"amountMinor": 2241000, "currency": "CZK"},
                "components": [
                  {"kind": "DISCOUNTS", "amount": {"amountMinor": 1864000, "currency": "CZK"}},
                  {"kind": "REFUNDS", "amount": {"amountMinor": 89000, "currency": "CZK"}},
                  {"kind": "DELETED_BILLS", "amount": {"amountMinor": 288000, "currency": "CZK"}}
                ]
              },
              "sharePercent": "6.7",
              "records": 240,
              "comparison": {
                "previous": {"from": "2026-10-26", "to": "2026-11-01"},
                "compared": {"from": "2026-10-26", "to": "2026-10-30"},
                "upToSameTime": true,
                "venueIds": ["$VENUE_A"],
                "currentSharePercent": "6.7",
                "previousSharePercent": "5.9",
                "changePoints": "0.8"
              },
              "discountsByType": [
                {"type": "HAPPY_HOUR", "records": 120, "amount": {"amountMinor": 987000, "currency": "CZK"}},
                {"type": "UNTYPED", "amount": {"amountMinor": 877000, "currency": "CZK"}}
              ],
              "refunds": {"records": 4, "amount": {"amountMinor": 89000, "currency": "CZK"}},
              "deletedBills": {"records": 2, "amount": {"amountMinor": 288000, "currency": "CZK"}},
              "unavailableComponents": ["ITEM_VOIDS", "SOMETHING_NEW"],
              "dataGaps": $GAPS_JSON
            }
            """.trimIndent()

        val NO_DATA_JSON =
            """
            {
              "status": "NO_DATA_YET",
              "currency": "CZK",
              "range": {"period": "WEEK", "from": "2026-11-02", "to": "2026-11-08", "finished": false},
              "venues": [],
              "pendingVenueIds": ["$VENUE_A"],
              "discountsByType": [],
              "unavailableComponents": [],
              "dataGaps": $GAPS_JSON
            }
            """.trimIndent()
    }
}
