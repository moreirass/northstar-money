package com.northstar.money.core.navigation

import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenTest {
    @Test
    fun categorySharesAggregateExpensesAndIgnoreIncome() {
        val transactions = listOf(
            transaction("Food", TransactionKind.EXPENSE, 3_200),
            transaction("Transport", TransactionKind.EXPENSE, 1_800),
            transaction("Salary", TransactionKind.INCOME, 280_000),
        )

        val result = homeCategoryShares(transactions)

        assertEquals(listOf("Food", "Transport"), result.map { it.name })
        assertEquals(listOf(3_200L, 1_800L), result.map { it.amountMinor })
        assertEquals(0.64f, result[0].fraction, 0.001f)
        assertEquals(0.36f, result[1].fraction, 0.001f)
        assertTrue(result.sumOf { it.fraction.toDouble() } in 0.999..1.001)
    }

    @Test
    fun categorySharesUseOthersForMissingCategory() {
        val result = homeCategoryShares(listOf(transaction(null, TransactionKind.EXPENSE, 450)))

        assertEquals("Outros", result.single().name)
        assertEquals(1f, result.single().fraction, 0f)
    }

    @Test
    fun dateLabelsUseRelativePortugueseNames() {
        val today = LocalDate.of(2026, 8, 31)

        assertEquals("Hoje", homeDateLabel("2026-08-31", today))
        assertEquals("Ontem", homeDateLabel("2026-08-30", today))
        assertEquals("28 Ago", homeDateLabel("2026-08-28", today))
        assertEquals("invalid", homeDateLabel("invalid", today))
    }

    private fun transaction(category: String?, kind: TransactionKind, amount: Long) = TransactionItem(
        id = "$category-$kind-$amount",
        payee = "Payee",
        categoryName = category,
        accountName = "Main",
        kind = kind,
        amount = Money(amount),
        localDate = "2026-08-31",
        cleared = true,
    )
}
