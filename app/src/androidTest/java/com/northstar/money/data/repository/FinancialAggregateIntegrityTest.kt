package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialAggregateIntegrityTest {
    private lateinit var database: NorthstarDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aggregatesNeverMixCurrenciesOrArchivedAccountBalances() = runBlocking {
        val dao = database.financeDao()
        val repository = OfflineFinanceRepository(dao)
        val now = System.currentTimeMillis()
        val monthStart = LocalDate.now().withDayOfMonth(1).toString()
        dao.insertAccounts(
            listOf(
                AccountEntity("eur", "EUR account", "CHECKING", "EUR", 10_000, null, 1, 1),
                AccountEntity("archived", "Archived", "CHECKING", "EUR", 50_000, 2, 1, 1),
                AccountEntity("usd", "USD account", "CHECKING", "USD", 30_000, null, 1, 1),
            ),
        )
        dao.insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))
        insertExpense("eur-expense", "eur-entry", "eur", -1_000, "EUR", monthStart, now)
        insertExpense("archived-expense", "archived-entry", "archived", -7_000, "EUR", monthStart, now)
        insertExpense("usd-expense", "usd-entry", "usd", -2_000, "USD", monthStart, now)
        dao.upsertBudget(BudgetAllocationEntity("budget", monthStart, "food", 5_000))
        dao.upsertDebt(DebtProfileEntity("usd-debt", "usd", 300, 1_000, 15, now))

        val summary = repository.observeSummary().first()
        val accounts = repository.observeAccounts().first().associateBy { it.id }
        val budget = repository.observeBudgets().first().single()
        val debt = repository.observeDebts().first().single()

        assertEquals("EUR", summary.balance.currencyCode)
        assertEquals(9_000L, summary.balance.minor)
        assertEquals(1_000L, summary.expensesThisMonth.minor)
        assertEquals(9_000L, accounts.getValue("eur").balance.minor)
        assertEquals(28_000L, accounts.getValue("usd").balance.minor)
        assertEquals(8_000L, budget.spent.minor)
        assertEquals("USD", debt.minimumPayment.currencyCode)
    }

    @Test
    fun repositorySupportsCrossCurrencyTransferWithoutMixingAccountBalances() = runBlocking {
        val dao = database.financeDao()
        val repository = OfflineFinanceRepository(dao)
        dao.insertAccounts(
            listOf(
                AccountEntity("eur", "EUR account", "CHECKING", "EUR", 0, null, 1, 1),
                AccountEntity("usd", "USD account", "CHECKING", "USD", 0, null, 1, 1),
            ),
        )
        dao.insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))

        val transactionFailure = runCatching {
            repository.addTransaction(TransactionKind.EXPENSE, Money(100, "EUR"), "usd", "food", "Shop")
        }.exceptionOrNull()
        repository.transfer(
            sourceAmount = Money(100, "EUR"),
            destinationAmount = Money(125, "USD"),
            sourceAccountId = "eur",
            destinationAccountId = "usd",
            note = "Exchange",
        )

        assertTrue(transactionFailure is IllegalArgumentException)
        val accounts = repository.observeAccounts().first().associateBy { it.id }
        assertEquals(-100L, accounts.getValue("eur").balance.minor)
        assertEquals(125L, accounts.getValue("usd").balance.minor)
        val entries = dao.exportSnapshot().transactionEntries.associateBy { it.accountId }
        assertEquals("EUR", entries.getValue("eur").currencyCode)
        assertEquals("USD", entries.getValue("usd").currencyCode)
    }

    private suspend fun insertExpense(
        transactionId: String,
        entryId: String,
        accountId: String,
        amountMinor: Long,
        currencyCode: String,
        localDate: String,
        now: Long,
    ) {
        database.financeDao().insertTransaction(
            TransactionEntity(transactionId, "EXPENSE", localDate, transactionId, "", now, now),
            TransactionEntryEntity(entryId, transactionId, accountId, "food", amountMinor, currencyCode, true),
        )
    }
}
