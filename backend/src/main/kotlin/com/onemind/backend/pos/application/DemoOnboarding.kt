package com.onemind.backend.pos.application

import com.onemind.backend.account.api.OrganizationRegistry
import com.onemind.backend.account.api.VenueRole
import com.onemind.backend.common.error.ConflictException
import com.onemind.backend.common.error.NotFoundException
import com.onemind.backend.common.error.RuleViolationException
import com.onemind.backend.common.error.ServiceUnavailableException
import com.onemind.backend.pos.infra.client.PosMerchant
import com.onemind.backend.pos.infra.client.PosPlaceState
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.client.PosProviderConfig
import com.onemind.backend.pos.infra.client.PosProviderException
import com.onemind.backend.pos.infra.persistence.PosConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class FeatureUnavailableException : NotFoundException(code = "FEATURE_UNAVAILABLE", detail = "This feature isn't available here.")

class AlreadyOnboardedException : ConflictException(code = "ALREADY_ONBOARDED", detail = "This account is already set up.")

class UnknownDemoPlaceException(
    placeId: String,
) : RuleViolationException(code = "UNKNOWN_DEMO_PLACE", detail = "This isn't an active demo place.", params = mapOf("placeId" to placeId))

class PosUnavailableException(
    cause: Throwable? = null,
) : ServiceUnavailableException(code = "POS_UNAVAILABLE", detail = "The POS couldn't be reached. Try again in a moment.", cause = cause)

/** A demo place as A5 lists it. */
data class DemoPlaceView(
    val placeId: String,
    val name: String,
    val active: Boolean,
)

data class DemoPlacesView(
    val merchantName: String,
    val places: List<DemoPlaceView>,
)

data class DemoVenueView(
    val venueId: UUID,
    val name: String,
    val followed: Boolean,
)

data class DemoOnboardingView(
    val organizationId: UUID,
    val venues: List<DemoVenueView>,
)

/**
 * Interim onboarding until the Storyous connection exists (P15; `docs/product/SCREENS.md`, A5): a new
 * account picks demo places, which creates its organization (the caller as owner), a `MOCK` connection
 * to the demo POS, a venue per active demo place, and follows the picked ones. Dev/demo only (rule 2):
 * without the demo POS ([PosProviderConfig.isMock], the same reading as the prod guard) it is
 * `404 FEATURE_UNAVAILABLE`.
 *
 * Steps: organization + connection (one transaction), places → venues ([SyncPlaces], POS call outside
 * a transaction), follow (one transaction). The first step locks the user, so parallel starts make one
 * organization. If the places step fails (the POS can't be read), the organization stays and a repeat
 * call by its owner finishes the job; `409 ALREADY_ONBOARDED` once the caller follows venues, when their
 * organization isn't a demo one, or when they aren't its owner.
 */
@Service
class DemoOnboarding(
    private val config: PosProviderConfig,
    private val providers: ObjectProvider<PosProvider>,
    private val organizations: OrganizationRegistry,
    private val connections: PosConnectionRepository,
    private val syncPlaces: SyncPlaces,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun places(): DemoPlacesView {
        val merchant = demoMerchant(demoProvider())
        return DemoPlacesView(merchant.name, merchant.places.map { DemoPlaceView(it.placeId, it.name, it.state == PosPlaceState.ACTIVE) })
    }

    fun start(
        userId: UUID,
        placeIds: Collection<String>,
    ): DemoOnboardingView {
        val provider = demoProvider()
        val merchant = demoMerchant(provider)
        val active = merchant.places.filter { it.state == PosPlaceState.ACTIVE }.associateBy { it.placeId }
        placeIds.firstOrNull { it !in active }?.let { throw UnknownDemoPlaceException(it) }

        val connection =
            requireNotNull(
                transactions.execute {
                    // Two taps or two devices: the second waits here and then sees the first one's organization.
                    connections.lockOnboarding(userId)
                    when (val membership = organizations.membershipOf(userId)) {
                        null -> {
                            val created = organizations.createForOwner(userId, DEMO_ORGANIZATION_NAME)
                            connections.insert(created, MOCK_PROVIDER, PosProviderConfig.demoMerchantFor(created))
                        }

                        else -> {
                            // Only the owner of a demo organization that follows nothing yet finishes a failed start.
                            val existing = connections.forOrganization(membership.organizationId)
                            val unfinished =
                                membership.role == VenueRole.OWNER &&
                                    existing?.provider == MOCK_PROVIDER &&
                                    organizations.followedVenueIds(userId).isEmpty()
                            if (!unfinished || existing == null) throw AlreadyOnboardedException()
                            existing
                        }
                    }
                },
            )
        val places =
            try {
                syncPlaces.run(connection, provider)
            } catch (e: PosProviderException) {
                log.warn("Demo onboarding couldn't read the POS: connectionId={}, error={}", connection.id, e.javaClass.simpleName)
                throw PosUnavailableException(e)
            } catch (e: PosMerchantNotFoundException) {
                log.warn("Demo onboarding couldn't read the POS: connectionId={}, error={}", connection.id, e.javaClass.simpleName)
                throw PosUnavailableException(e)
            }
        val picked =
            places.venues
                .filter { it.placeId in placeIds }
                .map { it.venueId }
                .toSet()
        transactions.executeWithoutResult { organizations.follow(userId, picked) }
        return DemoOnboardingView(
            organizationId = connection.organizationId,
            venues =
                places.venues
                    .filter { it.active }
                    .map { DemoVenueView(it.venueId, active.getValue(it.placeId).name, it.venueId in picked) },
        )
    }

    private fun demoProvider(): PosProvider = providers.ifAvailable?.takeIf { config.isMock } ?: throw FeatureUnavailableException()

    private fun demoMerchant(provider: PosProvider): PosMerchant =
        provider.merchant(PosProviderConfig.DEMO_MERCHANT_ID) ?: throw PosUnavailableException()

    companion object {
        const val DEMO_ORGANIZATION_NAME = "Demo organization"
        const val MOCK_PROVIDER = "MOCK"
    }
}
