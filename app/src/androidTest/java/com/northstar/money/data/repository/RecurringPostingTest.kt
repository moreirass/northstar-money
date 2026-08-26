package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.RecurringScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurringPostingTest {
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
                AccountEntity("archived", "Old", "CHECKING", "EUR", 0, 2, 1, 2),
            ),
        )
        dao.insertCategories(
            listOf(
                CategoryEntity("expense", "Bills", "EXPENSE", 0),
                CategoryEntity("expense-target", "Living", "EXPENSE", 1),
                CategoryEntity("income", "Salary", "INCOME", 0),
                CategoryEntity("archived-category", "Old bills", "EXPENSE", 2, archivedAt = 2),
            ),
        )
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun postDueRecurringOccurrences_catchesUpWithCorrectSignsAndIsIdempotent() = runBlocking {
        val dao = database.financeDao()
        dao.insertRecurring(schedule("monthly", "Rent", "EXPENSE", 50_000, "expense", "MONTHLY", 1, "2026-06-01"))
        dao.insertRecurring(schedule("biweekly", "Salary", "INCOME", 100_000, "income", "WEEKLY", 2, "2026-08-01"))

        val posted = repository.postDueRecurringOccurrences("2026-08-26")

        assertEquals(5, posted)
        val firstSnapshot = dao.exportSnapshot()
        assertEquals(5, firstSnapshot.transactions.size)
        assertEquals(
            listOf("2026-06-01", "2026-07-01", "2026-08-01"),
            firstSnapshot.transactions.filter { it.kind == "EXPENSE" }.map { it.localDate }.sorted(),
        )
        assertEquals(
            listOf("2026-08-01", "2026-08-15"),
            firstSnapshot.transactions.filter { it.kind == "INCOME" }.map { it.localDate }.sorted(),
        )
        assertTrue(firstSnapshot.transactionEntries.filter { it.categoryId == "expense" }.all { it.amountMinor == -50_000L })
        assertTrue(firstSnapshot.transactionEntries.filter { it.categoryId == "income" }.all { it.amountMinor == 100_000L })
        assertTrue(firstSnapshot.transactionEntries.none { it.cleared })
        assertEquals(
            "2026-09-01",
            firstSnapshot.recurringSchedules.single { it.id == "monthly" }.nextLocalDate,
        )
        assertEquals(
            "2026-08-29",
            firstSnapshot.recurringSchedules.single { it.id == "biweekly" }.nextLocalDate,
        )

        assertEquals(0, repository.postDueRecurringOccurrences("2026-08-26"))
        assertEquals(firstSnapshot, dao.exportSnapshot())
    }

    @Test
    fun postDueRecurringOccurrences_skipsPausedDeletedAndUnavailableParents() = runBlocking {
        val dao = database.financeDao()
        dao.insertRecurring(schedule("paused", "Paused", "EXPENSE", 100, "expense", active = false))
        dao.insertRecurring(schedule("deleted", "Deleted", "EXPENSE", 100, "expense", deletedAt = 2))
        dao.insertRecurring(schedule("old-account", "Old account", "EXPENSE", 100, "expense", accountId = "archived"))
        dao.insertRecurring(schedule("old-category", "Old category", "EXPENSE", 100, "archived-category"))
        val before = dao.exportSnapshot()

        assertEquals(0, repository.postDueRecurringOccurrences("2026-08-26"))

        val after = dao.exportSnapshot()
        assertTrue(after.transactions.isEmpty())
        assertTrue(after.transactionEntries.isEmpty())
        assertEquals(before.recurringSchedules, after.recurringSchedules)
    }

    @Test
    fun postDueRecurringOccurrences_resolvesMergedCategoryAndUpdatesSchedule() = runBlocking {
        val dao = database.financeDao()
        dao.insertRecurring(schedule("merged", "Rent", "EXPENSE", 100, "expense"))
        repository.mergeCategory("expense", "expense-target")

        assertEquals(1, repository.postDueRecurringOccurrences("2026-08-26"))

        val snapshot = dao.exportSnapshot()
        assertEquals("expense-target", snapshot.transactionEntries.single().categoryId)
        assertEquals("expense-target", snapshot.recurringSchedules.single().categoryId)
    }

    @Test
    fun concurrentPosting_runsStillCreateEachOccurrenceOnce() = runBlocking {
        val dao = database.financeDao()
        dao.insertRecurring(schedule("monthly", "Rent", "EXPENSE", 100, "expense", nextDate = "2026-06-01"))

        val totalPosted = coroutineScope {
            listOf(
                async(Dispatchers.IO) { repository.postDueRecurringOccurrences("2026-08-26") },
                async(Dispatchers.IO) { repository.postDueRecurringOccurrences("2026-08-26") },
            ).awaitAll().sum()
        }

        assertEquals(3, totalPosted)
        assertEquals(3, dao.exportSnapshot().transactions.size)
        assertEquals("2026-09-01", dao.exportSnapshot().recurringSchedules.single().nextLocalDate)
    }

    private fun schedule(
        id: String,
        name: String,
        kind: String,
        amountMinor: Long,
        categoryId: String,
        frequency: String = "MONTHLY",
        intervalCount: Int = 1,
        nextDate: String = "2026-08-26",
        active: Boolean = true,
        deletedAt: Long? = null,
        accountId: String = "account",
    ) = RecurringScheduleEntity(
        id, name, kind, amountMinor, "EUR", accountId, categoryId,
        frequency, intervalCount, nextDate, active, 1, deletedAt,
    )
}
