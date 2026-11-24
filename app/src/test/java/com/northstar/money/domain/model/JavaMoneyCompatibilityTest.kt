package com.northstar.money.domain.model

import com.northstar.money.domain.model.reference.*
import com.northstar.money.domain.model.reference.convertCurrencyMinor as legacyConversion
import com.northstar.money.domain.model.reference.nextOccurrence as legacyOccurrence
import com.northstar.money.domain.model.reference.validCurrency as legacyCurrency
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class JavaMoneyCompatibilityTest {
    @Test fun parsingFormattingEqualityAndOverflowMatchKotlin() {
        for (code in listOf("EUR", "USD", "GBP", "BRL", "CHF", "JPY", "KWD")) {
            for (minor in listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
                val old = KotlinMoney(minor, code)
                val migrated = Money(minor, code)
                assertEquals(old.hashCode(), migrated.hashCode())
                assertEquals(old.toString().replace("KotlinMoney(", "Money("), migrated.toString())
                assertEquals(migrated, Money(minor, code))
                for (locale in listOf(Locale.US, Locale.forLanguageTag("pt-PT"), Locale.JAPAN)) {
                    assertEquals(old.formatted(locale), migrated.formatted(locale))
                }
            }
        }
        for (text in listOf("42.50", "42,50", "\u00a0-42,50\u2003", "1E2")) {
            assertEquals(KotlinMoney.parseMajor(text).minor, Money.parseMajor(text).minor)
        }
        assertThrows(ArithmeticException::class.java) { Money.parseMajor("1.001", "EUR") }
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE).plus(Money(1)) }
        assertThrows(IllegalArgumentException::class.java) { Money(1).plus(Money(1, "JPY")) }
    }

    @Test fun conversionsAndCalendarAnchorsMatchKotlin() {
        for (base in listOf("EUR", "JPY", "KWD")) for (quote in listOf("EUR", "JPY", "KWD")) {
            for (amount in listOf(-12345L, -1L, 0L, 1L, 12345L)) for (rate in listOf(500_000L, 1_234_567L)) {
                assertEquals(legacyConversion(amount, base, quote, rate),
                    CurrencyMath.convertCurrencyMinor(amount, base, quote, rate))
            }
        }
        for (date in listOf("2024-02-29", "2025-01-31", "2026-12-31")) {
            val day = LocalDate.parse(date)
            for (frequency in listOf("WEEKLY", "MONTHLY", "YEARLY")) {
                assertEquals(legacyOccurrence(day, frequency, 1, day.dayOfMonth, day.monthValue),
                    CurrencyMath.nextOccurrence(day, frequency, 1, day.dayOfMonth, day.monthValue))
            }
        }
        for (code in listOf("EUR", "JPY", "KWD", "XXX", "ZZZ", "eur", "", "EURO")) {
            assertEquals(legacyCurrency(code), CurrencyMath.validCurrency(code))
        }
    }
}
