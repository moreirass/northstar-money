package com.northstar.money.domain.model.reference

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class KotlinMoney(val minor: Long, val currencyCode: String = "EUR") {
    init { require(validCurrency(currencyCode)) { "Unsupported monetary currency: $currencyCode" } }
    operator fun plus(other: KotlinMoney): KotlinMoney {
        require(currencyCode == other.currencyCode)
        return copy(minor = Math.addExact(minor, other.minor))
    }

    fun formatted(locale: Locale = Locale.getDefault()): String {
        val currency = Currency.getInstance(currencyCode)
        val divisor = BigDecimal.TEN.pow(currency.defaultFractionDigits)
        return NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
            .format(BigDecimal.valueOf(minor).divide(divisor))
    }

    companion object {
        fun parseMajor(text: String, currencyCode: String = "EUR"): KotlinMoney {
            val digits = Currency.getInstance(currencyCode).defaultFractionDigits
            val normalized = text.trim().replace(',', '.')
            val minor = normalized.toBigDecimal()
                .movePointRight(digits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
            return KotlinMoney(minor, currencyCode)
        }
    }
}

