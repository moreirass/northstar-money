package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.*
import com.northstar.money.data.exchange.HistoricalRateProvider
import com.northstar.money.data.exchange.RateQuote
import com.northstar.money.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialHardeningTest {
    @Test fun switchingCurrencyNeverReusesOldQuotesAndBudgetsKeepTheirCurrency() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, NorthstarDatabase::class.java).build()
        try {
            val currency = MutableStateFlow("EUR")
            val repository = OfflineFinanceRepository(db.financeDao(), baseCurrencyCode = currency,
                historicalRateProvider = HistoricalRateProvider { _, quote, date -> RateQuote(date, if (quote == "EUR") 900_000 else 800_000, "Test") })
            repository.createAccount("USD", AccountType.CHECKING, Money(10_000, "USD"))
            repository.createCategory("Food", CategoryKind.EXPENSE)
            val account = repository.observeAccounts().first().single()
            val category = repository.observeCategories().first().single()
            repository.addTransaction(TransactionKind.EXPENSE, Money(1000, "USD"), account.id, category.id, "Shop")
            repository.setBudget(category.id, Money(10_000))
            repository.refreshPendingExchangeRates()
            assertEquals(8_100L, repository.observeSummary().first().balance.minor)
            assertEquals(900L, repository.observeBudgets().first().single().spent.minor)
            currency.value = "GBP"
            assertTrue(repository.observeSummary().first().missingConversions > 0)
            assertEquals(0L, repository.observeBudgets().first().single().allocated.minor)
            repository.refreshPendingExchangeRates()
            assertEquals(7_200L, repository.observeSummary().first().balance.minor)
            assertEquals("EUR", db.financeDao().exportSnapshot().budgetAllocations.single().currencyCode)
        } finally { db.close() }
    }

    @Test fun paginationAndRealRepositorySignsDoNotTruncateExports() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, NorthstarDatabase::class.java).build()
        try {
            val repository = OfflineFinanceRepository(db.financeDao())
            repository.seedIfEmpty()
            val account = repository.observeAccounts().first().single()
            val category = repository.observeCategories().first().first { it.kind == CategoryKind.EXPENSE }
            repeat(4) { repository.addTransaction(TransactionKind.EXPENSE, Money(100), account.id, category.id, "Shop $it") }
            val page = repository.observeTransactionWindow(TransactionWindow(limit = 2)).first()
            assertEquals(2, page.items.size)
            assertTrue(page.hasMore)
            assertTrue(page.items.all { it.amount.minor == -100L && it.accountId == account.id })
            assertEquals(4, repository.allTransactions().size)
            assertEquals(400L, repository.observeSummary().first().expensesThisMonth.minor)
        } finally { db.close() }
    }

    @Test fun chainedCategoryMergesAreRejectedBeforeBreakingBackupIntegrity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, NorthstarDatabase::class.java).build()
        try {
            val repository = OfflineFinanceRepository(db.financeDao())
            listOf("A", "B", "C").forEach { repository.createCategory(it, CategoryKind.EXPENSE) }
            val categories = repository.observeCategories().first().associate { it.name to it.id }
            repository.mergeCategory(categories.getValue("A"), categories.getValue("B"))
            val rejected = try { repository.mergeCategory(categories.getValue("B"), categories.getValue("C")); false } catch (_: IllegalArgumentException) { true }
            assertTrue(rejected)
            com.northstar.money.data.backup.DatabaseSnapshotValidator().validate(db.financeDao().exportSnapshot())
        } finally { db.close() }
    }
}
