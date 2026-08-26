package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
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
