package com.onemind.archfixture.pos.infra.client

import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import java.net.HttpURLConnection
import java.net.http.HttpRequest

// Rule 1 violations: each can send a mutating request from the pos module.
class RestClientPosting(
    private val restClient: RestClient,
) {
    fun send(): String? =
        restClient
            .post()
            .uri("/bills")
            .retrieve()
            .body(String::class.java)
}

class RestTemplateDeleting(
    private val restTemplate: RestTemplate,
) {
    fun remove() = restTemplate.delete("/bills/1")
}

class RestTemplateExchanging(
    private val restTemplate: RestTemplate,
) {
    fun read() = restTemplate.exchange("/bills", HttpMethod.GET, null, String::class.java)
}

class HttpMethodUsing {
    fun method(): HttpMethod = HttpMethod.PATCH
}

class RequestEntityPosting {
    fun request(): RequestEntity<Void> = RequestEntity.post("/bills").build()
}

class JdkHttpPutting {
    fun request(): HttpRequest.Builder = HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.noBody())
}

interface PostingExchange {
    @PostExchange("/bills")
    fun send(): String
}

interface DeletingExchange {
    @HttpExchange(url = "/bills/1", method = "DELETE")
    fun remove()
}

@HttpExchange(url = "/bills", method = "POST")
interface TypeLevelPostingExchange {
    @HttpExchange
    fun create(): String
}

class HttpMethodFromString {
    fun method(): HttpMethod = HttpMethod.valueOf("POST")
}

class CustomRestTemplate : RestTemplate()

class RestTemplateSubclassPosting(
    private val restTemplate: CustomRestTemplate,
) {
    fun send(): String? = restTemplate.postForObject("/bills", null, String::class.java)
}

class UrlConnectionPosting(
    private val connection: HttpURLConnection,
) {
    fun send() {
        connection.requestMethod = "POST"
    }
}

// Allowed: GET only.
@HttpExchange("/api")
interface ReadingExchange {
    @GetExchange("/bills")
    fun bills(): String

    @HttpExchange(url = "/places", method = "GET")
    fun places(): String
}

class GetOnlyClient(
    private val restClient: RestClient,
) {
    fun bills(): String? =
        restClient
            .get()
            .uri("/bills")
            .retrieve()
            .body(String::class.java)

    fun method(): HttpMethod = HttpMethod.GET

    fun request(): HttpRequest.Builder = HttpRequest.newBuilder().GET()
}
