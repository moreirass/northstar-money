package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.domain.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalManagementTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        repository = OfflineFinanceRepository(database.financeDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createGoal_normalizesOpeningSavedAmountAsAuditableContribution() = runBlocking {
        repository.createGoal("Emergency fund", Money(10_000), Money(2_500), "2027-01-31")

        val snapshot = database.financeDao().exportSnapshot()
        val storedGoal = snapshot.goals.single()
        val contribution = snapshot.goalContributions.single()
        assertEquals(0L, storedGoal.savedMinor)
        assertEquals(storedGoal.id, contribution.goalId)
        assertEquals(2_500L, contribution.amountMinor)
        assertEquals("Opening saved amount", contribution.note)
        assertEquals(2_500L, repository.observeGoals().first().single().saved.minor)
    }

    @Test
    fun editGoal_preservesIdentityCreationTimeCurrencyAndContributions() = runBlocking {
        repository.createGoal("Emergency fund", Money(10_000), Money(2_500), null)
        val before = database.financeDao().exportSnapshot()
        val storedGoal = before.goals.single()
        val contribution = before.goalContributions.single()
        val editable = repository.getGoalForEdit(storedGoal.id)

        repository.updateGoal(
            editable.copy(
                name = "House deposit",
                target = Money(20_000),
                targetLocalDate = "2027-06-30",
                status = "PAUSED",
            ),
        )

        val after = database.financeDao().exportSnapshot()
        val updated = after.goals.single()
        assertEquals(storedGoal.id, updated.id)
        assertEquals(storedGoal.createdAt, updated.createdAt)
        assertEquals("EUR", updated.currencyCode)
        assertEquals("House deposit", updated.name)
        assertEquals(20_000L, updated.targetMinor)
        assertEquals("2027-06-30", updated.targetLocalDate)
        assertEquals("PAUSED", updated.status)
        assertEquals(contribution, after.goalContributions.single())
        assertEquals(2_500L, repository.observeGoals().first().single().saved.minor)
    }

    @Test
    fun editDeleteAndRestoreContribution_recalculatesGoalWithoutLosingHistory() = runBlocking {
        repository.createGoal("Travel", Money(50_000), Money(0), null)
        val goalId = database.financeDao().exportSnapshot().goals.single().id
        repository.addGoalContribution(goalId, Money(1_000), "2026-08-01", "First deposit")
        val original = repository.observeGoalContributions().first().single()

        repository.updateGoalContribution(
            original.copy(amount = Money(1_500), localDate = "2026-08-02", note = "Updated deposit"),
        )

        val edited = repository.observeGoalContributions().first().single()
        assertEquals(original.id, edited.id)
        assertEquals(1_500L, edited.amount.minor)
        assertEquals("2026-08-02", edited.localDate)
        assertEquals("Updated deposit", edited.note)
        assertEquals(1_500L, repository.observeGoals().first().single().saved.minor)

        repository.deleteGoalContribution(original.id)
        assertTrue(repository.observeGoalContributions().first().isEmpty())
        assertEquals(0L, repository.observeGoals().first().single().saved.minor)
        assertEquals(original.id, repository.observeDeletedGoalContributions().first().single().id)
        assertNotNull(database.financeDao().exportSnapshot().goalContributions.single().deletedAt)

        repository.restoreGoalContribution(original.id)
        assertTrue(repository.observeDeletedGoalContributions().first().isEmpty())
        assertEquals(1_500L, repository.observeGoals().first().single().saved.minor)
        assertEquals(null, database.financeDao().exportSnapshot().goalContributions.single().deletedAt)
    }

    @Test
    fun invalidGoalOrContributionUpdate_leavesStoredDataUntouched() = runBlocking {
        repository.createGoal("Travel", Money(50_000), Money(1_000), null)
        val before = database.financeDao().exportSnapshot()
        val goal = repository.getGoalForEdit(before.goals.single().id)
        val contribution = repository.getGoalContributionForEdit(before.goalContributions.single().id)

        val goalFailure = runCatching {
            repository.updateGoal(goal.copy(target = Money(50_000, "USD")))
        }.exceptionOrNull()
        val contributionFailure = runCatching {
            repository.updateGoalContribution(contribution.copy(amount = Money(2_000, "USD")))
        }.exceptionOrNull()

        assertTrue(goalFailure is IllegalArgumentException)
        assertTrue(contributionFailure is IllegalArgumentException)
        assertEquals(before, database.financeDao().exportSnapshot())
    }
}
