package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtManagementTest {
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
                AccountEntity("eur", "Card", "CREDIT", "EUR", 0, null, 1, 1),
                AccountEntity("usd", "US card", "CREDIT", "USD", 0, null, 1, 1),
            ),
        )
        dao.upsertDebt(DebtProfileEntity("debt", "eur", 350, 5_000, 15, 10))
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun editDebt_preservesIdentityAccountCurrencyAndCreationTime() = runBlocking {
        val editable = repository.getDebtForEdit("debt")

        repository.updateDebt(
            editable.copy(
                annualRateBasisPoints = 475,
                minimumPayment = Money(7_500, "EUR"),
                dueDay = 21,
            ),
        )

        val stored = database.financeDao().exportSnapshot().debtProfiles.single()
        assertEquals("debt", stored.id)
        assertEquals("eur", stored.accountId)
        assertEquals(10L, stored.createdAt)
        assertEquals(475, stored.annualRateBasisPoints)
        assertEquals(7_500L, stored.minimumPaymentMinor)
        assertEquals(21, stored.dueDay)
        assertEquals("EUR", repository.getDebtForEdit("debt").minimumPayment.currencyCode)
    }

    @Test
    fun invalidDebtEdit_leavesProfileUntouched() = runBlocking {
        val before = database.financeDao().exportSnapshot().debtProfiles
        val editable = repository.getDebtForEdit("debt")

        val currencyFailure = runCatching {
            repository.updateDebt(editable.copy(minimumPayment = Money(7_500, "USD")))
        }.exceptionOrNull()
        val accountFailure = runCatching {
            repository.updateDebt(editable.copy(accountId = "usd", minimumPayment = Money(7_500, "USD")))
        }.exceptionOrNull()

        assertTrue(currencyFailure is IllegalArgumentException)
        assertTrue(accountFailure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot().debtProfiles)
    }

    @Test
    fun createSecondDebtForSameAccount_doesNotReplaceExistingProfile() = runBlocking {
        val before = database.financeDao().exportSnapshot().debtProfiles.single()

        val failure = runCatching {
            repository.createDebt("eur", 900, Money(10_000, "EUR"), 28)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot().debtProfiles.single())
    }
}
