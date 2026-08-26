package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBackupJsonCodecTest {
    private val codec = FullBackupJsonCodec()

    @Test
    fun encodeAndDecode_preservesEveryDatabaseTable() {
        val snapshot = completeSnapshot()

        val encoded = codec.encode(snapshot, databaseVersion = 6, createdAtEpochMillis = 1234)
        val decoded = codec.decode(encoded)

        assertEquals(FullBackupJsonCodec.FORMAT, decoded.format)
        assertEquals(FullBackupJsonCodec.FORMAT_VERSION, decoded.formatVersion)
        assertEquals(6, decoded.databaseVersion)
        assertEquals(1234L, decoded.createdAtEpochMillis)
        assertEquals(snapshot, decoded.toSnapshot())
    }

    @Test
    fun decode_rejectsForeignBackupFormat() {
        val encoded = codec.encode(completeSnapshot(), databaseVersion = 4, createdAtEpochMillis = 1234)
            .replace(FullBackupJsonCodec.FORMAT, "some-other-backup")

        assertThrows(IllegalArgumentException::class.java) { codec.decode(encoded) }
    }

    @Test
    fun decode_acceptsBackupCreatedBeforeCategoryMerges() {
        val legacy = codec.encode(
            completeSnapshot().copy(goalContributions = emptyList()),
            databaseVersion = 5,
            createdAtEpochMillis = 1234,
        )
            .replace(",\"mergedIntoCategoryId\":\"target\"", "")
            .replace(",\"mergedIntoCategoryId\":null", "")
            .replace(",\"deletedAt\":null", "")
            .replace(",\"goalContributions\":[]", "")

        val decoded = codec.decode(legacy)

        assertTrue(decoded.categories.all { it.mergedIntoCategoryId == null })
        assertTrue(decoded.recurringSchedules.all { it.deletedAt == null })
        assertTrue(decoded.goalContributions.isEmpty())
    }

    private fun completeSnapshot() = DatabaseSnapshot(
        accounts = listOf(AccountEntity("account", "Account", "CHECKING", "EUR", 100, 9, 1, 2)),
        categories = listOf(
            CategoryEntity("category", "Food", "EXPENSE", 1, 9, "target"),
            CategoryEntity("target", "Living", "EXPENSE", 2),
        ),
        transactions = listOf(TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "Note", 3, 4)),
        transactionEntries = listOf(
            TransactionEntryEntity("entry", "transaction", "account", "category", -25, "EUR", false),
        ),
        reconciliations = listOf(
            ReconciliationEntity("reconciliation", "account", "2026-08-01", 75, 75, 0, null, 5),
        ),
        budgetAllocations = listOf(BudgetAllocationEntity("budget", "2026-08-01", "category", 200)),
        goals = listOf(GoalEntity("goal", "Reserve", 1000, 0, "EUR", null, "PAUSED", 6)),
        goalContributions = listOf(
            GoalContributionEntity("contribution", "goal", 100, "2026-08-01", "Opening", 6, 7, 9),
        ),
        recurringSchedules = listOf(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "EUR", "account", "category",
                "MONTHLY", 1, "2026-09-01", false, 7,
            ),
        ),
        debtProfiles = listOf(DebtProfileEntity("debt", "account", 350, 50, 15, 8)),
    )
}
