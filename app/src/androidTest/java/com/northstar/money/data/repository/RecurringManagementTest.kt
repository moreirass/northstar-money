package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.RecurringScheduleEntity
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
class RecurringManagementTest {
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
                AccountEntity("account", "Main", "CHECKING", "EUR", 0, null, 1, 1),
                AccountEntity("savings", "Savings", "SAVINGS", "EUR", 0, null, 1, 1),
                AccountEntity("usd", "USD", "CHECKING", "USD", 0, null, 1, 1),
            ),
        )
        dao.insertCategories(
            listOf(
                CategoryEntity("food", "Food", "EXPENSE", 0),
                CategoryEntity("living", "Living", "EXPENSE", 1),
                CategoryEntity("salary", "Salary", "INCOME", 0),
            ),
        )
        dao.insertRecurring(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "EUR", "account", "food",
                "MONTHLY", 1, "2026-09-01", true, 10,
            ),
        )
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun editRecurring_preservesIdentityAndCreationTime() = runBlocking {
        val editable = repository.getRecurringForEdit("recurring")

        repository.updateRecurring(
            editable.copy(
                name = "Utilities",
                amount = Money(750, "EUR"),
                accountId = "savings",
                categoryId = "living",
                frequency = "WEEKLY",
                intervalCount = 2,
                nextLocalDate = "2026-09-10",
            ),
        )

        val stored = database.financeDao().exportSnapshot().recurringSchedules.single()
        assertEquals("recurring", stored.id)
        assertEquals(10L, stored.createdAt)
        assertEquals("Utilities", stored.name)
        assertEquals(750L, stored.amountMinor)
        assertEquals("savings", stored.accountId)
        assertEquals("living", stored.categoryId)
        assertEquals("WEEKLY", stored.frequency)
        assertEquals(2, stored.intervalCount)
        assertTrue(stored.active)
    }

    @Test
    fun editRecurring_invalidCurrencyLeavesScheduleUntouched() = runBlocking {
        val before = database.financeDao().exportSnapshot().recurringSchedules
        val editable = repository.getRecurringForEdit("recurring")

        val failure = runCatching {
            repository.updateRecurring(editable.copy(accountId = "usd"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot().recurringSchedules)
    }

    @Test
    fun pauseResumeDeleteAndRestore_keepRecoveryPath() = runBlocking {
        repository.pauseRecurring("recurring")
        assertTrue(repository.observeRecurring().first().isEmpty())
        assertEquals("recurring", repository.observePausedRecurring().first().single().id)

        repository.resumeRecurring("recurring")
        assertEquals("recurring", repository.observeRecurring().first().single().id)
        assertTrue(repository.observePausedRecurring().first().isEmpty())

        repository.deleteRecurring("recurring")
        assertTrue(repository.observeRecurring().first().isEmpty())
        assertEquals("recurring", repository.observeDeletedRecurring().first().single().id)
        assertTrue(database.financeDao().exportSnapshot().recurringSchedules.single().deletedAt != null)

        repository.restoreRecurring("recurring")
        assertTrue(repository.observeDeletedRecurring().first().isEmpty())
        assertEquals("recurring", repository.observePausedRecurring().first().single().id)
        val restored = database.financeDao().exportSnapshot().recurringSchedules.single()
        assertEquals(null, restored.deletedAt)
        assertEquals(false, restored.active)
    }

    @Test
    fun mergedCategory_isResolvedBeforeEditAndResume() = runBlocking {
        repository.mergeCategory("food", "living")

        assertEquals("living", repository.getRecurringForEdit("recurring").categoryId)
        repository.pauseRecurring("recurring")
        repository.resumeRecurring("recurring")

        assertEquals("living", database.financeDao().exportSnapshot().recurringSchedules.single().categoryId)
    }
}
