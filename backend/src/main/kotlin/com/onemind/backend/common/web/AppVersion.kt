package com.onemind.backend.common.web

/**
 * An app version as the clients send it in `X-App-Version`: one to four numeric parts
 * (`1.4`, `1.4.2`), optionally followed by a pre-release or build suffix (`1.4.2-beta.1`, `1.4.2+87`).
 * Comparison uses the numeric parts only; missing parts count as 0, a suffix is ignored.
 */
class AppVersion private constructor(
    private val parts: List<Int>,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        (0 until PARTS)
            .asSequence()
            .map { parts.getOrElse(it) { 0 }.compareTo(other.parts.getOrElse(it) { 0 }) }
            .firstOrNull { it != 0 } ?: 0

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = (0 until PARTS).map { parts.getOrElse(it) { 0 } }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        private const val PARTS = 4
        private val PATTERN = Regex("""^(\d{1,9}(?:\.\d{1,9}){0,3})(?:[-+][0-9A-Za-z.+-]*)?$""")

        /** The version, or `null` if [value] isn't one. */
        fun parse(value: String): AppVersion? =
            PATTERN.matchEntire(value.trim())?.let { match -> AppVersion(match.groupValues[1].split('.').map(String::toInt)) }
    }
}
