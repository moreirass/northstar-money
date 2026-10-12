package com.northstar.money.core.navigation

import com.northstar.money.domain.model.*
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class SignedFinancialDataTest {
    private fun item(id: String, kind: TransactionKind, minor: Long, currency: String = "EUR") =
        TransactionItem(id, "Shop", "Food", "Main", kind, Money(minor, currency), "2026-10-01", false)

    @Test fun actualRepositorySignsProduceCorrectCashFlowAndCharts() {
        val items = listOf(item("income", TransactionKind.INCOME, 100_000), item("expense", TransactionKind.EXPENSE, -20_000))
        val summary = transactionMonthSummary(items, "EUR")
        assertEquals(20_000L, summary.expenses.minor)
        assertEquals(80_000L, summary.balance.minor)
        assertEquals(20_000L, homeCategoryShares(items).single().amountMinor)
        assertEquals(20_000L, budgetTrend(items, "Food", YearMonth.of(2026, 10)).last().spentMinor)
    }

    @Test fun onlyMatchingCurrencyAndDateAreUsedForConversion() {
        val item = item("usd", TransactionKind.EXPENSE, -10_000, "USD")
        val rate = HistoricalExchangeRate("r", "usd", "e", "USD", "EUR", 900_000, -9_000, "2026-10-01", "Test", "AVAILABLE")
        assertEquals(9_000L, transactionMonthSummary(listOf(item), "EUR", listOf(rate)).expenses.minor)
        assertNull(item.convertedTo("GBP", listOf(rate)))
        assertNull(item.convertedTo("EUR", listOf(rate.copy(rateLocalDate = "2026-09-30"))))
    }

    @Test fun categoryRemainderPreservesTotalAndWholeDonut() {
        val items = (1..8).map { item("$it", TransactionKind.EXPENSE, -100).copy(categoryName = "Category $it") }
        val shares = homeCategoryShares(items)
        assertEquals(800L, shares.sumOf { it.amountMinor })
        assertEquals(1.0, shares.sumOf { it.fraction.toDouble() }, 0.00001)
    }
}
