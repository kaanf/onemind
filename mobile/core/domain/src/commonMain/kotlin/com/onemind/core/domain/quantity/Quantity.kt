package com.onemind.core.domain.quantity

import kotlin.jvm.JvmInline

/**
 * A stock quantity with its unit, as the API sends it (`{"value": "1.25", "unit": "BOTTLE"}`). The spec
 * has no unit list yet (stock is phase 6), so [QuantityUnit] keeps the API code as is.
 */
data class Quantity(
    val value: DecimalValue,
    val unit: QuantityUnit,
) {
    operator fun plus(other: Quantity): Quantity = Quantity(value + other.sameUnit().value, unit)

    operator fun minus(other: Quantity): Quantity = Quantity(value - other.sameUnit().value, unit)

    private fun Quantity.sameUnit(): Quantity {
        require(unit == this@Quantity.unit) { "Can't combine ${this@Quantity.unit} and $unit" }
        return this
    }
}

/** The API's unit code (`BOTTLE`, `ML`, …); becomes an enum when the spec defines the units. */
@JvmInline
value class QuantityUnit(
    val code: String,
) {
    init {
        require(code.isNotBlank()) { "Empty unit" }
    }

    override fun toString(): String = code
}
