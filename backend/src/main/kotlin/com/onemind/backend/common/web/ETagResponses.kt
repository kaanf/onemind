package com.onemind.backend.common.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64

/**
 * Polling for live reads (ADR 0005): the answer carries a weak `ETag` over its JSON, and a request whose
 * `If-None-Match` names it gets `304 Not Modified` without a body.
 */
@Component
class ETagResponses(
    private val json: JsonMapper,
) {
    fun <T : Any> ok(
        body: T,
        ifNoneMatch: String?,
    ): ResponseEntity<T> {
        val tag = tagOf(body)
        val matches = ifNoneMatch?.split(',')?.map { it.trim() }?.any { it == tag || it == "*" } == true
        val response = if (matches) ResponseEntity.status(HttpStatus.NOT_MODIFIED) else ResponseEntity.ok()
        return response.eTag(tag).body(body.takeUnless { matches })
    }

    private fun tagOf(body: Any): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(body))
        return "W/\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(TAG_BYTES)) + "\""
    }

    private companion object {
        const val TAG_BYTES = 16
    }
}
