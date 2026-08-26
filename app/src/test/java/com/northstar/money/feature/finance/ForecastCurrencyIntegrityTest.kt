package com.northstar.money.feature.finance

import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.RecurringItem
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastCurrencyIntegrityTest {
    @Test
    fun forecastOnlyIncludesSchedulesInBalanceCurrency() {
        val today = LocalDate.of(2026, 8, 26)
        val schedules = listOf(
            RecurringItem(
                "eur",
                "EUR bill",
                TransactionKind.EXPENSE,
                Money(1_000, "EUR"),
                today.plusDays(1).toString(),
                "MONTHLY",
            ),
            RecurringItem(
                "usd",
                "USD bill",
                TransactionKind.EXPENSE,
                Money(9_000, "USD"),
                today.plusDays(1).toString(),
                "MONTHLY",
            ),
        )

        val forecast = calculateForecast(Money(10_000, "EUR"), schedules, today)

        assertEquals(9_000L, forecast.projectedBalance.minor)
        assertEquals("EUR", forecast.projectedBalance.currencyCode)
        assertEquals(1, forecast.scheduledEvents)
    }
}
