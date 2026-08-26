package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import org.junit.Assert.assertThrows
import org.junit.Test

class DatabaseSnapshotValidatorTest {
    private val validator = DatabaseSnapshotValidator()

    @Test
    fun validate_acceptsConsistentSnapshot() {
        validator.validate(validSnapshot())
    }

    @Test
    fun validate_rejectsMissingForeignKeyBeforeDatabaseMutation() {
        val invalid = validSnapshot().copy(
            transactionEntries = validSnapshot().transactionEntries.map { it.copy(accountId = "missing") },
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsUnbalancedTransfer() {
        val invalid = validSnapshot().copy(
            transactions = listOf(validSnapshot().transactions.single().copy(kind = "TRANSFER")),
            transactionEntries = listOf(
                validSnapshot().transactionEntries.single().copy(categoryId = null, amountMinor = -100),
                validSnapshot().transactionEntries.single().copy(
                    id = "entry-2",
                    accountId = "account-2",
                    categoryId = null,
                    amountMinor = 90,
                ),
            ),
            accounts = validSnapshot().accounts +
                AccountEntity("account-2", "Savings", "SAVINGS", "EUR", 0, null, 1, 1),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsEntryCurrencyThatDoesNotMatchAccount() {
        val invalid = validSnapshot().copy(
            transactionEntries = validSnapshot().transactionEntries.map { it.copy(currencyCode = "USD") },
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsMergeIntoMissingOrArchivedCategory() {
        val missingTarget = validSnapshot().copy(
            categories = listOf(
                CategoryEntity("category", "Food", "EXPENSE", 0, archivedAt = 1, mergedIntoCategoryId = "missing"),
            ),
        )
        val archivedTarget = validSnapshot().copy(
            categories = listOf(
                CategoryEntity("category", "Food", "EXPENSE", 0, archivedAt = 1, mergedIntoCategoryId = "target"),
                CategoryEntity("target", "Living", "EXPENSE", 1, archivedAt = 2),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(missingTarget) }
        assertThrows(IllegalArgumentException::class.java) { validator.validate(archivedTarget) }
    }

    @Test
    fun validate_rejectsInvalidRecurringDeletionTimestamp() {
        val base = validSnapshot()
        val invalid = base.copy(
            recurringSchedules = listOf(
                com.northstar.money.core.database.RecurringScheduleEntity(
                    "recurring", "Rent", "EXPENSE", 100, "EUR", "account", "category",
                    "MONTHLY", 1, "2026-09-01", true, 1, deletedAt = -1,
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsContributionWithMissingGoalOrInvalidAmount() {
        val base = validSnapshot().copy(
            goals = listOf(GoalEntity("goal", "Reserve", 1_000, 0, "EUR", null, "ACTIVE", 1)),
        )
        val missingGoal = base.copy(
            goalContributions = listOf(
                GoalContributionEntity("contribution", "missing", 100, "2026-08-01", "", 1, 1),
            ),
        )
        val invalidAmount = base.copy(
            goalContributions = listOf(
                GoalContributionEntity("contribution", "goal", 0, "2026-08-01", "", 1, 1),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(missingGoal) }
        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalidAmount) }
    }

    @Test
    fun validate_rejectsUnnormalizedGoalSavedAmount() {
        val invalid = validSnapshot().copy(
            goals = listOf(GoalEntity("goal", "Reserve", 1_000, 100, "EUR", null, "ACTIVE", 1)),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    private fun validSnapshot() = DatabaseSnapshot(
        accounts = listOf(AccountEntity("account", "Current", "CHECKING", "EUR", 0, null, 1, 1)),
        categories = listOf(CategoryEntity("category", "Food", "EXPENSE", 0)),
        transactions = listOf(TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "", 1, 1)),
        transactionEntries = listOf(
            TransactionEntryEntity("entry", "transaction", "account", "category", -100, "EUR", true),
        ),
        reconciliations = emptyList(),
        budgetAllocations = emptyList(),
        goals = emptyList(),
        recurringSchedules = emptyList(),
        debtProfiles = emptyList(),
    )
}
