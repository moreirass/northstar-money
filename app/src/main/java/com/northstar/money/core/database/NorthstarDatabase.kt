package com.northstar.money.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        AccountExchangeRateEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionEntryEntity::class,
        ReconciliationEntity::class,
        BudgetAllocationEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        RecurringScheduleEntity::class,
        DebtProfileEntity::class,
        ReceiptAttachmentEntity::class,
        TransactionExchangeRateEntity::class,
    ],
    version = NorthstarDatabase.VERSION,
    exportSchema = true,
)
abstract class NorthstarDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        const val VERSION = 10
    }
}

val MIGRATION_1_2 = SchemaMigrations.V1_TO_2
val MIGRATION_2_3 = SchemaMigrations.V2_TO_3
val MIGRATION_3_4 = SchemaMigrations.V3_TO_4
val MIGRATION_4_5 = SchemaMigrations.V4_TO_5
val MIGRATION_5_6 = SchemaMigrations.V5_TO_6
val MIGRATION_6_7 = SchemaMigrations.V6_TO_7
val MIGRATION_7_8 = SchemaMigrations.V7_TO_8
val MIGRATION_8_9 = SchemaMigrations.V8_TO_9

fun migration9To10(legacyCurrency: String = "EUR") = SchemaMigrations.currencyUpgrade(legacyCurrency)
val MIGRATION_9_10 = migration9To10()
