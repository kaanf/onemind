package com.onemind.core.domain.time

import kotlin.time.Clock

/**
 * The only place that reads the system clock (`:core:konsist` `TimeTest` guards this). Koin binds it as
 * [Clock]; everything else injects [Clock] so tests can pass a fixed one.
 */
val systemClock: Clock = Clock.System
