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
import com.northstar.money.domain.model.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetRolloverTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository
    private val currentMonth = LocalDate.now().withDayOfMonth(1)
    private val previousMonth = currentMonth.minusMonths(1)

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("account", "Main", "CHECKING", "EUR", 0, null, 1, 1))
        dao.insertCategories(
            listOf(
                CategoryEntity("food", "Food", "EXPENSE", 0),
                CategoryEntity("transport", "Transport", "EXPENSE", 1),
                CategoryEntity("health", "Health", "EXPENSE", 2),
                CategoryEntity("source", "Old food", "EXPENSE", 3),
                CategoryEntity("target", "Living", "EXPENSE", 4),
            ),
        )
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun observeBudgets_rollsSurplusAndDeficitIntoCurrentAvailableAmount() = runBlocking {
        val dao = database.financeDao()
        dao.upsertBudget(BudgetAllocationEntity("food-previous", previousMonth.toString(), "food", 10_000))
        dao.upsertBudget(BudgetAllocationEntity("food-current", currentMonth.toString(), "food", 20_000))
        dao.upsertBudget(BudgetAllocationEntity("transport-previous", previousMonth.toString(), "transport", 10_000))
        dao.upsertBudget(BudgetAllocationEntity("health-current", currentMonth.toString(), "health", 5_000))
        insertExpense("food-previous", "food", -4_000, previousMonth.plusDays(5), deletedAt = null)
        insertExpense("food-deleted", "food", -1_000, previousMonth.plusDays(6), deletedAt = 9)
        insertExpense("food-current", "food", -5_000, currentMonth.plusDays(5), deletedAt = null)
        insertExpense("food-future", "food", -9_000, currentMonth.plusMonths(1), deletedAt = null)
        insertExpense("transport-previous", "transport", -12_000, previousMonth.plusDays(5), deletedAt = null)
        insertExpense("health-before-budget", "health", -3_000, previousMonth.plusDays(5), deletedAt = null)

        val budgets = repository.observeBudgets().first().associateBy { it.categoryId }

        budgets.getValue("food").let {
            assertEquals(20_000L, it.allocated.minor)
            assertEquals(6_000L, it.rollover.minor)
            assertEquals(26_000L, it.planned.minor)
            assertEquals(5_000L, it.spent.minor)
        }
        budgets.getValue("transport").let {
            assertEquals(0L, it.allocated.minor)
            assertEquals(-2_000L, it.rollover.minor)
            assertEquals(-2_000L, it.planned.minor)
        }
        budgets.getValue("health").let {
            assertEquals(5_000L, it.allocated.minor)
            assertEquals(0L, it.rollover.minor)
            assertEquals(5_000L, it.planned.minor)
        }

        repository.setBudget("food", Money(30_000))
        val updated = repository.observeBudgets().first().single { it.categoryId == "food" }
        assertEquals(30_000L, updated.allocated.minor)
        assertEquals(6_000L, updated.rollover.minor)
        assertEquals(36_000L, updated.planned.minor)
    }

    @Test
    fun categoryMerge_combinesHistoricalRolloverButNotCurrentAllocations() = runBlocking {
        val dao = database.financeDao()
        dao.upsertBudget(BudgetAllocationEntity("source-previous", previousMonth.toString(), "source", 5_000))
        dao.upsertBudget(BudgetAllocationEntity("target-previous", previousMonth.toString(), "target", 10_000))
        dao.upsertBudget(BudgetAllocationEntity("source-current", currentMonth.toString(), "source", 7_000))
        dao.upsertBudget(BudgetAllocationEntity("target-current", currentMonth.toString(), "target", 20_000))
        insertExpense("source-spend", "source", -3_000, previousMonth.plusDays(5), deletedAt = null)
        insertExpense("target-spend", "target", -4_000, previousMonth.plusDays(5), deletedAt = null)

        repository.mergeCategory("source", "target")

        val merged = repository.observeBudgets().first().single { it.categoryId == "target" }
        assertEquals(20_000L, merged.allocated.minor)
        assertEquals(8_000L, merged.rollover.minor)
        assertEquals(28_000L, merged.planned.minor)

        repository.undoCategoryMerge("source")
        val split = repository.observeBudgets().first().associateBy { it.categoryId }
        assertEquals(2_000L, split.getValue("source").rollover.minor)
        assertEquals(6_000L, split.getValue("target").rollover.minor)
        assertEquals(7_000L, split.getValue("source").allocated.minor)
    }

    private suspend fun insertExpense(
        id: String,
        categoryId: String,
        amountMinor: Long,
        date: LocalDate,
        deletedAt: Long?,
    ) {
        database.financeDao().insertTransaction(
            TransactionEntity(id, "EXPENSE", date.toString(), id, "", 1, 1, deletedAt),
            TransactionEntryEntity("entry-$id", id, "account", categoryId, amountMinor, "EUR", false),
        )
    }
}
