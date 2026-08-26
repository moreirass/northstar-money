package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryManagementTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        database.financeDao().insertAccounts(
            listOf(AccountEntity("account", "Main", "CHECKING", "EUR", 0, null, 1, 1)),
        )
        database.financeDao().insertCategories(
            listOf(
                CategoryEntity("source", "Food", "EXPENSE", 0),
                CategoryEntity("target", "Living", "EXPENSE", 1),
                CategoryEntity("income", "Salary", "INCOME", 0),
            ),
        )
        repository = OfflineFinanceRepository(database.financeDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun renameArchiveAndRestore_areRecoverable() = runBlocking {
        repository.renameCategory("source", "Groceries")
        assertEquals("Groceries", repository.observeCategories().first().first { it.id == "source" }.name)

        repository.archiveCategory("source")
        assertTrue(repository.observeCategories().first().none { it.id == "source" })
        val archived = repository.observeArchivedCategories().first().single()
        assertEquals("Groceries", archived.name)
        assertNull(archived.mergedIntoCategoryId)

        repository.restoreCategory("source")
        assertTrue(repository.observeArchivedCategories().first().isEmpty())
        assertEquals("Groceries", repository.observeCategories().first().first { it.id == "source" }.name)
    }

    @Test
    fun duplicateRename_failsWithoutChangingCategories() = runBlocking {
        val before = database.financeDao().exportSnapshot().categories

        val failure = runCatching { repository.renameCategory("source", "Living") }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(before, database.financeDao().exportSnapshot().categories)
    }

    @Test
    fun merge_isVirtualAndUndoRestoresHistoricalCategoryAndBudget() = runBlocking {
        database.financeDao().insertTransaction(
            TransactionEntity("transaction", "EXPENSE", "2026-08-10", "Market", "", 2, 2),
            TransactionEntryEntity("entry", "transaction", "account", "source", -1_000, "EUR", true),
        )
        database.financeDao().upsertBudget(BudgetAllocationEntity("source-budget", "2026-08-01", "source", 1_500))
        database.financeDao().upsertBudget(BudgetAllocationEntity("target-budget", "2026-08-01", "target", 2_000))

        repository.mergeCategory("source", "target")

        assertTrue(repository.observeCategories().first().none { it.id == "source" })
        val archived = repository.observeArchivedCategories().first().single()
        assertEquals("target", archived.mergedIntoCategoryId)
        assertEquals("Living", archived.mergedIntoCategoryName)
        assertEquals("Living", repository.observeTransactions().first().single().categoryName)
        val mergedBudget = repository.observeBudgets().first().single { it.categoryId == "target" }
        assertEquals(1_000L, mergedBudget.spent.minor)
        assertEquals(2_000L, mergedBudget.planned.minor)
        assertEquals("target", database.financeDao().exportSnapshot().categories.first { it.id == "source" }.mergedIntoCategoryId)

        repository.undoCategoryMerge("source")

        assertEquals("Food", repository.observeTransactions().first().single().categoryName)
        val budgets = repository.observeBudgets().first().associateBy { it.categoryId }
        assertEquals(1_000L, budgets.getValue("source").spent.minor)
        assertEquals(1_500L, budgets.getValue("source").planned.minor)
        assertEquals(0L, budgets.getValue("target").spent.minor)
        assertTrue(repository.observeArchivedCategories().first().isEmpty())
    }

    @Test
    fun mergeDifferentKinds_failsWithoutArchivingSource() = runBlocking {
        val before = database.financeDao().exportSnapshot().categories

        val failure = runCatching { repository.mergeCategory("source", "income") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot().categories)
    }
}
