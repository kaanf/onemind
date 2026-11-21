package com.onemind.archfixture.account.infra.client

import org.springframework.web.client.RestClient

// Rule 1 covers the pos module only: other modules may post (e.g. to an email or LLM provider).
class AccountPosting(
    private val restClient: RestClient,
) {
    fun send(): String? =
        restClient
            .post()
            .uri("/mail")
            .retrieve()
            .body(String::class.java)
}
