package com.onemind.backend.common.security

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/** The signed-in user's ID: the access token's `sub` (ADR 0004). */
object CurrentUser {
    fun id(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            throw AuthenticationRequiredException()
        }
        return runCatching { UUID.fromString(authentication.name) }.getOrNull() ?: throw AuthenticationRequiredException()
    }
}
