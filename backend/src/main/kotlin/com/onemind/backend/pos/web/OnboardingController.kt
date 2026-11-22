package com.onemind.backend.pos.web

import com.onemind.api.v1.model.DemoOnboardingRequest
import com.onemind.api.v1.model.DemoOnboardingResult
import com.onemind.api.v1.model.DemoPlace
import com.onemind.api.v1.model.DemoPlaces
import com.onemind.api.v1.model.DemoVenue
import com.onemind.api.v1.web.OnboardingApi
import com.onemind.backend.common.security.CurrentUser
import com.onemind.backend.pos.application.DemoOnboarding
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Interim demo onboarding, A5 (dev/demo only, product rule 2): every place and venue says `demo = true`. */
@RestController
class OnboardingController(
    private val onboarding: DemoOnboarding,
) : OnboardingApi {
    override fun listDemoPlaces(): ResponseEntity<DemoPlaces> =
        ResponseEntity.ok(
            onboarding.places().let { view ->
                DemoPlaces(view.merchantName, view.places.map { DemoPlace(it.placeId, it.name, it.active, demo = true) })
            },
        )

    override fun startDemoOnboarding(
        idempotencyKey: UUID,
        demoOnboardingRequest: DemoOnboardingRequest,
    ): ResponseEntity<DemoOnboardingResult> {
        val view = onboarding.start(CurrentUser.id(), demoOnboardingRequest.placeIds.toSet())
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(DemoOnboardingResult(view.organizationId, view.venues.map { DemoVenue(it.venueId, it.name, it.followed, demo = true) }))
    }
}
