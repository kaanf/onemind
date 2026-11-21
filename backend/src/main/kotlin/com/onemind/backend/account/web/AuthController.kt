package com.onemind.backend.account.web

import com.onemind.api.v1.model.AppleSignInRequest
import com.onemind.api.v1.model.AuthTokens
import com.onemind.api.v1.model.EmailCodeStartRequest
import com.onemind.api.v1.model.EmailCodeVerifyRequest
import com.onemind.api.v1.model.GoogleSignInRequest
import com.onemind.api.v1.model.RefreshTokenRequest
import com.onemind.api.v1.web.AuthApi
import com.onemind.backend.account.application.EmailSignIn
import com.onemind.backend.account.application.IdTokenSignIn
import com.onemind.backend.account.application.SessionTokenService
import com.onemind.backend.account.application.SessionTokens
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.InetAddress
import java.time.ZoneOffset

@RestController
class AuthController(
    private val emailSignIn: EmailSignIn,
    private val idTokenSignIn: IdTokenSignIn,
    private val sessionTokens: SessionTokenService,
    // Request-scoped proxy; the client address behind Caddy comes from `server.forward-headers-strategy`.
    private val request: HttpServletRequest,
) : AuthApi {
    override fun startEmailSignIn(emailCodeStartRequest: EmailCodeStartRequest): ResponseEntity<Unit> {
        emailSignIn.start(emailCodeStartRequest.email, clientIp())
        return ResponseEntity.accepted().build()
    }

    override fun verifyEmailSignIn(emailCodeVerifyRequest: EmailCodeVerifyRequest): ResponseEntity<AuthTokens> =
        ResponseEntity.ok(
            emailSignIn
                .verify(emailCodeVerifyRequest.email, emailCodeVerifyRequest.code, emailCodeVerifyRequest.deviceName, clientIp())
                .toDto(),
        )

    override fun signInWithApple(appleSignInRequest: AppleSignInRequest): ResponseEntity<AuthTokens> =
        ResponseEntity.ok(
            with(appleSignInRequest) {
                idTokenSignIn.signInWithApple(identityToken, nonce, displayName, deviceName, clientIp()).toDto()
            },
        )

    override fun signInWithGoogle(googleSignInRequest: GoogleSignInRequest): ResponseEntity<AuthTokens> =
        ResponseEntity.ok(
            with(googleSignInRequest) { idTokenSignIn.signInWithGoogle(idToken, nonce, deviceName, clientIp()).toDto() },
        )

    override fun refreshTokens(refreshTokenRequest: RefreshTokenRequest): ResponseEntity<AuthTokens> =
        ResponseEntity.ok(sessionTokens.refresh(refreshTokenRequest.refreshToken).toDto())

    override fun logout(refreshTokenRequest: RefreshTokenRequest): ResponseEntity<Unit> {
        sessionTokens.logout(refreshTokenRequest.refreshToken)
        return ResponseEntity.noContent().build()
    }

    private fun clientIp(): InetAddress? = runCatching { InetAddress.ofLiteral(request.remoteAddr) }.getOrNull()

    private fun SessionTokens.toDto() =
        AuthTokens(
            accessToken = accessToken.value,
            accessTokenExpiresAt = accessToken.expiresAt.atOffset(ZoneOffset.UTC),
            refreshToken = refreshToken,
            refreshTokenExpiresAt = refreshTokenExpiresAt.atOffset(ZoneOffset.UTC),
        )
}
