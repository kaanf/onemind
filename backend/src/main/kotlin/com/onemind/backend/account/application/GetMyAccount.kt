package com.onemind.backend.account.application

import com.onemind.backend.account.domain.MembershipRole
import com.onemind.backend.account.infra.persistence.MyAccountRepository
import com.onemind.backend.common.security.AuthenticationRequiredException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.util.Currency
import java.util.UUID

data class MyVenueView(
    val venueId: UUID,
    val name: String,
    val timeZone: String,
    val currency: Currency,
    val businessDayStart: LocalTime,
    val followed: Boolean,
    val role: MembershipRole,
)

data class MyMembershipView(
    val organizationId: UUID,
    val organizationName: String,
    val role: MembershipRole,
)

data class MeView(
    val userId: UUID,
    val email: String,
    /** Null when the user hasn't given one: no invented initials. */
    val displayName: String?,
    val memberships: List<MyMembershipView>,
)

/**
 * The signed-in user's own account (`GET /me`, `GET /venues`): profile, memberships and the venues they
 * may open, read per request from memberships (ADR 0004), never from the token. A deleted user is `401`.
 */
@Service
class GetMyAccount(
    private val account: MyAccountRepository,
) {
    @Transactional(readOnly = true)
    fun me(userId: UUID): MeView {
        // A valid access token of a user deleted a moment ago (as DELETE /me).
        val profile = account.profile(userId) ?: throw AuthenticationRequiredException()
        return MeView(
            userId = profile.userId,
            email = profile.email,
            displayName = profile.displayName?.takeIf { it.isNotBlank() },
            memberships =
                account
                    .memberships(
                        userId,
                    ).map { MyMembershipView(it.organizationId, it.organizationName, MembershipRole.valueOf(it.role)) },
        )
    }

    /** A deleted user with a still-valid token is `401`, not an empty list (that would read as "not onboarded"). */
    @Transactional(readOnly = true)
    fun venues(userId: UUID): List<MyVenueView> {
        account.profile(userId) ?: throw AuthenticationRequiredException()
        return account.venuesOf(userId).map {
            MyVenueView(it.venueId, it.name, it.timeZone, it.currency, it.businessDayStart, it.followed, MembershipRole.valueOf(it.role))
        }
    }
}
