package com.northstar.money.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionEntryEntity::class,
        ReconciliationEntity::class,
        BudgetAllocationEntity::class,
        GoalEntity::class,
        RecurringScheduleEntity::class,
        DebtProfileEntity::class,
    ],
    version = NorthstarDatabase.VERSION,
    exportSchema = true,
)
abstract class NorthstarDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        const val VERSION = 4
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reconciliations (
                id TEXT NOT NULL PRIMARY KEY,
                accountId TEXT NOT NULL,
                statementLocalDate TEXT NOT NULL,
                statementBalanceMinor INTEGER NOT NULL,
                calculatedBalanceMinor INTEGER NOT NULL,
                differenceMinor INTEGER NOT NULL,
                adjustmentTransactionId TEXT,
                completedAt INTEGER NOT NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE,
                FOREIGN KEY(adjustmentTransactionId) REFERENCES transactions(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliations_accountId ON reconciliations(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliations_adjustmentTransactionId ON reconciliations(adjustmentTransactionId)")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS budget_allocations (
                id TEXT NOT NULL PRIMARY KEY,
                monthStart TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                plannedMinor INTEGER NOT NULL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_allocations_categoryId ON budget_allocations(categoryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budget_allocations_monthStart_categoryId ON budget_allocations(monthStart, categoryId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goals (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                targetMinor INTEGER NOT NULL,
                savedMinor INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                targetLocalDate TEXT,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_status ON goals(status)")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurring_schedules (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL,
                amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL,
                accountId TEXT NOT NULL, categoryId TEXT, frequency TEXT NOT NULL,
                intervalCount INTEGER NOT NULL, nextLocalDate TEXT NOT NULL,
                active INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_accountId ON recurring_schedules(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_categoryId ON recurring_schedules(categoryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_nextLocalDate ON recurring_schedules(nextLocalDate)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS debt_profiles (
                id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL,
                annualRateBasisPoints INTEGER NOT NULL, minimumPaymentMinor INTEGER NOT NULL,
                dueDay INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debt_profiles_accountId ON debt_profiles(accountId)")
    }
}
