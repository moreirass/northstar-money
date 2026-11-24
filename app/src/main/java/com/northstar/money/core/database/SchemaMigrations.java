package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.northstar.money.domain.model.CurrencyMath;

final class SchemaMigrations {
    private SchemaMigrations() {}

    static final Migration V1_TO_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("""
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
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliations_accountId ON reconciliations(accountId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliations_adjustmentTransactionId ON reconciliations(adjustmentTransactionId)");
        }
    };

    static final Migration V2_TO_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS budget_allocations (
                        id TEXT NOT NULL PRIMARY KEY,
                        monthStart TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        plannedMinor INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_allocations_categoryId ON budget_allocations(categoryId)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budget_allocations_monthStart_categoryId ON budget_allocations(monthStart, categoryId)");
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goals (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        targetMinor INTEGER NOT NULL,
                        savedMinor INTEGER NOT NULL,
                        currencyCode TEXT NOT NULL,
                        targetLocalDate TEXT,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_status ON goals(status)");
        }
    };

    static final Migration V3_TO_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recurring_schedules (
                        id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL,
                        accountId TEXT NOT NULL, categoryId TEXT, frequency TEXT NOT NULL,
                        intervalCount INTEGER NOT NULL, nextLocalDate TEXT NOT NULL,
                        active INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_accountId ON recurring_schedules(accountId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_categoryId ON recurring_schedules(categoryId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_nextLocalDate ON recurring_schedules(nextLocalDate)");
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS debt_profiles (
                        id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL,
                        annualRateBasisPoints INTEGER NOT NULL, minimumPaymentMinor INTEGER NOT NULL,
                        dueDay INTEGER NOT NULL, createdAt INTEGER NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE
                    )""");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debt_profiles_accountId ON debt_profiles(accountId)");
        }
    };

    static final Migration V4_TO_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_deletedAt ON transactions(deletedAt)");
        }
    };

    static final Migration V5_TO_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE categories ADD COLUMN mergedIntoCategoryId TEXT REFERENCES categories(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_mergedIntoCategoryId ON categories(mergedIntoCategoryId)");
        }
    };

    static final Migration V6_TO_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE recurring_schedules ADD COLUMN deletedAt INTEGER");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_schedules_deletedAt ON recurring_schedules(deletedAt)");
        }
    };

    static final Migration V7_TO_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goal_contributions (
                        id TEXT NOT NULL PRIMARY KEY,
                        goalId TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        localDate TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        FOREIGN KEY(goalId) REFERENCES goals(id) ON DELETE CASCADE
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_goalId ON goal_contributions(goalId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_localDate ON goal_contributions(localDate)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_contributions_deletedAt ON goal_contributions(deletedAt)");
            db.execSQL("""
                    INSERT INTO goal_contributions (
                        id, goalId, amountMinor, localDate, note, createdAt, updatedAt, deletedAt
                    )
                    SELECT 'legacy-' || id, id, savedMinor,
                           date(createdAt / 1000, 'unixepoch'), 'Opening saved amount', createdAt, createdAt, NULL
                    FROM goals
                    WHERE savedMinor > 0""");
            db.execSQL("UPDATE goals SET savedMinor = 0 WHERE savedMinor > 0");
        }
    };

    static final Migration V8_TO_9 = new Migration(8, 9) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipt_attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        transactionId TEXT NOT NULL,
                        content BLOB NOT NULL,
                        originalName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        byteSize INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        ocrStatus TEXT NOT NULL,
                        ocrText TEXT,
                        detectedAmountMinor INTEGER,
                        detectedCurrencyCode TEXT,
                        detectedLocalDate TEXT,
                        detectedMerchant TEXT,
                        FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_attachments_transactionId ON receipt_attachments(transactionId)");
            db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transaction_exchange_rates (
                        id TEXT NOT NULL PRIMARY KEY,
                        transactionId TEXT NOT NULL,
                        entryId TEXT NOT NULL,
                        baseCurrencyCode TEXT NOT NULL,
                        quoteCurrencyCode TEXT NOT NULL,
                        rateMicros INTEGER,
                        convertedAmountMinor INTEGER,
                        rateLocalDate TEXT NOT NULL,
                        source TEXT NOT NULL,
                        status TEXT NOT NULL,
                        fetchedAt INTEGER,
                        FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE,
                        FOREIGN KEY(entryId) REFERENCES transaction_entries(id) ON DELETE CASCADE
                    )""");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_exchange_rates_transactionId ON transaction_exchange_rates(transactionId)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transaction_exchange_rates_entryId ON transaction_exchange_rates(entryId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_exchange_rates_rateLocalDate ON transaction_exchange_rates(rateLocalDate)");
        }
    };

    static Migration currencyUpgrade(String legacyCurrency) {
        return new Migration(9, 10) {
            @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
                if (!CurrencyMath.validCurrency(legacyCurrency)) throw new IllegalArgumentException("Invalid migration currency");
                db.execSQL("ALTER TABLE budget_allocations ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'EUR'");
                db.execSQL("UPDATE budget_allocations SET currencyCode = ?", new Object[]{legacyCurrency});
                db.execSQL("DROP INDEX index_budget_allocations_monthStart_categoryId");
                db.execSQL("CREATE UNIQUE INDEX index_budget_allocations_monthStart_categoryId_currencyCode ON budget_allocations(monthStart, categoryId, currencyCode)");
                db.execSQL("ALTER TABLE recurring_schedules ADD COLUMN anchorDay INTEGER NOT NULL DEFAULT 1");
                db.execSQL("ALTER TABLE recurring_schedules ADD COLUMN anchorMonth INTEGER NOT NULL DEFAULT 1");
                db.execSQL("UPDATE recurring_schedules SET anchorDay = CAST(substr(nextLocalDate, 9, 2) AS INTEGER), anchorMonth = CAST(substr(nextLocalDate, 6, 2) AS INTEGER)");
                db.execSQL("""
                        WITH RECURSIVE resolved(original, target, depth) AS (
                                    SELECT id, mergedIntoCategoryId, 0 FROM categories WHERE mergedIntoCategoryId IS NOT NULL
                                    UNION ALL SELECT resolved.original, category.mergedIntoCategoryId, resolved.depth + 1
                                    FROM resolved JOIN categories category ON category.id = resolved.target
                                    WHERE category.mergedIntoCategoryId IS NOT NULL AND resolved.depth < 100
                                ) UPDATE categories SET mergedIntoCategoryId = (
                                    SELECT resolved.target FROM resolved JOIN categories final ON final.id = resolved.target
                                    WHERE resolved.original = categories.id AND final.mergedIntoCategoryId IS NULL AND final.archivedAt IS NULL
                                    ORDER BY resolved.depth DESC LIMIT 1
                                ) WHERE mergedIntoCategoryId IS NOT NULL""");
                db.execSQL("""
                        DELETE FROM transaction_exchange_rates WHERE EXISTS (
                                    SELECT 1 FROM transaction_entries entry JOIN transactions t ON t.id = entry.transactionId
                                    WHERE entry.id = transaction_exchange_rates.entryId AND
                                        (t.id != transaction_exchange_rates.transactionId OR t.localDate != transaction_exchange_rates.rateLocalDate
                                         OR entry.currencyCode != transaction_exchange_rates.baseCurrencyCode)
                                )""");
                db.execSQL("""
                        CREATE TABLE IF NOT EXISTS account_exchange_rates (
                                    accountId TEXT NOT NULL PRIMARY KEY, baseCurrencyCode TEXT NOT NULL,
                                    quoteCurrencyCode TEXT NOT NULL, openingBalanceMinor INTEGER NOT NULL,
                                    convertedAmountMinor INTEGER NOT NULL, rateMicros INTEGER NOT NULL,
                                    rateLocalDate TEXT NOT NULL,
                                    FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE
                                )""");
            }
        };
    }
}
