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

    @Test
    fun migrate4To5_preservesTransactionsAndAddsRecoverableDeletion() {
        helper.createDatabase(DB_4_5, 4).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(DB_4_5, 5, true, MIGRATION_4_5).apply {
            assertBaseDataPreserved()
            query("SELECT deletedAt FROM transactions WHERE id = 'transaction-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            execSQL("UPDATE transactions SET deletedAt = 123 WHERE id = 'transaction-1'")
            query("SELECT deletedAt FROM transactions WHERE id = 'transaction-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(123L, cursor.getLong(0))
            }
            close()
        }
    }

    @Test
    fun migrate1To5_runsCompleteMigrationChainWithoutDataLoss() {
        helper.createDatabase(DB_1_5, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(
            DB_1_5,
            5,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        ).apply {
            assertBaseDataPreserved()
            query("SELECT deletedAt FROM transactions WHERE id = 'transaction-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            close()
        }
    }

    @Test
    fun migrate5To6_preservesCategoriesAndAddsReversibleMergeLink() {
        helper.createDatabase(DB_5_6, 5).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(DB_5_6, 6, true, MIGRATION_5_6).apply {
            assertBaseDataPreserved()
            query("SELECT mergedIntoCategoryId FROM categories WHERE id = 'category-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            close()
        }
    }

    @Test
    fun migrate1To6_runsCompleteMigrationChainWithoutDataLoss() {
        helper.createDatabase(DB_1_6, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(
            DB_1_6,
            6,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        ).apply {
            assertBaseDataPreserved()
            query("SELECT mergedIntoCategoryId FROM categories WHERE id = 'category-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            close()
        }
    }

    @Test
    fun migrate6To7_preservesRecurrencesAndAddsRecoverableDeletion() {
        helper.createDatabase(DB_6_7, 6).apply {
            insertBaseData()
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
            close()
        }

        helper.runMigrationsAndValidate(DB_6_7, 7, true, MIGRATION_6_7).apply {
            assertBaseDataPreserved()
            query("SELECT deletedAt FROM recurring_schedules WHERE id = 'recurring-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            execSQL("UPDATE recurring_schedules SET deletedAt = 123 WHERE id = 'recurring-1'")
            query("SELECT deletedAt FROM recurring_schedules WHERE id = 'recurring-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(123L, cursor.getLong(0))
            }
            close()
        }
    }

    @Test
    fun migrate1To7_runsCompleteMigrationChainWithoutDataLoss() {
        helper.createDatabase(DB_1_7, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(
            DB_1_7,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        ).apply {
            assertBaseDataPreserved()
            close()
        }
    }

    @Test
    fun migrate7To8_preservesSavedGoalAsAuditableContribution() {
        helper.createDatabase(DB_7_8, 7).apply {
            insertBaseData()
            execSQL(
                """
                INSERT INTO goals (
                    id, name, targetMinor, savedMinor, currencyCode, targetLocalDate, status, createdAt
                ) VALUES ('goal-1', 'Emergency fund', 100000, 20000, 'EUR', NULL, 'ACTIVE', 1000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_7_8, 8, true, MIGRATION_7_8).apply {
            assertBaseDataPreserved()
            query("SELECT savedMinor FROM goals WHERE id = 'goal-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
            }
            query("SELECT goalId, amountMinor, deletedAt FROM goal_contributions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("goal-1", cursor.getString(0))
                assertEquals(20000L, cursor.getLong(1))
                assertEquals(true, cursor.isNull(2))
            }
            close()
        }
    }

    @Test
    fun migrate1To8_runsCompleteMigrationChainWithoutDataLoss() {
        helper.createDatabase(DB_1_8, 1).apply {
            insertBaseData()
            close()
        }

        helper.runMigrationsAndValidate(
            DB_1_8,
            8,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        ).apply {
            assertBaseDataPreserved()
            assertRowCount("goal_contributions", 0)
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
        private const val DB_4_5 = "migration-4-5"
        private const val DB_1_5 = "migration-1-5"
        private const val DB_5_6 = "migration-5-6"
        private const val DB_1_6 = "migration-1-6"
        private const val DB_6_7 = "migration-6-7"
        private const val DB_1_7 = "migration-1-7"
        private const val DB_7_8 = "migration-7-8"
        private const val DB_1_8 = "migration-1-8"
    }
}
