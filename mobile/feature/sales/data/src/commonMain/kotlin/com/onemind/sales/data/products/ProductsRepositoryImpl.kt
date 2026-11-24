package com.onemind.sales.data.products

import com.onemind.core.data.network.ApiJson
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.data.http.EtagGet
import com.onemind.sales.data.http.NoVenueError
import com.onemind.sales.data.http.venueSet
import com.onemind.sales.domain.products.ProductsRepository
import com.onemind.sales.domain.products.ProductsSummary
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import com.onemind.core.api.model.ProductsSummary as ProductsSummaryDto

/** `GET /dashboard/products` with ETag revalidation (ADR 0005). No read cache yet (ADR 0006 covers Today first). */
class ProductsRepositoryImpl(
    client: HttpClient,
) : ProductsRepository {
    private val get = EtagGet(client, "dashboard/products", ::parse)

    override suspend fun getProducts(venueIds: List<String>): Result<ProductsSummary, DataError> {
        if (venueIds.isEmpty()) return Result.Failure(NoVenueError)
        return get.get(listOf("venueIds" to venueSet(venueIds)))
    }

    /** The body as a summary: unreadable JSON is `SERIALIZATION`; the mapper decides the rest. */
    private fun parse(body: String): Result<ProductsSummary, DataError> =
        try {
            ApiJson.decodeFromString(ProductsSummaryDto.serializer(), body).toDomainResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(DataError.Network.SERIALIZATION)
        }
}
