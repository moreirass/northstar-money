package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionEditingTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        val dao = database.financeDao()
        dao.insertAccounts(
            listOf(
                AccountEntity("account-1", "First", "CHECKING", "EUR", 0, null, 1, 1),
                AccountEntity("account-2", "Second", "SAVINGS", "EUR", 0, null, 1, 1),
                AccountEntity("usd", "USD", "CHECKING", "USD", 0, null, 1, 1),
            ),
        )
        dao.insertCategories(
            listOf(
                CategoryEntity("food", "Food", "EXPENSE", 0),
                CategoryEntity("travel", "Travel", "EXPENSE", 1),
            ),
        )
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun editExpense_preservesIdsAndCreatedAtWhileUpdatingEntryAtomically() = runBlocking {
        seedExpense()
        val editable = repository.getTransactionForEdit("transaction")

        repository.updateTransaction(
            editable.copy(
                localDate = "2026-08-20",
                payee = "New payee",
                note = "Updated note",
                amount = Money(2_000, "EUR"),
                accountId = "account-2",
                categoryId = "travel",
            ),
        )

        val snapshot = database.financeDao().exportSnapshot()
        val transaction = snapshot.transactions.single()
        val entry = snapshot.transactionEntries.single()
        assertEquals("transaction", transaction.id)
        assertEquals(10L, transaction.createdAt)
        assertEquals("2026-08-20", transaction.localDate)
        assertEquals("New payee", transaction.payee)
        assertEquals("Updated note", transaction.note)
        assertEquals("entry", entry.id)
        assertEquals("account-2", entry.accountId)
        assertEquals("travel", entry.categoryId)
        assertEquals(-2_000L, entry.amountMinor)
        assertEquals(false, entry.cleared)
    }

    @Test
    fun editExpense_currencyMismatchDoesNotChangeAnything() = runBlocking {
        seedExpense()
        val before = database.financeDao().exportSnapshot()
        val editable = repository.getTransactionForEdit("transaction")

        val failure = runCatching {
            repository.updateTransaction(editable.copy(accountId = "usd"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot())
    }

    @Test
    fun editTransfer_keepsBothEntryIdsAndBalance() = runBlocking {
        database.financeDao().insertTransaction(
            TransactionEntity("transfer", "TRANSFER", "2026-08-01", "Transfer", "", 10, 10),
            listOf(
                TransactionEntryEntity("source-entry", "transfer", "account-1", null, -500, "EUR", true),
                TransactionEntryEntity("destination-entry", "transfer", "account-2", null, 500, "EUR", false),
            ),
        )
        val editable = repository.getTransactionForEdit("transfer")

        repository.updateTransaction(
            editable.copy(
                amount = Money(750, "EUR"),
                accountId = "account-2",
                destinationAccountId = "account-1",
                note = "Changed",
            ),
        )

        val snapshot = database.financeDao().exportSnapshot()
        val entries = snapshot.transactionEntries.associateBy { it.id }
        assertEquals(-750L, entries.getValue("source-entry").amountMinor)
        assertEquals("account-2", entries.getValue("source-entry").accountId)
        assertEquals(750L, entries.getValue("destination-entry").amountMinor)
        assertEquals("account-1", entries.getValue("destination-entry").accountId)
        assertEquals(0L, entries.values.sumOf { it.amountMinor })
        assertEquals("Changed", snapshot.transactions.single().note)
    }

    @Test
    fun editCrossCurrencyTransfer_preservesIndependentAmountsAndCurrencies() = runBlocking {
        database.financeDao().insertTransaction(
            TransactionEntity("transfer", "TRANSFER", "2026-08-01", "Transfer", "", 10, 10),
            listOf(
                TransactionEntryEntity("source-entry", "transfer", "account-1", null, -500, "EUR", true),
                TransactionEntryEntity("destination-entry", "transfer", "usd", null, 625, "USD", false),
            ),
        )
        val editable = repository.getTransactionForEdit("transfer")

        assertEquals(Money(500, "EUR"), editable.amount)
        assertEquals(Money(625, "USD"), editable.destinationAmount)
        repository.updateTransaction(
            editable.copy(
                amount = Money(800, "EUR"),
                destinationAmount = Money(1_000, "USD"),
                note = "Updated exchange",
            ),
        )

        val entries = database.financeDao().exportSnapshot().transactionEntries.associateBy { it.id }
        assertEquals(-800L, entries.getValue("source-entry").amountMinor)
        assertEquals("EUR", entries.getValue("source-entry").currencyCode)
        assertEquals(1_000L, entries.getValue("destination-entry").amountMinor)
        assertEquals("USD", entries.getValue("destination-entry").currencyCode)
    }

    @Test
    fun reconciliationAdjustment_cannotBeOpenedForEditing() = runBlocking {
        seedExpense()
        database.financeDao().insertReconciliationWithAdjustment(
            ReconciliationEntity("reconciliation", "account-1", "2026-08-01", -100, -100, 0, "transaction", 12),
            adjustment = null,
            entry = null,
        )

        val failure = runCatching { repository.getTransactionForEdit("transaction") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private suspend fun seedExpense() {
        database.financeDao().insertTransaction(
            TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "Original", 10, 10),
            TransactionEntryEntity("entry", "transaction", "account-1", "food", -1_000, "EUR", false),
        )
    }
}
