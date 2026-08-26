package com.northstar.money.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun `major units parse exactly to minor units`() {
        assertEquals(Money(4_250, "EUR"), Money.parseMajor("42.50", "EUR"))
        assertEquals(Money(4_250, "EUR"), Money.parseMajor("42,50", "EUR"))
    }

    @Test
    fun `excess fractional precision is rejected instead of rounded silently`() {
        assertThrows(ArithmeticException::class.java) {
            Money.parseMajor("42.501", "EUR")
        }
    }

    @Test
    fun `addition rejects mixed currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, "EUR") + Money(100, "USD")
        }
    }
}

