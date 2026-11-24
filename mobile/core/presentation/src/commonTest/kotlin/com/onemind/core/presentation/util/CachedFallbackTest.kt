package com.onemind.core.presentation.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CachedFallbackTest {
    @Test
    fun anAnswerWithinTheDelayNeverShowsTheCache() =
        runTest {
            var slow = false
            val answer =
                awaitWithCachedFallback({
                    delay(CachedFallbackDelay - 1.milliseconds)
                    "fresh"
                }) { slow = true }

            assertEquals("fresh", answer)
            assertFalse(slow)
        }

    @Test
    fun aSlowAnswerShowsTheCacheAtTheDelayAndStillReturnsTheAnswer() =
        runTest {
            var slowAt: Long? = null
            var calls = 0
            val answer =
                awaitWithCachedFallback({
                    calls++
                    delay(8.seconds)
                    "fresh"
                }) { slowAt = testScheduler.currentTime }

            assertEquals(CachedFallbackDelay.inWholeMilliseconds, slowAt)
            assertEquals("fresh", answer)
            assertEquals(8.seconds.inWholeMilliseconds, testScheduler.currentTime)
            assertEquals(1, calls)
        }

    @Test
    fun cancellingTheCallerCancelsTheRequest() =
        runTest {
            val requestCancelled = CompletableDeferred<Unit>()
            var slow = false
            val caller =
                async {
                    awaitWithCachedFallback({
                        try {
                            delay(8.seconds)
                        } finally {
                            requestCancelled.complete(Unit)
                        }
                    }) { slow = true }
                }
            advanceTimeBy(2.seconds)
            caller.cancel()
            runCurrent()

            assertTrue(requestCancelled.isCompleted)
            assertFalse(slow)
        }
}
