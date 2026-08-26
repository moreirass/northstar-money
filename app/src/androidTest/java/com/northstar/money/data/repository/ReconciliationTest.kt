package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconciliationTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository
    private val statementDate = LocalDate.now().minusDays(5)

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        database.financeDao().insertAccount(
            AccountEntity("account", "Current", "CHECKING", "EUR", 10_000, null, 1, 1),
        )
        database.financeDao().insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))
        repository = OfflineFinanceRepository(database.financeDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun reconcile_comparesOnlyClearedEntriesThroughStatementDate() = runBlocking {
        insertExpense("cleared", -1_000, statementDate.minusDays(1), cleared = true)
        insertExpense("uncleared", -500, statementDate.minusDays(1), cleared = false)
        insertExpense("later", -200, statementDate.plusDays(1), cleared = true)

        repository.reconcile("account", statementDate.toString(), Money(9_000), createAdjustment = false)

        val snapshot = database.financeDao().exportSnapshot()
        val reconciliation = snapshot.reconciliations.single()
        assertEquals(9_000L, reconciliation.calculatedBalanceMinor)
        assertEquals(0L, reconciliation.differenceMinor)
        assertEquals(null, reconciliation.adjustmentTransactionId)
        assertEquals(false, snapshot.transactionEntries.single { it.transactionId == "uncleared" }.cleared)
        assertEquals(8_300L, repository.observeAccounts().first().single().balance.minor)
        assertEquals(8_800L, repository.observeAccounts().first().single().clearedBalance.minor)
    }

    @Test
    fun reconcile_mismatchWithoutAdjustmentIsAtomicAndRejected() = runBlocking {
        insertExpense("cleared", -1_000, statementDate.minusDays(1), cleared = true)
        val before = database.financeDao().exportSnapshot()

        val failure = runCatching {
            repository.reconcile("account", statementDate.toString(), Money(8_500), createAdjustment = false)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot())
    }

    @Test
    fun reconcile_withAdjustmentCreatesClearedEntryOnStatementDate() = runBlocking {
        insertExpense("cleared", -1_000, statementDate.minusDays(1), cleared = true)

        repository.reconcile("account", statementDate.toString(), Money(8_500), createAdjustment = true)

        val snapshot = database.financeDao().exportSnapshot()
        val reconciliation = snapshot.reconciliations.single()
        val adjustment = snapshot.transactions.single { it.id == reconciliation.adjustmentTransactionId }
        val entry = snapshot.transactionEntries.single { it.transactionId == adjustment.id }
        assertEquals(statementDate.toString(), adjustment.localDate)
        assertEquals(-500L, reconciliation.differenceMinor)
        assertEquals(-500L, entry.amountMinor)
        assertTrue(entry.cleared)
        assertEquals(8_500L, repository.observeAccounts().first().single().clearedBalance.minor)

        val beforeProtectedChanges = database.financeDao().exportSnapshot()
        assertTrue(runCatching { repository.setTransactionCleared(adjustment.id, false) }.isFailure)
        assertTrue(runCatching { repository.deleteTransaction(adjustment.id) }.isFailure)
        assertEquals(beforeProtectedChanges, database.financeDao().exportSnapshot())
    }

    @Test
    fun setTransactionCleared_updatesEveryTransferEntryTogether() = runBlocking {
        val date = statementDate.minusDays(1).toString()
        database.financeDao().insertAccount(
            AccountEntity("savings", "Savings", "SAVINGS", "EUR", 0, null, 1, 1),
        )
        database.financeDao().insertTransaction(
            TransactionEntity("transfer", "TRANSFER", date, "Transfer", "", 1, 1),
            listOf(
                TransactionEntryEntity("source", "transfer", "account", null, -100, "EUR", false),
                TransactionEntryEntity("destination", "transfer", "savings", null, 100, "EUR", false),
            ),
        )

        repository.setTransactionCleared("transfer", true)

        assertTrue(database.financeDao().exportSnapshot().transactionEntries.all { it.cleared })
        assertTrue(repository.observeTransactions().first().single().cleared)
    }

    private suspend fun insertExpense(id: String, amountMinor: Long, date: LocalDate, cleared: Boolean) {
        database.financeDao().insertTransaction(
            TransactionEntity(id, "EXPENSE", date.toString(), id, "", 1, 1),
            TransactionEntryEntity("entry-$id", id, "account", "food", amountMinor, "EUR", cleared),
        )
    }
}
