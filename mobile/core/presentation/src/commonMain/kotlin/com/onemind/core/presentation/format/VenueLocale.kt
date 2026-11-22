package com.onemind.core.presentation.format

import com.onemind.core.domain.money.Currency
import kotlin.jvm.JvmInline

/**
 * The locale money is formatted in: the venue's, never the UI language (ADR 0012 §3, `48 320 Kč` in an
 * English UI). A BCP 47 tag (`cs-CZ`).
 */
@JvmInline
value class VenueLocale(
    val tag: String,
) {
    companion object {
        val CZECH = VenueLocale("cs-CZ")

        /** Placeholder for currencies without a known venue locale. */
        val FALLBACK = VenueLocale("en-US")

        /**
         * **Interim:** the API doesn't send a venue locale yet, so it is inferred from the currency (CZK →
         * `cs-CZ`, anything else → [FALLBACK]). This is the only place to change once the venue carries
         * its locale (roadmap: venue locale in the API).
         */
        fun forCurrency(currency: Currency): VenueLocale = if (currency == Currency.CZK) CZECH else FALLBACK
    }
}
