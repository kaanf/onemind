package com.onemind.core.domain

import kotlin.time.Clock
import kotlin.time.Instant

// TEMPORARY red proof for :core:konsist (system clock read outside the clock seam). Reverted in the next commit.
fun redProofNow(): Instant = Clock.System.now()
