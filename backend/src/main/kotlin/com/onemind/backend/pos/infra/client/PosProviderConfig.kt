package com.onemind.backend.pos.infra.client

import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import java.util.UUID

/**
 * Which [PosProvider] runs, from `onemind.pos.provider`: empty (none; the sync is skipped), `mock`
 * (set only by `application-local.yml` in the dev-only `mock` source set) or, later, `storyous`. A prod
 * start with the mock fails, however it is spelled (product rule 2: demo data is never shown as a real
 * venue's figures).
 */
@Configuration(proxyBeanMethods = false)
class PosProviderConfig(
    environment: Environment,
) {
    /** Whether the demo POS is configured: the one reading of the property, also behind the prod guard. */
    val isMock: Boolean =
        environment
            .getProperty(PROPERTY)
            .orEmpty()
            .trim()
            .equals(MOCK, ignoreCase = true)

    init {
        check(!(isMock && environment.acceptsProfiles(Profiles.of("prod")))) {
            "$PROPERTY=$MOCK is not allowed in prod: the mock POS is dev/demo data only"
        }
    }

    companion object {
        const val PROPERTY = "onemind.pos.provider"
        const val MOCK = "mock"

        /**
         * The demo POS merchant. A demo connection uses `demo-merchant/<organizationId>` (unique per
         * organization); the demo POS answers every such ID with the same demo places.
         */
        const val DEMO_MERCHANT_ID = "demo-merchant"

        fun demoMerchantFor(organizationId: UUID) = "$DEMO_MERCHANT_ID/$organizationId"
    }
}
