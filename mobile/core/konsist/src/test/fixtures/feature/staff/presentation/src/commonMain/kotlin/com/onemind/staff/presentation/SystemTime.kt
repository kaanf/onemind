// Fixture (not compiled): reading the system clock and the device time zone outside the seam.
// Clock.System in a comment is fine.
package com.onemind.staff.presentation

import kotlin.time.Clock
import kotlin.time.Clock.System
import kotlinx.datetime.TimeZone

fun viaImport() = System.now()

fun viaClass() = Clock.System.now()

fun deviceZone() = TimeZone.currentSystemDefault()

val note = "Clock.System is fine in a string"
