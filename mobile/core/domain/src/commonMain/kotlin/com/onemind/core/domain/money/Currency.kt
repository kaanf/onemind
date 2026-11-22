package com.onemind.core.domain.money

import kotlin.jvm.JvmInline

/** ISO 4217 currency code (`CZK`), as the API sends it. */
@JvmInline
value class Currency(
    val code: String,
) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Not an ISO 4217 code: '$code'" }
    }

    override fun toString(): String = code

    companion object {
        val CZK = Currency("CZK")
    }
}
