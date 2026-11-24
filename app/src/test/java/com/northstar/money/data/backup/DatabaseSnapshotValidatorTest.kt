package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionExchangeRateEntity
import org.junit.Assert.assertEquals
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
            transactionEntries = validSnapshot().transactionEntries.map { it.entryWith(accountId = "missing") },
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsUnbalancedTransfer() {
        val invalid = validSnapshot().copy(
            transactions = listOf(validSnapshot().transactions.single().asTransfer()),
            transactionEntries = listOf(
                validSnapshot().transactionEntries.single().entryWith(categoryId = null, amountMinor = -100),
                validSnapshot().transactionEntries.single().entryWith(
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
    fun validate_acceptsCrossCurrencyTransferWithIndependentAmounts() {
        val base = validSnapshot()
        val transfer = base.copy(
            accounts = base.accounts +
                AccountEntity("usd", "USD account", "CHECKING", "USD", 0, null, 1, 1),
            transactions = listOf(base.transactions.single().asTransfer()),
            transactionEntries = listOf(
                base.transactionEntries.single().entryWith(categoryId = null, amountMinor = -100),
                base.transactionEntries.single().entryWith(
                    id = "usd-entry",
                    accountId = "usd",
                    categoryId = null,
                    amountMinor = 125,
                    currencyCode = "USD",
                ),
            ),
        )

        validator.validate(transfer)
    }

    @Test
    fun validate_rejectsEntryCurrencyThatDoesNotMatchAccount() {
        val invalid = validSnapshot().copy(
            transactionEntries = validSnapshot().transactionEntries.map { it.entryWith(currencyCode = "USD") },
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsInconsistentReconciliationAmounts() {
        val base = validSnapshot()
        val invalid = base.copy(
            reconciliations = listOf(
                com.northstar.money.core.database.ReconciliationEntity(
                    "reconciliation", "account", "2026-08-01", 100, 50, 40, null, 1,
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) { validator.validate(invalid) }
    }

    @Test
    fun validate_rejectsMergeIntoMissingOrArchivedCategory() {
        val missingTarget = validSnapshot().copy(
            categories = listOf(
                CategoryEntity("category", "Food", "EXPENSE", 0, 1, "missing"),
            ),
        )
        val archivedTarget = validSnapshot().copy(
            categories = listOf(
                CategoryEntity("category", "Food", "EXPENSE", 0, 1, "target"),
                CategoryEntity("target", "Living", "EXPENSE", 1, 2, null),
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

    @Test fun rejectsUnsupportedCurrencyAndMultipleExpenseEntries() {
        val base = validSnapshot()
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(base.copy(accounts = base.accounts.map { it.withDetails(it.name, it.type, "ZZZ", it.openingBalanceMinor, it.updatedAt) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(base.copy(transactionEntries = base.transactionEntries + base.transactionEntries.single().entryWith(id = "second")))
        }
    }

    @Test fun rejectsExpenseAssignedToAnIncomeCategory() {
        val base = validSnapshot()
        assertThrows(IllegalArgumentException::class.java) {
            val incomeCategories = base.categories.map {
                CategoryEntity(it.id, it.name, "INCOME", it.sortOrder, it.archivedAt, it.mergedIntoCategoryId)
            }
            validator.validate(base.copy(categories = incomeCategories))
        }
    }

    @Test fun rejectsInconsistentExchangeConversionAndWrongTransactionDate() {
        val base = validSnapshot()
        val rate = TransactionExchangeRateEntity(
            "rate", "transaction", "entry", "EUR", "USD", 2_000_000, -200, "2026-08-01", "Test", "AVAILABLE", 1)
        validator.validate(base.copy(transactionExchangeRates = listOf(rate)))
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(base.copy(transactionExchangeRates = listOf(rate.withConvertedAmount(-999))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(base.copy(transactionExchangeRates = listOf(rate.asPending(rate.transactionId, rate.baseCurrencyCode, "2026-08-02").withQuote(2_000_000, -200, "Test", 1))))
        }
    }

    @Test fun historicalQuotePreservesMicrosAndSignedMinorUnitsAcrossBackup() {
        val base = validSnapshot()
        val entry = base.transactionEntries.single().entryWith(amountMinor = -9_007_199_254_740_993L)
        val pending = TransactionExchangeRateEntity(
            "rate", entry.transactionId, entry.id, "EUR", "USD", null, null, "2026-08-01", "Test", "PENDING", null)
        val available = pending.withQuote(1_000_001L, -9_007_208_261_940_248L, "Test", 123L)
        assertEquals(TransactionExchangeRateEntity("rate", "transaction", "entry", "EUR", "USD",
            1_000_001L, -9_007_208_261_940_248L, "2026-08-01", "Test", "AVAILABLE", 123L), available)
        assertEquals(pending, available.asPending(entry.transactionId, "EUR", "2026-08-01"))
        val codec = FullBackupJsonCodec()
        for (rate in listOf(pending, available)) {
            val snapshot = base.copy(transactionEntries = listOf(entry), transactionExchangeRates = listOf(rate))
            val restored = codec.decode(codec.encode(snapshot, 10, 123L, "USD")).toSnapshot()
            validator.validate(restored)
            assertEquals(rate, restored.transactionExchangeRates.single())
        }
    }

    @Test fun rejectsTransferBetweenTheSameAccount() {
        val base = validSnapshot()
        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(base.copy(transactions = base.transactions.map { it.asTransfer() },
                transactionEntries = listOf(base.transactionEntries.single().entryWith(categoryId = null),
                    base.transactionEntries.single().entryWith(id = "destination", categoryId = null, amountMinor = 100))))
        }
    }

    private fun TransactionEntity.asTransfer() =
        TransactionEntity(id, "TRANSFER", localDate, payee, note, createdAt, updatedAt, deletedAt)

    private fun TransactionEntryEntity.entryWith(
        id: String = this.id,
        accountId: String = this.accountId,
        categoryId: String? = this.categoryId,
        amountMinor: Long = this.amountMinor,
        currencyCode: String = this.currencyCode,
    ) = TransactionEntryEntity(id, transactionId, accountId, categoryId, amountMinor, currencyCode, cleared)

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
