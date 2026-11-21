package com.onemind.konsist

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Product rule 6, times are the venue's: the system clock is read in one place only (the clock seam,
 * bound in Koin) and the device time zone is never used. Everything else injects `kotlin.time.Clock`
 * and formats in the venue's time zone from the API.
 */
class TimeTest {
    private val clockSeam = "/core/domain/src/commonMain/kotlin/com/onemind/core/domain/time/SystemClock.kt"

    private val systemTime =
        listOf(
            Regex("""\bClock\.System\b"""),
            Regex("""\bSystem\.currentTimeMillis\s*\("""),
            Regex("""\bSystem\.nanoTime\s*\("""),
            Regex("""\bNSDate\s*\(\s*\)"""),
            Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\.now\s*\("""),
        )
    private val deviceTimeZone =
        listOf(
            Regex("""\bTimeZone\.currentSystemDefault\s*\("""),
            Regex("""\bTimeZone\.getDefault\s*\("""),
            Regex("""\bZoneId\.systemDefault\s*\("""),
            Regex("""\bNSTimeZone\.(localTimeZone|systemTimeZone|defaultTimeZone)\b"""),
        )

    @Test
    fun systemClockIsReadOnlyInTheClockSeam() {
        val offenders = offenders(systemTime) { !it.endsWith(clockSeam) }
        assertTrue(offenders.isEmpty(), "Inject kotlin.time.Clock instead of reading the system clock:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun deviceTimeZoneIsNeverUsed() {
        val offenders = offenders(deviceTimeZone) { true }
        assertTrue(offenders.isEmpty(), "Use the venue's time zone from the API, not the device's:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun clockSeamExists() {
        assertTrue(MobileScopes.app.files.any { it.path.endsWith(clockSeam) }, "clock seam moved: update TimeTest")
    }

    private fun offenders(
        patterns: List<Regex>,
        applies: (path: String) -> Boolean,
    ): List<String> =
        MobileScopes.app.files.filter { applies(it.path) }.flatMap { file ->
            file.text.lineSequence().mapIndexedNotNull { index, line ->
                if (patterns.any { it.containsMatchIn(line) }) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
}
