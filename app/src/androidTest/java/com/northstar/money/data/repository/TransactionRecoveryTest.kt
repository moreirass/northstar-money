package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionRecoveryTest {
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
    fun deleteAndRestoreTransaction_isDurableAndUpdatesEveryFinancialTotal() = runBlocking {
        val dao = database.financeDao()
        val repository = OfflineFinanceRepository(dao)
        dao.insertAccount(AccountEntity("account", "Current", "CHECKING", "EUR", 10_000, null, 1, 1))
        dao.insertCategory(CategoryEntity("category", "Food", "EXPENSE", 0))
        dao.insertTransaction(
            TransactionEntity("transaction", "EXPENSE", LocalDate.now().toString(), "Shop", "", 2, 2),
            TransactionEntryEntity("entry", "transaction", "account", "category", -2_500, "EUR", true),
        )

        repository.deleteTransaction("transaction")

        assertEquals(emptyList<Any>(), repository.observeTransactions().first())
        assertEquals(listOf("transaction"), repository.observeDeletedTransactions().first().map { it.id })
        assertEquals(10_000L, repository.observeAccounts().first().single().balance.minor)
        assertEquals(0L, repository.observeSummary().first().expensesThisMonth.minor)
        val deletedSnapshot = dao.exportSnapshot()
        assertNotNull(deletedSnapshot.transactions.single().deletedAt)
        assertEquals(listOf("entry"), deletedSnapshot.transactionEntries.map { it.id })

        repository.restoreTransaction("transaction")

        assertEquals(listOf("transaction"), repository.observeTransactions().first().map { it.id })
        assertEquals(emptyList<Any>(), repository.observeDeletedTransactions().first())
        assertEquals(7_500L, repository.observeAccounts().first().single().balance.minor)
        assertEquals(2_500L, repository.observeSummary().first().expensesThisMonth.minor)
        assertNull(dao.exportSnapshot().transactions.single().deletedAt)
    }
}
