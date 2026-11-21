package com.onemind.backend.common.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerExceptionResolver
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MinimumAppVersionFilterTest {
    private val neverResolve = HandlerExceptionResolver { _, _, _, e -> error("unexpected rejection: $e") }

    @Test
    fun `without minimums nothing is checked`() {
        val filter = MinimumAppVersionFilter(AppVersionProperties(), neverResolve)
        val chain = MockFilterChain()

        filter.doFilter(oldIosRequest(), MockHttpServletResponse(), chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `a minimum that is not a version stops the application`() {
        assertFailsWith<IllegalArgumentException> { MinimumAppVersionFilter(AppVersionProperties(minimumIos = "latest"), neverResolve) }
    }

    private fun oldIosRequest() =
        MockHttpServletRequest("POST", "/api/v1/auth/refresh").apply {
            addHeader(MinimumAppVersionFilter.PLATFORM_HEADER, "IOS")
            addHeader(MinimumAppVersionFilter.VERSION_HEADER, "0.0.1")
        }
}
