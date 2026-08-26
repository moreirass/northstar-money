package com.northstar.money.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.DatabaseSnapshot
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
import org.junit.Assert.assertTrue
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
            TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "", 3, 4, deletedAt = 98),
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
        assertEquals(98L, snapshot.transactions.single().deletedAt)
    }

    @Test
    fun passwordProtectedBackup_roundTripsWithAndroidCryptoProvider() = runBlocking {
        val plainText = OfflineFinanceRepository(database.financeDao()).createFullBackup()
        val password = "correct horse battery staple".toCharArray()
        try {
            val encrypted = SecureBackupCodec().encrypt(plainText, password)

            assertEquals(plainText, SecureBackupCodec().decrypt(encrypted, password))
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun restoreFullBackup_replacesEveryTableAndUndoRestoresPreviousSnapshot() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("original", "Original", "CHECKING", "EUR", 42, null, 1, 1))
        dao.insertCategory(CategoryEntity("original-category", "Original", "EXPENSE", 0))
        val original = dao.exportSnapshot()
        val target = completeRestoreSnapshot()
        val recoveryStore = MemoryRestoreRecoveryStore()
        val repository = OfflineFinanceRepository(dao, restoreRecoveryStore = recoveryStore)
        val password = "correct horse battery staple".toCharArray()
        try {
            repository.restoreFullBackup(
                FullBackupJsonCodec().encode(target, NorthstarDatabase.VERSION),
                password,
            )

            assertEquals(target, dao.exportSnapshot())
            assertTrue(requireNotNull(recoveryStore.payload).isNotEmpty())

            repository.undoLastFullRestore(password)

            assertEquals(original, dao.exportSnapshot())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun replaceWithSnapshot_constraintFailureRollsBackEveryDeletion() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("original", "Original", "CHECKING", "EUR", 42, null, 1, 1))
        val original = dao.exportSnapshot()
        val invalid = emptySnapshot().copy(
            accounts = listOf(AccountEntity("replacement", "Replacement", "CHECKING", "EUR", 0, null, 2, 2)),
            categories = listOf(
                CategoryEntity("category-1", "Duplicate", "EXPENSE", 0),
                CategoryEntity("category-2", "Duplicate", "EXPENSE", 1),
            ),
        )

        val failure = runCatching { dao.replaceWithSnapshot(invalid) }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(original, dao.exportSnapshot())
    }

    @Test
    fun restoreFullBackup_recoveryWriteFailureLeavesDatabaseUntouched() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("original", "Original", "CHECKING", "EUR", 42, null, 1, 1))
        val original = dao.exportSnapshot()
        val repository = OfflineFinanceRepository(
            dao = dao,
            restoreRecoveryStore = object : RestoreRecoveryStore {
                override fun save(payload: ByteArray) = throw IllegalStateException("disk full")
                override fun load(): ByteArray? = null
            },
        )
        val password = "correct horse battery staple".toCharArray()
        try {
            val failure = runCatching {
                repository.restoreFullBackup(
                    FullBackupJsonCodec().encode(completeRestoreSnapshot(), NorthstarDatabase.VERSION),
                    password,
                )
            }.exceptionOrNull()

            assertTrue(failure != null)
            assertEquals(original, dao.exportSnapshot())
        } finally {
            password.fill('\u0000')
        }
    }

    private fun completeRestoreSnapshot() = DatabaseSnapshot(
        accounts = listOf(AccountEntity("restored-account", "Restored", "CHECKING", "EUR", 100, 99, 10, 11)),
        categories = listOf(CategoryEntity("restored-category", "Restored", "EXPENSE", 0, 99)),
        transactions = listOf(TransactionEntity("restored-transaction", "EXPENSE", "2026-08-01", "Shop", "Note", 12, 13)),
        transactionEntries = listOf(
            TransactionEntryEntity(
                "restored-entry", "restored-transaction", "restored-account", "restored-category", -25, "EUR", false,
            ),
        ),
        reconciliations = listOf(
            ReconciliationEntity("restored-reconciliation", "restored-account", "2026-08-01", 75, 75, 0, null, 14),
        ),
        budgetAllocations = listOf(BudgetAllocationEntity("restored-budget", "2026-08-01", "restored-category", 200)),
        goals = listOf(GoalEntity("restored-goal", "Reserve", 1000, 100, "EUR", null, "PAUSED", 15)),
        recurringSchedules = listOf(
            RecurringScheduleEntity(
                "restored-recurring", "Rent", "EXPENSE", 500, "EUR", "restored-account", "restored-category",
                "MONTHLY", 1, "2026-09-01", false, 16,
            ),
        ),
        debtProfiles = listOf(DebtProfileEntity("restored-debt", "restored-account", 350, 50, 15, 17)),
    )

    private fun emptySnapshot() = DatabaseSnapshot(
        accounts = emptyList(),
        categories = emptyList(),
        transactions = emptyList(),
        transactionEntries = emptyList(),
        reconciliations = emptyList(),
        budgetAllocations = emptyList(),
        goals = emptyList(),
        recurringSchedules = emptyList(),
        debtProfiles = emptyList(),
    )

    private class MemoryRestoreRecoveryStore : RestoreRecoveryStore {
        var payload: ByteArray? = null

        override fun save(payload: ByteArray) {
            this.payload = payload.copyOf()
        }

        override fun load(): ByteArray? = payload?.copyOf()
    }
}
