package com.onemind.backend.pos

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.api.OrganizationRegistry
import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.account.api.VenueRole
import com.onemind.backend.common.error.NotFoundException
import com.onemind.backend.common.security.AccessTokenIssuer
import com.onemind.backend.pos.infra.client.PosMerchant
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.client.PosProviderException
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.testing.MutableClock
import com.onemind.backend.testing.TenancyFixture
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Interim demo onboarding (A5, dev/demo only): `GET /onboarding/demo-places`, `POST /onboarding/demo`. */
@Import(TestcontainersConfiguration::class, DemoOnboardingApiTests.Config::class)
@SpringBootTest(properties = ["db-scheduler.enabled=false", "onemind.pos.provider=mock"])
@AutoConfigureMockMvc
class DemoOnboardingApiTests {
    /** The demo POS, whose per-organization merchant calls (the places step) can be made to fail. */
    class FlakyDemoPos(
        private val mock: MockPosProvider,
    ) : PosProvider by mock {
        @Volatile var failPlacesStep = false

        @Volatile var bugInPlacesStep = false

        /** Places steps run: merchant calls for a per-organization demo connection. */
        val placesSteps = AtomicInteger()

        override fun merchant(merchantId: String): PosMerchant? {
            if (merchantId.contains('/')) {
                placesSteps.incrementAndGet()
                if (failPlacesStep) throw PosProviderException("POS unavailable")
                check(!bugInPlacesStep) { "a bug, not a POS outage" }
            }
            return mock.merchant(merchantId)
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-11-20T21:00:00Z"))

        @Bean
        @Primary
        fun flakyDemoPos(mock: MockPosProvider) = FlakyDemoPos(mock)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    @Autowired
    lateinit var venueAccess: VenueAccess

    @Autowired
    lateinit var pos: FlakyDemoPos

    @Autowired
    lateinit var transactions: TransactionTemplate

    @Autowired
    lateinit var organizations: OrganizationRegistry

    @AfterEach
    fun reset() {
        pos.failPlacesStep = false
        pos.bugInPlacesStep = false
    }

    @Test
    fun `the demo places are listed, all marked demo`() {
        val user = user()

        get(user).andExpect {
            status { isOk() }
            jsonPath("$.places.length()") { value(3) }
            jsonPath("$.places[*].demo") { value(everyItem(equalTo(true))) }
            jsonPath("$.places[?(@.placeId == 'demo-oldtown')].active") { value(contains(false)) }
        }
    }

    @Test
    fun `picking demo places creates the organization, a MOCK connection and venues, and follows the picked ones`() {
        val user = user()

        val body =
            start(user, listOf("demo-letna"))
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.venues.length()") { value(2) }
                    jsonPath("$.venues[*].demo") { value(everyItem(equalTo(true))) }
                    jsonPath("$.venues[?(@.name == 'Demo · Bar Letná')].followed") { value(contains(true)) }
                    jsonPath("$.venues[?(@.name == 'Demo · Bistro Karlín')].followed") { value(contains(false)) }
                }.andReturn()
                .response.contentAsString

        val organization =
            requireNotNull(
                jdbc.queryForObject<UUID>("select organization_id from account.membership where user_id = ? and role = 'OWNER'", user),
            )
        assertEquals(true, body.contains(organization.toString()))
        assertEquals("Demo organization", jdbc.queryForObject<String>("select name from account.organization where id = ?", organization))
        assertEquals(
            mapOf("provider" to "MOCK", "external_merchant_id" to "demo-merchant/$organization"),
            jdbc.queryForMap("select provider, external_merchant_id from pos.pos_connection where organization_id = ?", organization),
        )
        val followed =
            jdbc
                .queryForList(
                    "select venue_id from account.followed_venue where user_id = ?",
                    UUID::class.java,
                    user,
                ).filterNotNull()
        assertEquals(1, followed.size)
        assertEquals(VenueRole.OWNER, venueAccess.requireAccess(user, followed.single(), VenueRole.OWNER))
        assertFailsWith<NotFoundException> { venueAccess.requireAccess(user(), followed.single()) }
    }

    @Test
    fun `a second onboarding is a conflict, the same Idempotency-Key replays the first answer`() {
        val user = user()
        val key = UUID.randomUUID()
        val first =
            start(user, listOf("demo-letna"), key)
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString

        val replay =
            start(user, listOf("demo-letna"), key)
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        start(user, listOf("demo-karlin")).andExpectProblem(409, "ALREADY_ONBOARDED")

        assertEquals(first, replay)
        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", user))
    }

    @Test
    fun `a place that isn't an active demo place is rejected`() {
        val user = user()

        start(user, listOf("demo-oldtown")).andExpectProblem(422, "UNKNOWN_DEMO_PLACE")
        start(user, listOf("somewhere-else")).andExpectProblem(422, "UNKNOWN_DEMO_PLACE")
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", user))
    }

    @Test
    fun `when the places step fails, the next call finishes the onboarding`() {
        val user = user()
        pos.failPlacesStep = true

        start(user, listOf("demo-karlin")).andExpectProblem(503, "POS_UNAVAILABLE")
        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", user))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.followed_venue where user_id = ?", user))

        pos.failPlacesStep = false
        start(user, listOf("demo-karlin")).andExpect {
            status { isCreated() }
            jsonPath("$.venues[?(@.followed == true)].name") { value(contains("Demo · Bistro Karlín")) }
        }
        start(user, listOf("demo-karlin")).andExpectProblem(409, "ALREADY_ONBOARDED")
    }

    @Test
    fun `an account whose organization isn't a demo one can't onboard here`() {
        val user = user()
        val organization =
            requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Real bar') returning id"))
        jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, 'OWNER')", organization, user)

        start(user, listOf("demo-letna")).andExpectProblem(409, "ALREADY_ONBOARDED")
    }

    @Test
    fun `a bug in the places step stays a server error, not a POS outage`() {
        val user = user()
        pos.bugInPlacesStep = true

        start(user, listOf("demo-letna")).andExpect { status { isInternalServerError() } }
    }

    @Test
    fun `parallel starts with different keys make one organization`() {
        val user = user()
        val ready = CountDownLatch(1)
        val statuses = ConcurrentLinkedQueue<Int>()
        val threads =
            (1..2).map {
                thread {
                    ready.await()
                    statuses += start(user, listOf("demo-letna")).andReturn().response.status
                }
            }
        ready.countDown()
        threads.forEach { it.join() }

        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", user))
        assertTrue(statuses.all { it == 201 || it == 409 }, statuses.toString())
        assertTrue(201 in statuses)
        val organization =
            requireNotNull(jdbc.queryForObject<UUID>("select organization_id from account.membership where user_id = ?", user))
        assertEquals(2, jdbc.queryForObject<Int>("select count(*) from account.venue where organization_id = ?", organization))
    }

    @Test
    fun `a staff member of someone else's unfinished demo organization can't finish it`() {
        val owner = user()
        pos.failPlacesStep = true
        start(owner, listOf("demo-letna")).andExpectProblem(503, "POS_UNAVAILABLE")
        pos.failPlacesStep = false
        val organization =
            requireNotNull(jdbc.queryForObject<UUID>("select organization_id from account.membership where user_id = ?", owner))
        val staff = user()
        jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, 'STAFF')", organization, staff)
        val stepsBefore = pos.placesSteps.get()

        start(staff, listOf("demo-letna")).andExpectProblem(409, "ALREADY_ONBOARDED")

        assertEquals(stepsBefore, pos.placesSteps.get(), "no places step for a staff member")
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.venue where organization_id = ?", organization))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.followed_venue where user_id in (?, ?)", owner, staff))
    }

    @Test
    fun `onboarding never reaches another organization`() {
        val fixture = TenancyFixture(jdbc, accessTokens)

        // An owner of a real organization isn't moved into a demo one; an outsider gets an own organization.
        start(fixture.ownerB, listOf("demo-letna")).andExpectProblem(409, "ALREADY_ONBOARDED")
        start(fixture.outsider, listOf("demo-letna")).andExpect { status { isCreated() } }
        val outsiderOrganization = requireNotNull(organizations.membershipOf(fixture.outsider)).organizationId
        assertNotEquals(fixture.organizationA, outsiderOrganization)
        assertNotEquals(fixture.organizationB, outsiderOrganization)

        // Following someone else's venue does nothing; seeing it is 404.
        transactions.executeWithoutResult { organizations.follow(fixture.outsider, listOf(fixture.venueA)) }
        assertEquals(
            0,
            jdbc.queryForObject<Int>(
                "select count(*) from account.followed_venue where user_id = ? and venue_id = ?",
                fixture.outsider,
                fixture.venueA,
            ),
        )
        assertFailsWith<NotFoundException> { venueAccess.requireAccess(fixture.outsider, fixture.venueA) }
    }

    @Test
    fun `both endpoints need a signed-in user`() {
        mockMvc.get("/api/v1/onboarding/demo-places").andExpect { status { isUnauthorized() } }
    }

    private fun user(): UUID =
        requireNotNull(
            jdbc.queryForObject<UUID>(
                "insert into account.app_user (email, email_verified_at) values (?, now()) returning id",
                "demo-${UUID.randomUUID()}@example.com",
            ),
        )

    private fun get(user: UUID) =
        mockMvc.get("/api/v1/onboarding/demo-places") {
            header("Authorization", "Bearer ${accessTokens.issue(user).value}")
        }

    private fun start(
        user: UUID,
        placeIds: List<String>,
        key: UUID = UUID.randomUUID(),
    ) = mockMvc.post("/api/v1/onboarding/demo") {
        header("Authorization", "Bearer ${accessTokens.issue(user).value}")
        header("Idempotency-Key", key.toString())
        contentType = MediaType.APPLICATION_JSON
        content = """{"placeIds": [${placeIds.joinToString { "\"$it\"" }}]}"""
    }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        jsonPath("$.code") { value(code) }
    }
}
