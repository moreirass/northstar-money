package com.northstar.money.domain.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecurringCalendarTest {
    @Test fun shortMonthDoesNotChangeOriginalDay() {
        val january = LocalDate.of(2026, 1, 31)
        val february = nextOccurrence(january, "MONTHLY", 1, 31, 1)
        assertEquals(LocalDate.of(2026, 2, 28), february)
        assertEquals(LocalDate.of(2026, 3, 31), nextOccurrence(february, "MONTHLY", 1, 31, 1))
    }
    @Test fun leapDayReturnsAfterNonLeapYears() {
        val date = nextOccurrence(LocalDate.of(2024, 2, 29), "YEARLY", 1, 29, 2)
        assertEquals(LocalDate.of(2025, 2, 28), date)
        assertEquals(LocalDate.of(2028, 2, 29), nextOccurrence(date, "YEARLY", 3, 29, 2))
    }
    @Test fun unsupportedAndNonMonetaryCurrencyCodesAreRejected() {
        assertFalse(validCurrency("ZZZ"))
        assertFalse(validCurrency("XXX"))
        assertFalse(validCurrency("eur"))
        assertTrue(validCurrency("JPY"))
    }
}
