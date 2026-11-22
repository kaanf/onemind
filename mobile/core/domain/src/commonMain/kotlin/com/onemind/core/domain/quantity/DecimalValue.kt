package com.onemind.core.domain.quantity

/**
 * An exact decimal for quantities and rates, which the API sends as decimal strings (`"1.25"`,
 * `"0.6000"`; `docs/api/README.md`). Stored as an unscaled `Long` and a scale (`1.25` = 125 × 10⁻²), so
 * nothing is ever parsed into a `Double`. Equality ignores trailing zeros (`1.50` == `1.5`).
 */
class DecimalValue private constructor(
    private val unscaled: Long,
    val scale: Int,
) : Comparable<DecimalValue> {
    operator fun plus(other: DecimalValue): DecimalValue {
        val scale = maxOf(scale, other.scale)
        return DecimalValue(addExact(rescaled(scale), other.rescaled(scale)), scale)
    }

    operator fun minus(other: DecimalValue): DecimalValue = this + -other

    operator fun unaryMinus(): DecimalValue {
        check(unscaled != Long.MIN_VALUE) { "Decimal overflow" }
        return DecimalValue(-unscaled, scale)
    }

    val isNegative: Boolean get() = unscaled < 0
    val isZero: Boolean get() = unscaled == 0L

    override fun compareTo(other: DecimalValue): Int {
        val scale = maxOf(scale, other.scale)
        return rescaled(scale).compareTo(other.rescaled(scale))
    }

    override fun equals(other: Any?): Boolean = other is DecimalValue && compareTo(other) == 0

    override fun hashCode(): Int = stripped().let { 31 * it.unscaled.hashCode() + it.scale }

    /** The API form: plain digits, `.` as the separator, `-` for negatives, scale kept (`"1.250"`). */
    override fun toString(): String {
        val digits = (if (unscaled < 0) -unscaled else unscaled).toString().padStart(scale + 1, '0')
        val sign = if (unscaled < 0) "-" else ""
        return if (scale == 0) sign + digits else "$sign${digits.dropLast(scale)}.${digits.takeLast(scale)}"
    }

    private fun rescaled(target: Int): Long {
        var value = unscaled
        repeat(target - scale) { value = multiplyExact(value, 10) }
        return value
    }

    private fun stripped(): DecimalValue {
        var value = unscaled
        var scale = scale
        while (scale > 0 && value % 10 == 0L) {
            value /= 10
            scale--
        }
        return DecimalValue(value, scale)
    }

    companion object {
        private val pattern = Regex("^-?\\d+(\\.\\d+)?$")
        private const val MAX_SCALE = 9

        /** Parses the API's decimal string; anything else (exponents, commas, spaces) is rejected. */
        fun parse(text: String): DecimalValue {
            require(pattern.matches(text)) { "Not a decimal string: '$text'" }
            val fraction = text.substringAfter('.', "")
            require(fraction.length <= MAX_SCALE) { "Too many decimals: '$text'" }
            val unscaled =
                (text.substringBefore('.') + fraction).toLongOrNull() ?: throw IllegalArgumentException("Decimal too large: '$text'")
            return DecimalValue(unscaled, fraction.length)
        }

        fun of(whole: Long): DecimalValue = DecimalValue(whole, 0)
    }
}

private fun addExact(
    a: Long,
    b: Long,
): Long {
    val result = a + b
    check((a xor result) and (b xor result) >= 0) { "Decimal overflow" }
    return result
}

private fun multiplyExact(
    a: Long,
    b: Long,
): Long {
    val result = a * b
    check(a == 0L || (result / a == b && !(a == -1L && b == Long.MIN_VALUE))) { "Decimal overflow" }
    return result
}
