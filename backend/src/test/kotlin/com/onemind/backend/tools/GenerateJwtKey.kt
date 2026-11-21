package com.onemind.backend.tools

import com.onemind.backend.common.security.JwtConfig
import java.time.LocalDate

/** Prints a new access-token signing key: `./gradlew generateJwtKey`. Store the private JWK as a secret. */
fun main() {
    val key = JwtConfig.generateKey("onemind-${LocalDate.now()}")
    println("Private JWK (JWT_SIGNING_KEY):\n${key.toJSONString()}")
    println("Public JWK (JWT_PREVIOUS_PUBLIC_KEY during the next rotation):\n${key.toPublicJWK().toJSONString()}")
}
