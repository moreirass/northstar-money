package com.northstar.money.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.data.repository.OfflineFinanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullBackupRepositoryTest {
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
    fun createFullBackup_includesAllTablesAndHiddenRecords() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("account", "Archived", "CHECKING", "EUR", 100, 99, 1, 2))
        dao.insertCategory(CategoryEntity("category", "Archived", "EXPENSE", 1, 99))
        dao.insertTransaction(
            TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "", 3, 4),
            TransactionEntryEntity("entry", "transaction", "account", "category", -25, "EUR", false),
        )
        dao.insertReconciliationWithAdjustment(
            ReconciliationEntity("reconciliation", "account", "2026-08-01", 75, 75, 0, null, 5),
            adjustment = null,
            entry = null,
        )
        dao.upsertBudget(BudgetAllocationEntity("budget", "2026-08-01", "category", 200))
        dao.insertGoal(GoalEntity("goal", "Reserve", 1000, 100, "EUR", null, "PAUSED", 6))
        dao.insertRecurring(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "EUR", "account", "category",
                "MONTHLY", 1, "2026-09-01", false, 7,
            ),
        )
        dao.upsertDebt(DebtProfileEntity("debt", "account", 350, 50, 15, 8))

        val encoded = OfflineFinanceRepository(dao).createFullBackup()
        val snapshot = FullBackupJsonCodec().decode(encoded).toSnapshot()

        assertEquals(listOf("account"), snapshot.accounts.map { it.id })
        assertEquals(listOf("category"), snapshot.categories.map { it.id })
        assertEquals(listOf("transaction"), snapshot.transactions.map { it.id })
        assertEquals(listOf("entry"), snapshot.transactionEntries.map { it.id })
        assertEquals(listOf("reconciliation"), snapshot.reconciliations.map { it.id })
        assertEquals(listOf("budget"), snapshot.budgetAllocations.map { it.id })
        assertEquals(listOf("goal"), snapshot.goals.map { it.id })
        assertEquals(listOf("recurring"), snapshot.recurringSchedules.map { it.id })
        assertEquals(listOf("debt"), snapshot.debtProfiles.map { it.id })
        assertEquals(99L, snapshot.accounts.single().archivedAt)
        assertEquals(99L, snapshot.categories.single().archivedAt)
        assertEquals(false, snapshot.recurringSchedules.single().active)
    }
}
