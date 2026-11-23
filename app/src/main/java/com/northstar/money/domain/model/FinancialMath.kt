package com.northstar.money.domain.model

import java.time.LocalDate

// Pontes temporárias para os consumidores Kotlin ainda não migrados.
fun Money.magnitude(): Money = if (minor < 0) Money(Math.negateExact(minor), currencyCode) else this

fun validCurrency(code: String): Boolean = CurrencyMath.validCurrency(code)

fun nextOccurrence(date: LocalDate, frequency: String, interval: Int, anchorDay: Int, anchorMonth: Int): LocalDate =
    CurrencyMath.nextOccurrence(date, frequency, interval, anchorDay, anchorMonth)

fun convertCurrencyMinor(amountMinor: Long, base: String, quote: String, rateMicros: Long): Long =
    CurrencyMath.convertCurrencyMinor(amountMinor, base, quote, rateMicros)
