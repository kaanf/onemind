package com.onemind.backend.common.idempotency

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.idempotency.infra.IdempotencyClaim
import com.onemind.backend.common.idempotency.infra.IdempotencyRecordRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.mock.web.MockPart
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class, IdempotencyFilterTests.ProbeConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
class IdempotencyFilterTests {
    @RestController
    class ProbeController {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)

        @PostMapping("/test/idempotent")
        fun create(
            @RequestHeader("Idempotency-Key") key: UUID,
            @RequestBody body: Map<String, Any>,
        ): ResponseEntity<Map<String, Any>> {
            val call = calls.incrementAndGet()
            if (body["block"] == true) {
                entered.countDown()
                gate.await(10, TimeUnit.SECONDS)
            }
            check(body["fail"] != true) { "failing on purpose" }
            return ResponseEntity.created(URI.create("/test/idempotent/$call")).body(mapOf("call" to call))
        }

        @PostMapping("/test/idempotent/upload")
        fun upload(
            @RequestPart file: MultipartFile,
        ) = mapOf("call" to calls.incrementAndGet(), "size" to file.size)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ProbeConfig {
        @Bean
        fun probeController() = ProbeController()

        @Bean
        @Order(0)
        fun probeSecurity(http: HttpSecurity): SecurityFilterChain =
            http
                .securityMatcher("/test/**")
                .csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var controller: ProbeController

    @Autowired
    lateinit var records: IdempotencyRecordRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var userId: UUID

    @BeforeEach
    fun createUser() {
        userId = UUID.randomUUID()
        jdbc.update("insert into account.app_user (id, email) values (?, ?)", userId, "$userId@example.com")
    }

    @Test
    fun `a retry with the same key and body replays the first response`() {
        val key = UUID.randomUUID()
        val before = controller.calls.get()

        val first = send(key, """{"name": "Pilsner"}""").andReturn().response
        val retry = send(key, """{"name": "Pilsner"}""").andReturn().response

        assertEquals(before + 1, controller.calls.get())
        assertEquals(201, retry.status)
        assertEquals(first.getHeader("Location"), retry.getHeader("Location"))
        assertEquals(first.contentType, retry.contentType)
        assertEquals(first.contentAsString, retry.contentAsString)
    }

    @Test
    fun `the same key with a different body is rejected`() {
        val key = UUID.randomUUID()
        send(key, """{"name": "Pilsner"}""")

        send(key, """{"name": "Kozel"}""").andExpectProblem(422, "IDEMPOTENCY_KEY_REUSED")
    }

    @Test
    fun `keys are scoped per user`() {
        val key = UUID.randomUUID()
        val before = controller.calls.get()
        send(key, "{}")

        createUser()
        send(key, "{}").andExpect { status { isCreated() } }

        assertEquals(before + 2, controller.calls.get())
    }

    @Test
    fun `a retry while the first request runs is a conflict`() {
        val key = UUID.randomUUID()
        val first = CompletableFuture.supplyAsync { send(key, """{"block": true}""").andReturn().response.status }
        assertTrue(controller.entered.await(10, TimeUnit.SECONDS))

        send(key, """{"block": true}""").andExpectProblem(409, "IDEMPOTENT_REQUEST_IN_PROGRESS")

        controller.gate.countDown()
        assertEquals(201, first.get(10, TimeUnit.SECONDS))
    }

    @Test
    fun `a failed request is not stored, so it can be retried`() {
        val key = UUID.randomUUID()
        val before = controller.calls.get()

        send(key, """{"fail": true}""").andExpect { status { isInternalServerError() } }
        send(key, """{"fail": true}""").andExpect { status { isInternalServerError() } }

        assertEquals(before + 2, controller.calls.get())
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from platform.idempotency_record where key = ?", key))
    }

    @Test
    fun `multipart requests are compared by their parts`() {
        val key = UUID.randomUUID()
        val before = controller.calls.get()

        fun upload(content: String) =
            mockMvc.multipart("/test/idempotent/upload") {
                with(user(userId.toString()))
                header(IdempotencyFilter.HEADER, key.toString())
                part(MockPart("file", "receipt.jpg", content.toByteArray(), MediaType.IMAGE_JPEG))
            }

        val first = upload("image").andReturn().response.contentAsString
        assertEquals(first, upload("image").andReturn().response.contentAsString)
        upload("other image").andExpectProblem(422, "IDEMPOTENCY_KEY_REUSED")

        assertEquals(before + 1, controller.calls.get())
    }

    @Test
    fun `a key that is not a UUID is a bad request`() {
        send("not-a-uuid", "{}").andExpectProblem(400, "INVALID_IDEMPOTENCY_KEY")
        send("1-1-1-1-1", "{}").andExpectProblem(400, "INVALID_IDEMPOTENCY_KEY")
    }

    @Test
    fun `a missing key where the operation requires one is a bad request`() {
        mockMvc
            .post("/test/idempotent") {
                with(user(userId.toString()))
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpectProblem(400, "BAD_REQUEST")
    }

    @Test
    fun `requests without a user are not tracked`() {
        val key = UUID.randomUUID()
        val before = controller.calls.get()
        repeat(2) {
            mockMvc.post("/test/idempotent") {
                header(IdempotencyFilter.HEADER, key.toString())
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }
        }

        assertEquals(before + 2, controller.calls.get())
    }

    @Test
    fun `an expired or abandoned record is taken over, others are kept`() {
        val key = UUID.randomUUID()
        val hash = byteArrayOf(1)
        val now = Instant.parse("2026-11-21T12:00:00Z")

        fun claim(
            at: Instant,
            requestHash: ByteArray = hash,
        ) = records.claim(userId, key, "POST", "/x", requestHash, at, IdempotencyFilter.TTL, IdempotencyFilter.LOCK_TIMEOUT)

        assertIs<IdempotencyClaim.Acquired>(claim(now))
        assertIs<IdempotencyClaim.InProgress>(claim(now.plusSeconds(60)))
        assertIs<IdempotencyClaim.Mismatch>(claim(now.plusSeconds(600), byteArrayOf(2)))
        assertIs<IdempotencyClaim.Acquired>(claim(now.plusSeconds(600)))

        records.complete(userId, key, 201, mapOf("Content-Type" to "application/json"), """{"id": 1}""")
        val replay = assertIs<IdempotencyClaim.Replay>(claim(now.plusSeconds(3600)))
        assertEquals(201, replay.status)
        assertEquals(mapOf("Content-Type" to "application/json"), replay.headers)

        assertIs<IdempotencyClaim.Acquired>(claim(now.plus(IdempotencyFilter.TTL).plusSeconds(600)))
    }

    @Test
    fun `expired records are purged`() {
        val now = Instant.parse("2026-11-21T12:00:00Z")
        val expired = UUID.randomUUID()
        val live = UUID.randomUUID()
        val ttl = IdempotencyFilter.TTL
        val lock = IdempotencyFilter.LOCK_TIMEOUT
        records.claim(userId, expired, "POST", "/x", byteArrayOf(1), now.minus(ttl).minusSeconds(1), ttl, lock)
        records.claim(userId, live, "POST", "/x", byteArrayOf(1), now, ttl, lock)

        records.deleteExpired(now)

        val keys = jdbc.queryForList("select key from platform.idempotency_record where user_id = ?", UUID::class.java, userId)
        assertEquals(listOf(live), keys)
    }

    private fun send(
        key: Any,
        body: String,
    ) = mockMvc.post("/test/idempotent") {
        with(user(userId.toString()))
        header(IdempotencyFilter.HEADER, key.toString())
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.code") { value(code) }
        jsonPath("$.traceId") { exists() }
    }
}
