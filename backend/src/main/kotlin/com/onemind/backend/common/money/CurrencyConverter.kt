package com.onemind.backend.common.money

import org.jooq.impl.AbstractConverter
import java.util.Currency

/** Maps `currency char(3)` columns to [Currency] in generated jOOQ classes (see build.gradle.kts). */
class CurrencyConverter : AbstractConverter<String, Currency>(String::class.java, Currency::class.java) {
    override fun from(databaseObject: String?): Currency? = databaseObject?.let { Currency.getInstance(it.trim()) }

    override fun to(userObject: Currency?): String? = userObject?.currencyCode
}
