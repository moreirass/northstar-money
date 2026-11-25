package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        AccountEntity.class,
        AccountExchangeRateEntity.class,
        CategoryEntity.class,
        TransactionEntity.class,
        TransactionEntryEntity.class,
        ReconciliationEntity.class,
        BudgetAllocationEntity.class,
        GoalEntity.class,
        GoalContributionEntity.class,
        RecurringScheduleEntity.class,
        DebtProfileEntity.class,
        ReceiptAttachmentEntity.class,
        TransactionExchangeRateEntity.class,
    },
    version = NorthstarDatabase.VERSION,
    exportSchema = true
)
public abstract class NorthstarDatabase extends RoomDatabase {
    public static final int VERSION = 10;

    protected abstract FinanceDao financeQueries();

    @NonNull
    public final SuspendingFinanceDao financeDao() {
        return new SuspendingFinanceDao(this, financeQueries());
    }
}
