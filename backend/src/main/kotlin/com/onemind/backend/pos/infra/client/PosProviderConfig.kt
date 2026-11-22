package com.onemind.backend.pos.infra.client

import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * Which [PosProvider] runs, from `onemind.pos.provider`: `mock` (dev default in `application.yml`) or,
 * later, `storyous`. A prod start with the mock fails (product rule 2: demo data is never shown as a
 * real venue's figures).
 */
@Configuration(proxyBeanMethods = false)
class PosProviderConfig(
    environment: Environment,
) {
    init {
        val provider = environment.getProperty(PROPERTY)
        check(!(provider == MOCK && environment.acceptsProfiles(Profiles.of("prod")))) {
            "$PROPERTY=$MOCK is not allowed in prod: the mock POS is dev/demo data only"
        }
    }

    companion object {
        const val PROPERTY = "onemind.pos.provider"
        const val MOCK = "mock"
    }
}
