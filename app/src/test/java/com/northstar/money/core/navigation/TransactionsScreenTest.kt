package com.northstar.money.core.navigation

import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionsScreenTest {
    @Test
    fun monthFilterAndSummaryFollowSelectedMonth() {
        val august = listOf(
            transaction("expense", TransactionKind.EXPENSE, 14_200, "2026-08-27"),
            transaction("income", TransactionKind.INCOME, 28_000, "2026-08-26"),
            transaction("other-month", TransactionKind.EXPENSE, 500, "2026-07-31"),
        )

        val visible = transactionsInMonth(august, YearMonth.of(2026, 8))
        val summary = transactionMonthSummary(visible, "EUR")

        assertEquals(listOf("expense", "income"), visible.map { it.id })
        assertEquals(14_200, summary.expenses.minor)
        assertEquals(28_000, summary.income.minor)
        assertEquals(13_800, summary.balance.minor)
    }

    @Test
    fun summaryDoesNotMixCurrencies() {
        val items = listOf(
            transaction("eur", TransactionKind.EXPENSE, 1_000, "2026-08-27", "EUR"),
            transaction("usd", TransactionKind.EXPENSE, 2_000, "2026-08-27", "USD"),
        )

        assertEquals(1_000, transactionMonthSummary(items, "EUR").expenses.minor)
    }

    @Test
    fun titleAndTimeAreFormattedFromRealData() {
        val instant = Instant.parse("2026-08-27T08:32:00Z")
        val item = transaction("time", TransactionKind.EXPENSE, 580, "2026-08-27")
            .copy(createdAt = instant.toEpochMilli())

        assertEquals("August 2026", monthTitle(YearMonth.of(2026, 8), Locale.ENGLISH))
        assertEquals("08:32", transactionTime(item, ZoneOffset.UTC))
    }

    private fun transaction(
        id: String,
        kind: TransactionKind,
        amount: Long,
        date: String,
        currency: String = "EUR",
    ) = TransactionItem(
        id = id,
        payee = id,
        categoryName = "Category",
        accountName = "Main",
        kind = kind,
        amount = Money(amount, currency),
        localDate = date,
        cleared = true,
    )
}
