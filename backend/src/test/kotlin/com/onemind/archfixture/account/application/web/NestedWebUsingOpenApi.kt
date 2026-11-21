package com.onemind.archfixture.account.application.web

import com.onemind.api.v1.model.AuthTokens

// R5 is anchored to the module's web package: a deeper "web" segment doesn't count.
class NestedWebUsingOpenApi(
    val dto: AuthTokens,
)
