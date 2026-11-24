package com.onemind.core.designsystem.components

import com.onemind.core.designsystem.theme.OneMindMotion
import kotlin.test.Test
import kotlin.test.assertEquals

class ShimmerPhaseTest {
    private val sweep = OneMindMotion.SHIMMER_MILLIS.toLong()

    @Test
    fun theSweepRepeatsEveryShimmerDuration() {
        assertEquals(0f, shimmerPhase(0, durationScale = 1f))
        assertEquals(0.5f, shimmerPhase(sweep / 2, durationScale = 1f))
        assertEquals(0.25f, shimmerPhase(7 * sweep + sweep / 4, durationScale = 1f))
    }

    @Test
    fun aSlowerAnimationScaleStretchesTheSweep() {
        assertEquals(0.25f, shimmerPhase(sweep / 2, durationScale = 2f))
    }

    @Test
    fun withAnimationsOffTheShimmerStaysStill() {
        listOf(0L, sweep / 3, 123_456_789L).forEach { frame ->
            assertEquals(0f, shimmerPhase(frame, durationScale = 0f))
        }
    }
}
