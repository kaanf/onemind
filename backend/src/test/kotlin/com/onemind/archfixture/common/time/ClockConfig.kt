package com.onemind.archfixture.common.time

import java.time.Clock

// Rule 6: the one class allowed to read the system clock.
class ClockConfig {
    fun clock(): Clock = Clock.systemUTC()
}
