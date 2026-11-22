package com.onemind.core.data.session

import com.onemind.core.api.model.AuthTokens
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The tokens of this device's sign-in (ADR 0004), as persisted in [SecureSessionStore]. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val version: Int = FORMAT_VERSION,
) {
    /** Never print tokens, even by accident in a log or crash report. */
    override fun toString(): String =
        "StoredSession(accessTokenExpiresAt=$accessTokenExpiresAt, refreshTokenExpiresAt=$refreshTokenExpiresAt)"

    companion object {
        const val FORMAT_VERSION = 1

        fun from(tokens: AuthTokens): StoredSession =
            StoredSession(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accessTokenExpiresAt = tokens.accessTokenExpiresAt,
                refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
            )
    }
}
