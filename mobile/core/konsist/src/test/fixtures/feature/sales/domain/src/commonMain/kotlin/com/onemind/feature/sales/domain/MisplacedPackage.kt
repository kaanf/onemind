// Fixture (not compiled): a file in feature/sales/domain declaring a wrong package. Layers come from
// the path, so the domain and money guards still see it, and the package guard flags the drift.
package com.onemind.feature.sales.domain

import io.ktor.client.HttpClient

data class ProbeMoney(
    val amount: Double,
    val client: HttpClient,
)
