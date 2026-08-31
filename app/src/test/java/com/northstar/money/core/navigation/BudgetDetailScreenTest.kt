package com.northstar.money.core.navigation

import androidx.compose.ui.graphics.Color
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetDetailScreenTest {
    @Test
    fun filtersCurrentCategoryExpensesAndBuildsSixMonthTrend() {
        val items = listOf(
            transaction("aug-food", "Food", "2026-08-20", 4_200),
            transaction("jul-food", "Food", "2026-07-20", 3_000),
            transaction("other", "Transport", "2026-08-20", 9_000),
            transaction("income", "Food", "2026-08-20", 7_000, TransactionKind.INCOME),
        )

        assertEquals(listOf("aug-food"), transactionsForBudgetMonth(items, "Food", YearMonth.of(2026, 8)).map { it.id })
        val trend = budgetTrend(items, "Food", YearMonth.of(2026, 8))
        assertEquals(6, trend.size)
        assertEquals(3_000, trend[4].spentMinor)
        assertEquals(4_200, trend[5].spentMinor)
    }

    @Test
    fun detailUsesDesignThresholdsAndReadableRelativeDate() {
        assertEquals(Color(0xFF10B981), budgetDetailStatusColor(7_000, 10_000))
        assertEquals(Color(0xFFF59E0B), budgetDetailStatusColor(7_001, 10_000))
        assertEquals(Color(0xFFF43F5E), budgetDetailStatusColor(10_000, 10_000))

        val item = transaction("today", "Food", "2026-08-31", 100).copy(
            createdAt = Instant.parse("2026-08-31T14:20:00Z").toEpochMilli(),
        )
        assertEquals(
            "Today, 14:20",
            budgetTransactionDateLabel(
                item,
                today = LocalDate.of(2026, 8, 31),
                zoneId = ZoneOffset.UTC,
                locale = Locale.ENGLISH,
            ),
        )
    }

    private fun transaction(
        id: String,
        category: String,
        date: String,
        amount: Long,
        kind: TransactionKind = TransactionKind.EXPENSE,
    ) = TransactionItem(
        id = id,
        payee = id,
        categoryName = category,
        accountName = "Main",
        kind = kind,
        amount = Money(amount),
        localDate = date,
        cleared = true,
    )
}
