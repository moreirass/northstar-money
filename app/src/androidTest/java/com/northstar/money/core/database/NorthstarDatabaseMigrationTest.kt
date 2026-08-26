package com.northstar.money.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NorthstarDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NorthstarDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingDataAndCreatesReconciliations() {
        helper.createDatabase(DB_1_2, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(DB_1_2, 2, true, MIGRATION_1_2).apply {
            assertBaseDataPreserved()
            execSQL(
                """
                INSERT INTO reconciliations (
                    id, accountId, statementLocalDate, statementBalanceMinor,
                    calculatedBalanceMinor, differenceMinor, adjustmentTransactionId, completedAt
                ) VALUES ('reconciliation-1', 'account-1', '2026-08-01', 12500, 12500, 0, NULL, 2)
                """.trimIndent(),
            )
            assertRowCount("reconciliations", 1)
            close()
        }
    }

    @Test
    fun migrate2To3_preservesExistingDataAndCreatesBudgetsAndGoals() {
        helper.createDatabase(DB_2_3, 2).apply {
            insertBaseData()
            execSQL(
                """
                INSERT INTO reconciliations (
                    id, accountId, statementLocalDate, statementBalanceMinor,
                    calculatedBalanceMinor, differenceMinor, adjustmentTransactionId, completedAt
                ) VALUES ('reconciliation-1', 'account-1', '2026-08-01', 12500, 12500, 0, NULL, 2)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_2_3, 3, true, MIGRATION_2_3).apply {
            assertBaseDataPreserved()
            assertRowCount("reconciliations", 1)
            execSQL(
                """
                INSERT INTO budget_allocations (id, monthStart, categoryId, plannedMinor)
                VALUES ('budget-1', '2026-08-01', 'category-1', 50000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO goals (
                    id, name, targetMinor, savedMinor, currencyCode, targetLocalDate, status, createdAt
                ) VALUES ('goal-1', 'Emergency fund', 100000, 20000, 'EUR', NULL, 'ACTIVE', 3)
                """.trimIndent(),
            )
            assertRowCount("budget_allocations", 1)
            assertRowCount("goals", 1)
            close()
        }
    }

    @Test
    fun migrate3To4_preservesExistingDataAndCreatesRecurrencesAndDebts() {
        helper.createDatabase(DB_3_4, 3).apply {
            insertBaseData()
            execSQL(
                """
                INSERT INTO budget_allocations (id, monthStart, categoryId, plannedMinor)
                VALUES ('budget-1', '2026-08-01', 'category-1', 50000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO goals (
                    id, name, targetMinor, savedMinor, currencyCode, targetLocalDate, status, createdAt
                ) VALUES ('goal-1', 'Emergency fund', 100000, 20000, 'EUR', NULL, 'ACTIVE', 3)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_3_4, 4, true, MIGRATION_3_4).apply {
            assertBaseDataPreserved()
            assertRowCount("budget_allocations", 1)
            assertRowCount("goals", 1)
            execSQL(
                """
                INSERT INTO recurring_schedules (
                    id, name, kind, amountMinor, currencyCode, accountId, categoryId,
                    frequency, intervalCount, nextLocalDate, active, createdAt
                ) VALUES (
                    'recurring-1', 'Rent', 'EXPENSE', 75000, 'EUR', 'account-1', 'category-1',
                    'MONTHLY', 1, '2026-09-01', 1, 4
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debt_profiles (
                    id, accountId, annualRateBasisPoints, minimumPaymentMinor, dueDay, createdAt
                ) VALUES ('debt-1', 'account-1', 350, 10000, 15, 4)
                """.trimIndent(),
            )
            assertRowCount("recurring_schedules", 1)
            assertRowCount("debt_profiles", 1)
            close()
        }
    }

    @Test
    fun migrate1To4_runsCompleteMigrationChainWithoutDataLoss() {
        helper.createDatabase(DB_1_4, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(
            DB_1_4,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        ).apply {
            assertBaseDataPreserved()
            listOf(
                "reconciliations",
                "budget_allocations",
                "goals",
                "recurring_schedules",
                "debt_profiles",
            ).forEach { assertRowCount(it, 0) }
            close()
        }
    }

    private fun SupportSQLiteDatabase.insertBaseData() {
        execSQL(
            """
            INSERT INTO accounts (
                id, name, type, currencyCode, openingBalanceMinor, archivedAt, createdAt, updatedAt
            ) VALUES ('account-1', 'Current account', 'CHECKING', 'EUR', 10000, NULL, 1, 1)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO categories (id, name, kind, sortOrder, archivedAt)
            VALUES ('category-1', 'Groceries', 'EXPENSE', 1, NULL)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO transactions (id, kind, localDate, payee, note, createdAt, updatedAt)
            VALUES ('transaction-1', 'EXPENSE', '2026-08-01', 'Market', 'Weekly shop', 1, 1)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO transaction_entries (
                id, transactionId, accountId, categoryId, amountMinor, currencyCode, cleared
            ) VALUES ('entry-1', 'transaction-1', 'account-1', 'category-1', -2500, 'EUR', 1)
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.assertBaseDataPreserved() {
        assertRowCount("accounts", 1)
        assertRowCount("categories", 1)
        assertRowCount("transactions", 1)
        assertRowCount("transaction_entries", 1)
        query("SELECT amountMinor FROM transaction_entries WHERE id = 'entry-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(-2500L, cursor.getLong(0))
        }
    }

    private fun SupportSQLiteDatabase.assertRowCount(table: String, expected: Int) {
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Unexpected row count in $table", expected, cursor.getInt(0))
        }
    }

    companion object {
        private const val DB_1_2 = "migration-1-2"
        private const val DB_2_3 = "migration-2-3"
        private const val DB_3_4 = "migration-3-4"
        private const val DB_1_4 = "migration-1-4"
    }
}
