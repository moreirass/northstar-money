package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountManagementTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("account", "Main", "CHECKING", "EUR", 1_000, null, 10, 10))
        dao.insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))
        dao.insertTransaction(
            TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "", 11, 11),
            TransactionEntryEntity("entry", "transaction", "account", "food", -200, "EUR", true),
        )
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun editAccount_preservesIdentityCurrencyAndHistory() = runBlocking {
        val editable = repository.getAccountForEdit("account")

        repository.updateAccount(
            editable.copy(
                name = "Household",
                type = AccountType.SAVINGS,
                openingBalance = Money(2_000, "EUR"),
            ),
        )

        val snapshot = database.financeDao().exportSnapshot()
        val account = snapshot.accounts.single()
        assertEquals("account", account.id)
        assertEquals(10L, account.createdAt)
        assertEquals("Household", account.name)
        assertEquals("SAVINGS", account.type)
        assertEquals("EUR", account.currencyCode)
        assertEquals(2_000L, account.openingBalanceMinor)
        assertEquals("entry", snapshot.transactionEntries.single().id)
        assertEquals(1_800L, repository.observeAccounts().first().single().balance.minor)
    }

    @Test
    fun editAccount_currencyChangeFailsWithoutMutation() = runBlocking {
        val before = database.financeDao().exportSnapshot()
        val editable = repository.getAccountForEdit("account")

        val failure = runCatching {
            repository.updateAccount(editable.copy(openingBalance = Money(1_000, "USD")))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot())
    }

    @Test
    fun configureInitialAccount_updatesOnlyUntouchedSeedAccount() = runBlocking {
        database.financeDao().insertAccount(
            AccountEntity("main-account", "Main account", "CHECKING", "EUR", 0, null, 20, 20),
        )

        repository.configureInitialAccount("Conta Principal", Money(123_456, "USD"))

        val configured = repository.getAccountForEdit("main-account")
        assertEquals("Conta Principal", configured.name)
        assertEquals("USD", configured.openingBalance.currencyCode)
        assertEquals(123_456L, configured.openingBalance.minor)
        assertTrue(
            runCatching {
                repository.updateAccount(configured.copy(openingBalance = Money(123_456, "EUR")))
            }.isFailure,
        )
    }

    @Test
    fun archiveAndRestore_hideAndRecoverDependentPlanningData() = runBlocking {
        val dao = database.financeDao()
        dao.insertRecurring(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "EUR", "account", "food",
                "MONTHLY", 1, "2026-09-01", true, 12,
            ),
        )
        dao.upsertDebt(DebtProfileEntity("debt", "account", 350, 50, 15, 12))

        repository.archiveAccount("account")

        assertTrue(repository.observeAccounts().first().isEmpty())
        assertEquals(800L, repository.observeArchivedAccounts().first().single().balance.minor)
        assertTrue(repository.observeRecurring().first().isEmpty())
        assertTrue(repository.observeDebts().first().isEmpty())
        assertEquals("Main", repository.observeTransactions().first().single().accountName)

        repository.restoreAccount("account")

        assertTrue(repository.observeArchivedAccounts().first().isEmpty())
        assertEquals(800L, repository.observeAccounts().first().single().balance.minor)
        assertEquals("recurring", repository.observeRecurring().first().single().id)
        assertEquals("debt", repository.observeDebts().first().single().id)
    }
}
