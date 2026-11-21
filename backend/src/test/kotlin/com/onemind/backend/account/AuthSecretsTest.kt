package com.onemind.backend.account

import com.onemind.backend.account.application.AuthSecrets
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthSecretsTest {
    private val secrets = AuthSecrets("pepper", MockEnvironment())

    @Test
    fun `codes are six digits and their hash depends on address and pepper`() {
        repeat(100) { assertTrue(secrets.newEmailCode().matches(Regex("[0-9]{6}"))) }

        val hash = secrets.hashEmailCode("Owner@Example.com", "012345")
        assertTrue(secrets.emailCodeMatches("owner@example.com", "012345", hash))
        assertFalse(secrets.emailCodeMatches("other@example.com", "012345", hash))
        assertFalse(secrets.emailCodeMatches("owner@example.com", "012346", hash))
        assertNotEquals(hash, AuthSecrets("other pepper", MockEnvironment()).hashEmailCode("owner@example.com", "012345"))
    }

    @Test
    fun `refresh tokens are random and hashed`() {
        val token = secrets.newRefreshToken()
        assertEquals(43, token.length)
        assertNotEquals(token, secrets.newRefreshToken())
        assertEquals(64, secrets.hashRefreshToken(token).length)
    }

    @Test
    fun `prod requires a pepper`() {
        assertFailsWith<IllegalStateException> { AuthSecrets("", MockEnvironment().apply { setActiveProfiles("prod") }) }
    }
}
