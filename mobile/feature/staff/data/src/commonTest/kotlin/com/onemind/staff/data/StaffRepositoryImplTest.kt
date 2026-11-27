package com.onemind.staff.data

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.data.network.HttpClientFactory
import com.onemind.core.data.session.SecureSessionStore
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.staff.domain.StaffMember
import com.onemind.staff.domain.StaffSummary
import com.onemind.staff.domain.StaffTotals
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

class StaffRepositoryImplTest {
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
            StaffRepositoryImpl(
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
        etag: String? = null,
    ) = respond(
        body,
        HttpStatusCode.OK,
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
            val fixture = Fixture(backgroundScope) { json(READY_JSON) }

            val summary = assertIs<Result.Success<StaffSummary>>(fixture.repository.getStaff(listOf(VENUE_A))).data

            assertTrue(summary.hasData)
            assertEquals(LocalDate(2026, 11, 20), summary.businessDay)
            assertEquals(StaffTotals(czk(1_482_000), czk(121_000), 82, 31), summary.totals)
            assertEquals(StaffMember("s1", "Tomáš Novák", czk(1_482_000), czk(121_000), 82, 31, 2), summary.staff.single())
            assertEquals(1, summary.dataGaps.paymentMismatchBillCount)
            assertEquals(
                "/api/v1/dashboard/staff",
                fixture.engine.requestHistory
                    .single()
                    .url.encodedPath,
            )
        }

    @Test
    fun noDataYetHasNoTotalsAndNoStaff() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val summary = assertIs<Result.Success<StaffSummary>>(fixture.repository.getStaff(listOf(VENUE_A))).data

            assertEquals(false, summary.hasData)
            assertNull(summary.totals)
            assertTrue(summary.staff.isEmpty())
        }

    @Test
    fun anUnchangedAnswerReturnsThePreviousSummary() =
        runTest {
            var calls = 0
            val fixture =
                Fixture(backgroundScope) {
                    if (calls++ == 0) json(READY_JSON, etag = "\"v1\"") else respond("", HttpStatusCode.NotModified)
                }

            val first = assertIs<Result.Success<StaffSummary>>(fixture.repository.getStaff(listOf(VENUE_A))).data
            val second = assertIs<Result.Success<StaffSummary>>(fixture.repository.getStaff(listOf(VENUE_A))).data

            assertSame(first, second)
            assertEquals("\"v1\"", fixture.engine.requestHistory[1].headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun anUnknownStatusAsksForAnAppUpdate() =
        runTest {
            val fixture =
                Fixture(
                    backgroundScope,
                ) { json(READY_JSON.replace("\"status\":\"READY\",\"currency\"", "\"status\":\"LATER\",\"currency\"")) }

            assertEquals(Result.Failure(DataError.Network.NEEDS_APP_UPDATE), fixture.repository.getStaff(listOf(VENUE_A)))
        }

    private companion object {
        const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"

        const val VENUE_JSON =
            """{"venueId":"$VENUE_A","businessDay":"2026-11-20","timeZone":"Europe/Prague","status":"READY","dataAsOf":"2026-11-20T20:38:00Z"}"""

        const val GAPS_JSON =
            """{"quarantinedBillCount":0,"linelessBillCount":0,"outdatedBillCount":0,"paymentMismatchBillCount":1}"""

        const val READY_JSON =
            """{"status":"READY","currency":"CZK","businessDay":"2026-11-20","venues":[$VENUE_JSON],"pendingVenueIds":[],""" +
                """"totals":{"sales":{"amountMinor":1482000,"currency":"CZK"},""" +
                """"tips":{"amountMinor":121000,"currency":"CZK"},"guests":82,"billCount":31},""" +
                """"staff":[{"staffId":"s1","name":"Tomáš Novák","sales":{"amountMinor":1482000,"currency":"CZK"},""" +
                """"tips":{"amountMinor":121000,"currency":"CZK"},"guests":82,"billCount":31,"deletedBillCount":2}],""" +
                """"dataGaps":$GAPS_JSON}"""

        const val NO_DATA_JSON =
            """{"status":"NO_DATA_YET","currency":"CZK","businessDay":"2026-11-20","venues":[$VENUE_JSON],""" +
                """"pendingVenueIds":["$VENUE_A"],"totals":null,"staff":null,"dataGaps":$GAPS_JSON}"""
    }
}
