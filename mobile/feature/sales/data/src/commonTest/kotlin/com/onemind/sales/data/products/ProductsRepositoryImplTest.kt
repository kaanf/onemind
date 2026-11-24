package com.onemind.sales.data.products

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
import com.onemind.sales.domain.products.HourQuantity
import com.onemind.sales.domain.products.ProductCategory
import com.onemind.sales.domain.products.ProductsSummary
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProductsRepositoryImplTest {
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
            ProductsRepositoryImpl(
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

            val summary = assertIs<Result.Success<ProductsSummary>>(fixture.repository.getProducts(listOf(VENUE_A))).data

            assertTrue(summary.hasData)
            assertEquals(LocalDate(2026, 11, 5), summary.businessDay)
            assertEquals(listOf(21, 22, 23, 0), summary.hours)
            assertEquals(listOf(ProductCategory("c1", "Beer", null), ProductCategory("c2", "Lager", "c1")), summary.categories)
            val product = summary.products.single()
            assertEquals("p1", product.productId)
            assertEquals("c2", product.categoryId)
            assertEquals(DecimalValue.parse("12.5"), product.sold)
            assertEquals(czk(87_500), product.gross)
            assertEquals(listOf(HourQuantity(22, DecimalValue.parse("10")), HourQuantity(0, DecimalValue.parse("2.5"))), product.hourly)
            assertEquals(22, product.peakHour)
            assertEquals(1, summary.dataGaps.linelessBillCount)
        }

    @Test
    fun noDataYetHasEmptyLists() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(NO_DATA_JSON) }

            val summary = assertIs<Result.Success<ProductsSummary>>(fixture.repository.getProducts(listOf(VENUE_A))).data

            assertEquals(false, summary.hasData)
            assertTrue(summary.products.isEmpty() && summary.hours.isEmpty() && summary.categories.isEmpty())
        }

    @Test
    fun anUnchangedAnswerReturnsThePreviousSummary() =
        runTest {
            var calls = 0
            val fixture =
                Fixture(backgroundScope) {
                    if (calls++ == 0) json(READY_JSON, etag = "\"v1\"") else respond("", HttpStatusCode.NotModified)
                }

            val first = assertIs<Result.Success<ProductsSummary>>(fixture.repository.getProducts(listOf(VENUE_A))).data
            val second = assertIs<Result.Success<ProductsSummary>>(fixture.repository.getProducts(listOf(VENUE_A))).data

            assertSame(first, second)
            val request = fixture.engine.requestHistory[1]
            assertEquals("/api/v1/dashboard/products", request.url.encodedPath)
            assertEquals("\"v1\"", request.headers[HttpHeaders.IfNoneMatch])
        }

    @Test
    fun aBadQuantityIsUnreadable() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(READY_JSON.replace("\"12.5\"", "\"12,5\"")) }

            assertEquals(Result.Failure(DataError.Network.SERIALIZATION), fixture.repository.getProducts(listOf(VENUE_A)))
        }

    private companion object {
        const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"

        const val VENUE_JSON =
            """{"venueId":"$VENUE_A","businessDay":"2026-11-05","timeZone":"Europe/Prague","status":"READY","dataAsOf":"2026-11-05T20:38:00Z"}"""

        val READY_JSON =
            """
            {
              "status": "READY",
              "currency": "CZK",
              "businessDay": "2026-11-05",
              "venues": [$VENUE_JSON],
              "pendingVenueIds": [],
              "hours": [21, 22, 23, 0],
              "categories": [{"id": "c1", "name": "Beer", "parentId": null}, {"id": "c2", "name": "Lager", "parentId": "c1"}],
              "products": [
                {
                  "venueId": "$VENUE_A", "productId": "p1", "name": "Pilsner", "categoryId": "c2", "sold": "12.5",
                  "gross": {"amountMinor": 87500, "currency": "CZK"},
                  "hourly": [{"hour": 22, "quantity": "10"}, {"hour": 0, "quantity": "2.5"}],
                  "peakHour": 22
                }
              ],
              "dataGaps": {"quarantinedBillCount": 0, "linelessBillCount": 1, "outdatedBillCount": 0, "paymentMismatchBillCount": 0}
            }
            """.trimIndent()

        val NO_DATA_JSON =
            """
            {
              "status": "NO_DATA_YET", "currency": "CZK", "businessDay": "2026-11-05", "venues": [],
              "pendingVenueIds": ["$VENUE_A"], "hours": null, "categories": null, "products": null,
              "dataGaps": {"quarantinedBillCount": 0, "linelessBillCount": 0, "outdatedBillCount": 0, "paymentMismatchBillCount": 0}
            }
            """.trimIndent()
    }
}
