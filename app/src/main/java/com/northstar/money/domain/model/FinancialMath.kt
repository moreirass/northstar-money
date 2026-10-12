package com.northstar.money.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

fun Money.magnitude(): Money = if (minor < 0) copy(minor = Math.negateExact(minor)) else this

fun validCurrency(code: String): Boolean = runCatching {
    code.length == 3 && code == code.uppercase(java.util.Locale.ROOT) &&
        Currency.getInstance(code).defaultFractionDigits in 0..4
}.getOrDefault(false)

/** Keep the original calendar day even after a short month or leap year. */
fun nextOccurrence(date: LocalDate, frequency: String, interval: Int, anchorDay: Int, anchorMonth: Int): LocalDate {
    require(interval > 0 && anchorDay in 1..31 && anchorMonth in 1..12)
    return when (frequency) {
        "WEEKLY" -> date.plusWeeks(interval.toLong())
        "MONTHLY" -> YearMonth.from(date).plusMonths(interval.toLong()).let {
            it.atDay(anchorDay.coerceAtMost(it.lengthOfMonth()))
        }
        "YEARLY" -> YearMonth.of(Math.addExact(date.year, interval), anchorMonth).let {
            it.atDay(anchorDay.coerceAtMost(it.lengthOfMonth()))
        }
        else -> error("Unsupported recurring frequency")
    }
}

/** Currency conversion belongs to the domain, independently of HTTP or Android. */
fun convertCurrencyMinor(amountMinor: Long, base: String, quote: String, rateMicros: Long): Long {
    require(validCurrency(base) && validCurrency(quote) && rateMicros > 0)
    return java.math.BigDecimal.valueOf(amountMinor)
        .movePointLeft(Currency.getInstance(base).defaultFractionDigits)
        .multiply(java.math.BigDecimal.valueOf(rateMicros, 6))
        .movePointRight(Currency.getInstance(quote).defaultFractionDigits)
        .setScale(0, java.math.RoundingMode.HALF_EVEN).longValueExact()
}
