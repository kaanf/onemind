package com.onemind.backend.account

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.api.MembershipRole
import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.common.security.AccessTokenIssuer
import com.onemind.backend.common.security.CurrentUser
import com.onemind.backend.testing.TenancyFixture
import com.onemind.backend.testing.expectNoCrossVenueAccess
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** [VenueAccess] through venue-scoped probe endpoints, and the cross-venue helper every endpoint uses. */
@Import(TestcontainersConfiguration::class, VenueAccessTests.Config::class)
@SpringBootTest
@AutoConfigureMockMvc
class VenueAccessTests {
    @RestController
    class VenueProbeController(
        private val venueAccess: VenueAccess,
    ) {
        @GetMapping("/test/venues/{venueId}/members")
        fun members(
            @PathVariable venueId: UUID,
        ) = mapOf("role" to venueAccess.requireAccess(CurrentUser.id(), venueId).name)

        @GetMapping("/test/venues/{venueId}/owners")
        fun owners(
            @PathVariable venueId: UUID,
        ) = mapOf("role" to venueAccess.requireAccess(CurrentUser.id(), venueId, MembershipRole.OWNER).name)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun venueProbeController(venueAccess: VenueAccess) = VenueProbeController(venueAccess)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    private val tenancy by lazy { TenancyFixture(jdbc, accessTokens) }

    @Test
    fun `members of the venue's organization have access with their role`() {
        members(tenancy.venueA, tenancy.ownerA).andExpect { jsonPath("$.role") { value("OWNER") } }
        members(tenancy.venueA, tenancy.staffA).andExpect { jsonPath("$.role") { value("STAFF") } }
    }

    @Test
    fun `other organizations, outsiders and unknown venues get 404`() {
        tenancy.expectNoCrossVenueAccess { venueId, token -> members(venueId, token) }
        tenancy.expectNoCrossVenueAccess { venueId, token -> owners(venueId, token) }
    }

    @Test
    fun `a role that is too low is 403, an owner passes`() {
        owners(tenancy.venueA, tenancy.token(tenancy.staffA)).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("VENUE_ROLE_REQUIRED") }
            jsonPath("$.params.role") { value("OWNER") }
        }
        owners(tenancy.venueA, tenancy.token(tenancy.ownerA)).andExpect { status { isOk() } }
    }

    @Test
    fun `venues of an organization scheduled for deletion are not accessible`() {
        tenancy.scheduleDeletion(tenancy.organizationA)

        members(tenancy.venueA, tenancy.staffA).andExpect { status { isNotFound() } }
    }

    @Test
    fun `a disabled venue is not accessible, also to its owner`() {
        tenancy.disable(tenancy.venueA)

        members(tenancy.venueA, tenancy.ownerA).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VENUE_NOT_FOUND") }
        }
    }

    @Test
    fun `access needs a signed-in user`() {
        mockMvc.get("/test/venues/${tenancy.venueA}/members").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `access follows the membership, not the token`() {
        val token = tenancy.token(tenancy.staffA)
        members(tenancy.venueA, token).andExpect { status { isOk() } }

        jdbc.update("delete from account.membership where user_id = ?", tenancy.staffA)

        members(tenancy.venueA, token).andExpect { status { isNotFound() } }
    }

    private fun members(
        venueId: UUID,
        userId: UUID,
    ) = members(venueId, tenancy.token(userId))

    private fun members(
        venueId: UUID,
        token: String,
    ) = mockMvc.get("/test/venues/$venueId/members") { header("Authorization", "Bearer $token") }

    private fun owners(
        venueId: UUID,
        token: String,
    ) = mockMvc.get("/test/venues/$venueId/owners") { header("Authorization", "Bearer $token") }
}
