package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.northstar.money.domain.model.CurrencyMath;
import java.util.ArrayList;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public abstract class FinanceDao {
    @Query(
        """
            SELECT a.id, a.name, a.type, a.currencyCode,
                   a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0) AS balanceMinor,
                   a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL AND e.cleared = 1 THEN e.amountMinor ELSE 0 END), 0) AS clearedBalanceMinor
            FROM accounts a
            LEFT JOIN transaction_entries e ON e.accountId = a.id
            LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
                AND e.currencyCode = a.currencyCode
            WHERE a.archivedAt IS NULL
            GROUP BY a.id
            ORDER BY a.createdAt
            """
    )
    public abstract @NonNull Flow<List<AccountBalanceRow>> observeAccounts();

    @Query(
        """
            SELECT a.id, a.name, a.type, a.currencyCode,
                   a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0) AS balanceMinor,
                   a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL AND e.cleared = 1 THEN e.amountMinor ELSE 0 END), 0) AS clearedBalanceMinor
            FROM accounts a
            LEFT JOIN transaction_entries e ON e.accountId = a.id
            LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
                AND e.currencyCode = a.currencyCode
            WHERE a.archivedAt IS NOT NULL
            GROUP BY a.id
            ORDER BY a.createdAt
            """
    )
    public abstract @NonNull Flow<List<AccountBalanceRow>> observeArchivedAccounts();

    @Query("SELECT COUNT(*) FROM categories WHERE archivedAt IS NULL AND kind = :kind")
    public abstract int countActiveCategories(@NonNull String kind);

    @Query("SELECT * FROM categories WHERE archivedAt IS NULL ORDER BY kind, sortOrder, name")
    public abstract @NonNull Flow<List<CategoryEntity>> observeCategories();

    @Query(
        """
            SELECT c.id, c.name, c.kind, c.mergedIntoCategoryId,
                   target.name AS mergedIntoCategoryName
            FROM categories c
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE c.archivedAt IS NOT NULL
            ORDER BY c.kind, c.name
            """
    )
    public abstract @NonNull Flow<List<ArchivedCategoryRow>> observeArchivedCategories();

    @Query(
        """
            SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
                   e.accountId, COALESCE(target.id, c.id) AS categoryId,
                   (SELECT other.accountId FROM transaction_entries other
                    WHERE other.transactionId = t.id AND other.id != e.id LIMIT 1) AS destinationAccountId,
                   a.name AS accountName, COALESCE(target.name, c.name) AS categoryName,
                   NOT EXISTS (
                       SELECT 1 FROM transaction_entries pending
                       WHERE pending.transactionId = t.id AND pending.cleared = 0
                   ) AS cleared
            FROM transactions t
            JOIN transaction_entries e ON e.transactionId = t.id
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN categories c ON c.id = e.categoryId
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE t.deletedAt IS NULL AND e.id = (
                SELECT e2.id FROM transaction_entries e2 WHERE e2.transactionId = t.id
                ORDER BY e2.amountMinor, e2.id LIMIT 1
            )
            ORDER BY t.localDate DESC, t.createdAt DESC
            """
    )
    public abstract @NonNull Flow<List<TransactionRow>> observeTransactions();

    @Query(
        """
            SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
                   e.accountId, COALESCE(target.id, c.id) AS categoryId,
                   (SELECT other.accountId FROM transaction_entries other
                    WHERE other.transactionId = t.id AND other.id != e.id LIMIT 1) AS destinationAccountId,
                   a.name AS accountName, COALESCE(target.name, c.name) AS categoryName,
                   NOT EXISTS (
                       SELECT 1 FROM transaction_entries pending
                       WHERE pending.transactionId = t.id AND pending.cleared = 0
                   ) AS cleared
            FROM transactions t
            JOIN transaction_entries e ON e.transactionId = t.id
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN categories c ON c.id = e.categoryId
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE t.deletedAt IS NULL AND e.id = (
                SELECT e2.id FROM transaction_entries e2 WHERE e2.transactionId = t.id
                ORDER BY e2.amountMinor, e2.id LIMIT 1
            )
            ORDER BY t.localDate DESC, t.createdAt DESC LIMIT :limit
            """
    )
    public abstract @NonNull Flow<List<TransactionRow>> observeRecentTransactions(int limit);

    @Query(
        """
            SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
                   e.accountId, COALESCE(target.id, c.id) AS categoryId,
                   (SELECT other.accountId FROM transaction_entries other
                    WHERE other.transactionId = t.id AND other.id != e.id LIMIT 1) AS destinationAccountId,
                   a.name AS accountName, COALESCE(target.name, c.name) AS categoryName,
                   NOT EXISTS (
                       SELECT 1 FROM transaction_entries pending
                       WHERE pending.transactionId = t.id AND pending.cleared = 0
                   ) AS cleared
            FROM transactions t
            JOIN transaction_entries e ON e.transactionId = t.id
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN categories c ON c.id = e.categoryId
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE t.deletedAt IS NULL
              AND (:startDate IS NULL OR t.localDate >= :startDate)
              AND (:endDate IS NULL OR t.localDate < :endDate)
              AND (:categoryId IS NULL OR COALESCE(target.id, c.id) = :categoryId)
              AND e.id = (
                SELECT e2.id FROM transaction_entries e2 WHERE e2.transactionId = t.id
                ORDER BY e2.amountMinor, e2.id LIMIT 1
            )
            ORDER BY t.localDate DESC, t.createdAt DESC LIMIT :limit
            """
    )
    public abstract @NonNull Flow<List<TransactionRow>> observeTransactionWindow(@Nullable String startDate,
            @Nullable String endDate,
            @Nullable String categoryId,
            int limit);

    @Query("SELECT id, transactionId, originalName, mimeType, byteSize, createdAt, ocrStatus, detectedAmountMinor, detectedCurrencyCode, detectedLocalDate, detectedMerchant FROM receipt_attachments ORDER BY createdAt DESC")
    public abstract @NonNull Flow<List<ReceiptMetadataRow>> observeReceiptAttachments();

    @Insert
    public abstract void insertReceiptAttachment(@NonNull ReceiptAttachmentEntity item);

    @Update
    public abstract int updateReceiptAttachment(@NonNull ReceiptAttachmentEntity item);

    @Query("SELECT * FROM receipt_attachments WHERE id = :id")
    public abstract @Nullable ReceiptAttachmentEntity getReceiptAttachment(@NonNull String id);

    @Query("DELETE FROM receipt_attachments WHERE id = :id")
    public abstract int deleteReceiptAttachment(@NonNull String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertTransactionExchangeRate(@NonNull TransactionExchangeRateEntity item);

    @Query("SELECT * FROM transaction_exchange_rates ORDER BY rateLocalDate DESC")
    public abstract @NonNull Flow<List<TransactionExchangeRateEntity>> observeTransactionExchangeRates();

    @Query("SELECT * FROM transaction_exchange_rates WHERE status = 'PENDING'")
    public abstract @NonNull List<TransactionExchangeRateEntity> getPendingTransactionExchangeRates();

    @Query("SELECT * FROM transaction_exchange_rates WHERE entryId = :entryId")
    public abstract @Nullable TransactionExchangeRateEntity getTransactionExchangeRate(@NonNull String entryId);

    @Query(
        """
            SELECT t.id AS transactionId, t.localDate, e.id AS entryId, e.accountId, e.categoryId,
                   e.amountMinor, e.currencyCode, e.cleared
            FROM transaction_entries e
            JOIN transactions t ON t.id = e.transactionId
            LEFT JOIN transaction_exchange_rates fx ON fx.entryId = e.id
            WHERE t.deletedAt IS NULL AND (fx.id IS NULL OR fx.status != 'AVAILABLE'
                OR fx.quoteCurrencyCode != :quoteCurrencyCode OR fx.baseCurrencyCode != e.currencyCode
                OR fx.rateLocalDate != t.localDate)
            """
    )
    public abstract @NonNull List<EntryRateCandidate> getEntriesMissingExchangeRates(@NonNull String quoteCurrencyCode);

    @Query(
        """
            SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
                   e.accountId, COALESCE(target.id, c.id) AS categoryId,
                   (SELECT other.accountId FROM transaction_entries other
                    WHERE other.transactionId = t.id AND other.id != e.id LIMIT 1) AS destinationAccountId,
                   a.name AS accountName, COALESCE(target.name, c.name) AS categoryName,
                   NOT EXISTS (
                       SELECT 1 FROM transaction_entries pending
                       WHERE pending.transactionId = t.id AND pending.cleared = 0
                   ) AS cleared
            FROM transactions t
            JOIN transaction_entries e ON e.transactionId = t.id
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN categories c ON c.id = e.categoryId
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE t.deletedAt IS NOT NULL AND e.id = (
                SELECT e2.id FROM transaction_entries e2 WHERE e2.transactionId = t.id
                ORDER BY e2.amountMinor, e2.id LIMIT 1
            )
            ORDER BY t.deletedAt DESC
            """
    )
    public abstract @NonNull Flow<List<TransactionRow>> observeDeletedTransactions();

    @Query(
        """
            SELECT
              COALESCE((SELECT SUM(CASE WHEN opening.currencyCode = :baseCurrencyCode
                        THEN opening.openingBalanceMinor ELSE openingFx.convertedAmountMinor END)
                        FROM accounts opening LEFT JOIN account_exchange_rates openingFx
                        ON openingFx.accountId = opening.id AND openingFx.quoteCurrencyCode = :baseCurrencyCode
                        AND openingFx.baseCurrencyCode = opening.currencyCode
                        AND openingFx.openingBalanceMinor = opening.openingBalanceMinor
                        WHERE opening.archivedAt IS NULL), 0)
                + COALESCE(SUM(CASE WHEN e.currencyCode = :baseCurrencyCode
                                    THEN e.amountMinor ELSE fx.convertedAmountMinor END), 0) AS balanceMinor,
              COALESCE(SUM(CASE WHEN t.kind = 'INCOME' AND t.localDate >= :monthStart AND t.localDate < :nextMonthStart
                                THEN CASE WHEN e.currencyCode = :baseCurrencyCode
                                          THEN e.amountMinor ELSE fx.convertedAmountMinor END ELSE 0 END), 0) AS incomeMinor,
              COALESCE(-SUM(CASE WHEN t.kind = 'EXPENSE' AND t.localDate >= :monthStart AND t.localDate < :nextMonthStart
                                 THEN CASE WHEN e.currencyCode = :baseCurrencyCode
                                           THEN e.amountMinor ELSE fx.convertedAmountMinor END ELSE 0 END), 0) AS expenseMinor,
              COALESCE(SUM(CASE WHEN e.currencyCode != :baseCurrencyCode AND fx.id IS NULL THEN 1 ELSE 0 END), 0)
              + (SELECT COUNT(*) FROM accounts opening WHERE opening.archivedAt IS NULL
                 AND opening.currencyCode != :baseCurrencyCode AND opening.openingBalanceMinor != 0
                 AND NOT EXISTS (SELECT 1 FROM account_exchange_rates openingFx
                     WHERE openingFx.accountId = opening.id AND openingFx.quoteCurrencyCode = :baseCurrencyCode
                     AND openingFx.baseCurrencyCode = opening.currencyCode
                     AND openingFx.openingBalanceMinor = opening.openingBalanceMinor)) AS missingConversions
            FROM transaction_entries e
            JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN transaction_exchange_rates fx ON fx.entryId = e.id AND fx.status = 'AVAILABLE' AND fx.quoteCurrencyCode = :baseCurrencyCode AND fx.baseCurrencyCode = e.currencyCode
            WHERE a.archivedAt IS NULL
            """
    )
    public abstract @NonNull Flow<SummaryRow> observeSummary(@NonNull String monthStart,
            @NonNull String baseCurrencyCode,
            @NonNull String nextMonthStart);

    @Query(
        """
            WITH budget_values AS (
                SELECT c.id AS categoryId, c.name AS categoryName, c.sortOrder,
                       COALESCE((
                           SELECT SUM(currentAllocation.plannedMinor)
                           FROM budget_allocations currentAllocation
                           WHERE currentAllocation.categoryId = c.id AND currentAllocation.currencyCode = :baseCurrencyCode
                             AND currentAllocation.monthStart = :monthStart
                       ), 0) AS allocatedMinor,
                       CASE WHEN EXISTS (
                           SELECT 1
                           FROM budget_allocations earlierAllocation
                           JOIN categories earlierCategory ON earlierCategory.id = earlierAllocation.categoryId
                           WHERE COALESCE(earlierCategory.mergedIntoCategoryId, earlierCategory.id) = c.id
                             AND earlierAllocation.monthStart < :monthStart AND earlierAllocation.currencyCode = :baseCurrencyCode
                       ) THEN
                           COALESCE((
                               SELECT SUM(priorAllocation.plannedMinor)
                               FROM budget_allocations priorAllocation
                               JOIN categories allocatedCategory ON allocatedCategory.id = priorAllocation.categoryId
                               WHERE COALESCE(allocatedCategory.mergedIntoCategoryId, allocatedCategory.id) = c.id
                                 AND priorAllocation.monthStart < :monthStart AND priorAllocation.currencyCode = :baseCurrencyCode
                           ), 0) - COALESCE((
                               SELECT -SUM(CASE WHEN entry.currencyCode = :baseCurrencyCode THEN entry.amountMinor ELSE fx.convertedAmountMinor END)
                               FROM transaction_entries entry
                               JOIN transactions transactionRecord ON transactionRecord.id = entry.transactionId
                               JOIN categories spentCategory ON spentCategory.id = entry.categoryId
                               LEFT JOIN transaction_exchange_rates fx ON fx.entryId = entry.id
                                 AND fx.status = 'AVAILABLE' AND fx.quoteCurrencyCode = :baseCurrencyCode
                                 AND fx.baseCurrencyCode = entry.currencyCode
                               WHERE COALESCE(spentCategory.mergedIntoCategoryId, spentCategory.id) = c.id

                                 AND transactionRecord.kind = 'EXPENSE' AND transactionRecord.deletedAt IS NULL
                                 AND transactionRecord.localDate >= (
                                     SELECT MIN(firstAllocation.monthStart)
                                     FROM budget_allocations firstAllocation
                                     JOIN categories firstCategory ON firstCategory.id = firstAllocation.categoryId
                                     WHERE COALESCE(firstCategory.mergedIntoCategoryId, firstCategory.id) = c.id AND firstAllocation.currencyCode = :baseCurrencyCode
                                 )
                                 AND transactionRecord.localDate < :monthStart
                           ), 0)
                       ELSE 0 END AS rolloverMinor,
                       COALESCE((
                           SELECT -SUM(CASE WHEN entry.currencyCode = :baseCurrencyCode THEN entry.amountMinor ELSE fx.convertedAmountMinor END)
                           FROM transaction_entries entry
                           JOIN transactions transactionRecord ON transactionRecord.id = entry.transactionId
                           JOIN categories spentCategory ON spentCategory.id = entry.categoryId
                               LEFT JOIN transaction_exchange_rates fx ON fx.entryId = entry.id
                                 AND fx.status = 'AVAILABLE' AND fx.quoteCurrencyCode = :baseCurrencyCode
                                 AND fx.baseCurrencyCode = entry.currencyCode
                           WHERE COALESCE(spentCategory.mergedIntoCategoryId, spentCategory.id) = c.id

                             AND transactionRecord.kind = 'EXPENSE' AND transactionRecord.deletedAt IS NULL
                             AND transactionRecord.localDate >= :monthStart
                             AND transactionRecord.localDate < :nextMonthStart
                       ), 0) AS spentMinor
                FROM categories c
                WHERE c.kind = 'EXPENSE' AND c.archivedAt IS NULL
            )
            SELECT categoryId, categoryName, allocatedMinor, rolloverMinor,
                   allocatedMinor + rolloverMinor AS plannedMinor, spentMinor,
                   (SELECT COUNT(*) FROM transaction_entries entry
                    JOIN transactions t ON t.id = entry.transactionId
                    JOIN categories category ON category.id = entry.categoryId
                    WHERE COALESCE(category.mergedIntoCategoryId, category.id) = budget_values.categoryId
                    AND t.deletedAt IS NULL AND t.kind = 'EXPENSE'
                    AND t.localDate >= :monthStart AND t.localDate < :nextMonthStart
                    AND entry.currencyCode != :baseCurrencyCode
                    AND NOT EXISTS (SELECT 1 FROM transaction_exchange_rates fx WHERE fx.entryId = entry.id
                        AND fx.status = 'AVAILABLE' AND fx.quoteCurrencyCode = :baseCurrencyCode
                        AND fx.baseCurrencyCode = entry.currencyCode)) AS missingConversions
            FROM budget_values
            ORDER BY sortOrder, categoryName
            """
    )
    public abstract @NonNull Flow<List<BudgetRow>> observeBudgets(@NonNull String monthStart,
            @NonNull String nextMonthStart,
            @NonNull String baseCurrencyCode);

    @Query(
        """
            SELECT g.id, g.name, g.targetMinor,
                   g.savedMinor + COALESCE(SUM(CASE WHEN contribution.deletedAt IS NULL THEN contribution.amountMinor ELSE 0 END), 0) AS savedMinor,
                   g.currencyCode, g.targetLocalDate, g.status, g.createdAt
            FROM goals g
            LEFT JOIN goal_contributions contribution ON contribution.goalId = g.id
            GROUP BY g.id
            ORDER BY g.createdAt
            """
    )
    public abstract @NonNull Flow<List<GoalEntity>> observeGoals();

    @Query(
        """
            SELECT contribution.id, contribution.goalId, goal.name AS goalName,
                   contribution.amountMinor, goal.currencyCode,
                   contribution.localDate, contribution.note
            FROM goal_contributions contribution
            JOIN goals goal ON goal.id = contribution.goalId
            WHERE contribution.deletedAt IS NULL
            ORDER BY contribution.localDate DESC, contribution.createdAt DESC
            """
    )
    public abstract @NonNull Flow<List<GoalContributionRow>> observeGoalContributions();

    @Query(
        """
            SELECT contribution.id, contribution.goalId, goal.name AS goalName,
                   contribution.amountMinor, goal.currencyCode,
                   contribution.localDate, contribution.note
            FROM goal_contributions contribution
            JOIN goals goal ON goal.id = contribution.goalId
            WHERE contribution.deletedAt IS NOT NULL
            ORDER BY contribution.deletedAt DESC
            """
    )
    public abstract @NonNull Flow<List<GoalContributionRow>> observeDeletedGoalContributions();

    @Query(
        """
            SELECT r.* FROM recurring_schedules r
            JOIN accounts a ON a.id = r.accountId
            WHERE r.active = 1 AND r.deletedAt IS NULL AND a.archivedAt IS NULL
            ORDER BY r.nextLocalDate
            """
    )
    public abstract @NonNull Flow<List<RecurringScheduleEntity>> observeRecurring();

    @Query("SELECT * FROM recurring_schedules WHERE active = 0 AND deletedAt IS NULL ORDER BY nextLocalDate")
    public abstract @NonNull Flow<List<RecurringScheduleEntity>> observePausedRecurring();

    @Query("SELECT * FROM recurring_schedules WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    public abstract @NonNull Flow<List<RecurringScheduleEntity>> observeDeletedRecurring();

    @Query(
        """
            SELECT recurring.* FROM recurring_schedules recurring
            JOIN accounts account ON account.id = recurring.accountId
            WHERE recurring.active = 1 AND recurring.deletedAt IS NULL
              AND recurring.nextLocalDate <= :throughLocalDate AND account.archivedAt IS NULL
            ORDER BY recurring.nextLocalDate, recurring.id
            """
    )
    public abstract @NonNull List<RecurringScheduleEntity> getDueRecurringSchedules(@NonNull String throughLocalDate);

    @Query(
        """
            SELECT d.id, d.accountId, d.annualRateBasisPoints, d.minimumPaymentMinor,
                   d.dueDay, d.createdAt, a.currencyCode
            FROM debt_profiles d
            JOIN accounts a ON a.id = d.accountId
            WHERE a.archivedAt IS NULL
            ORDER BY d.createdAt
            """
    )
    public abstract @NonNull Flow<List<DebtProfileRow>> observeDebts();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertBudget(@NonNull BudgetAllocationEntity item);

    @Insert
    public abstract void insertGoal(@NonNull GoalEntity item);

    @Insert
    protected abstract void insertGoalContributionEntity(@NonNull GoalContributionEntity item);

    @Transaction
    public void insertGoalWithInitialContribution(@NonNull GoalEntity goal,
            @Nullable GoalContributionEntity initialContribution) {
        insertGoal(goal);
        if (initialContribution != null) insertGoalContributionEntity(initialContribution);
    }

    @Query("SELECT * FROM goals WHERE id = :id")
    public abstract @Nullable GoalEntity getGoal(@NonNull String id);

    @Update
    protected abstract int updateGoalEntity(@NonNull GoalEntity goal);

    @Query("SELECT * FROM goal_contributions WHERE id = :id AND deletedAt IS NULL")
    public abstract @Nullable GoalContributionEntity getGoalContributionForEdit(@NonNull String id);

    @Update
    protected abstract int updateGoalContributionEntity(@NonNull GoalContributionEntity contribution);

    @Query("UPDATE goal_contributions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract int softDeleteGoalContribution(@NonNull String id, long deletedAt);

    @Query("UPDATE goal_contributions SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id AND deletedAt IS NOT NULL")
    protected abstract int restoreDeletedGoalContribution(@NonNull String id, long restoredAt);

    @Transaction
    public void updateGoal(@NonNull GoalEntity goal) {
        require(updateGoalEntity(goal) == 1, "Savings goal could not be updated");
    }

    @Transaction
    public void insertGoalContribution(@NonNull GoalContributionEntity contribution) {
        require(getGoal(contribution.getGoalId()) != null, "Savings goal is missing");
        insertGoalContributionEntity(contribution);
    }

    @Transaction
    public void updateGoalContribution(@NonNull GoalContributionEntity contribution) {
        require(updateGoalContributionEntity(contribution) == 1, "Contribution could not be updated");
    }

    @Transaction
    public void deleteGoalContribution(@NonNull String id, long deletedAt) {
        require(softDeleteGoalContribution(id, deletedAt) == 1, "Contribution is missing or deleted");
    }

    @Transaction
    public void restoreGoalContribution(@NonNull String id, long restoredAt) {
        require(restoreDeletedGoalContribution(id, restoredAt) == 1, "Contribution is not available for recovery");
    }

    @Insert
    public abstract void insertRecurring(@NonNull RecurringScheduleEntity item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertDebt(@NonNull DebtProfileEntity item);

    @Query("SELECT * FROM debt_profiles WHERE id = :id")
    public abstract @Nullable DebtProfileEntity getDebtProfile(@NonNull String id);

    @Query("SELECT COUNT(*) FROM debt_profiles WHERE accountId = :accountId")
    protected abstract int countDebtProfilesForAccount(@NonNull String accountId);

    @Insert
    protected abstract void insertDebtProfileEntity(@NonNull DebtProfileEntity item);

    @Update
    protected abstract int updateDebtProfileEntity(@NonNull DebtProfileEntity item);

    @Transaction
    public void insertDebtProfile(@NonNull DebtProfileEntity item) {
        require(countDebtProfilesForAccount(item.getAccountId()) == 0, "Account already has a debt profile");
        insertDebtProfileEntity(item);
    }

    @Transaction
    public void updateDebtProfile(@NonNull DebtProfileEntity item) {
        require(updateDebtProfileEntity(item) == 1, "Debt profile could not be updated");
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract void insertAccounts(@NonNull List<AccountEntity> items);

    @Insert
    public abstract void insertAccount(@NonNull AccountEntity item);

    @Query("SELECT COUNT(*) FROM transaction_entries WHERE accountId = :accountId")
    public abstract int countEntriesForAccount(@NonNull String accountId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract void insertCategories(@NonNull List<CategoryEntity> items);

    @Query("SELECT COUNT(*) FROM accounts")
    protected abstract int countAccounts();

    @Query("SELECT COUNT(*) FROM categories")
    protected abstract int countCategories();

    @Transaction
    public void seedIfEmpty(@NonNull List<AccountEntity> defaultAccounts,
            @NonNull List<CategoryEntity> defaultCategories) {
        if (countAccounts() == 0) insertAccounts(defaultAccounts);
        if (countCategories() == 0) insertCategories(defaultCategories);
    }

    @Insert
    public abstract void insertCategory(@NonNull CategoryEntity item);

    @Insert
    protected abstract void insertTransactionEntity(@NonNull TransactionEntity item);

    @Insert
    protected abstract void insertEntry(@NonNull TransactionEntryEntity item);

    @Insert
    protected abstract void insertEntries(@NonNull List<TransactionEntryEntity> items);

    @Insert
    protected abstract void insertReconciliation(@NonNull ReconciliationEntity item);

    @Transaction
    public void insertTransaction(@NonNull TransactionEntity transaction, @NonNull TransactionEntryEntity entry) {
        validateEntry(transaction, entry);
        insertTransactionEntity(transaction);
        insertEntry(entry);
    }

    @Transaction
    public void insertTransaction(@NonNull TransactionEntity transaction,
            @NonNull List<TransactionEntryEntity> entries) {
        for (TransactionEntryEntity entry : entries) validateEntry(transaction, entry);
        insertTransactionEntity(transaction);
        insertEntries(entries);
    }

    @Query(
        """
            SELECT a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0)
            FROM accounts a
            LEFT JOIN transaction_entries e ON e.accountId = a.id
            LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
                AND e.currencyCode = a.currencyCode
            WHERE a.id = :accountId
            GROUP BY a.id
            """
    )
    public abstract long getAccountBalance(@NonNull String accountId);

    @Query(
        """
            SELECT a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0)
            FROM accounts a
            LEFT JOIN transaction_entries e ON e.accountId = a.id AND e.cleared = 1
            LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
                AND t.localDate <= :statementLocalDate AND e.currencyCode = a.currencyCode
            WHERE a.id = :accountId AND a.archivedAt IS NULL
            GROUP BY a.id
            """
    )
    protected abstract @Nullable Long getClearedAccountBalance(@NonNull String accountId,
            @NonNull String statementLocalDate);

    @Query("SELECT currencyCode FROM accounts WHERE id = :accountId AND archivedAt IS NULL")
    public abstract @Nullable String getActiveAccountCurrency(@NonNull String accountId);

    @Query("SELECT * FROM accounts WHERE id = :accountId AND archivedAt IS NULL")
    public abstract @Nullable AccountEntity getActiveAccount(@NonNull String accountId);

    @Update
    protected abstract int updateAccountEntity(@NonNull AccountEntity account);

    @Query("UPDATE accounts SET archivedAt = :archivedAt, updatedAt = :archivedAt WHERE id = :accountId AND archivedAt IS NULL")
    protected abstract int archiveActiveAccount(@NonNull String accountId, long archivedAt);

    @Query("UPDATE accounts SET archivedAt = NULL, updatedAt = :restoredAt WHERE id = :accountId AND archivedAt IS NOT NULL")
    protected abstract int restoreArchivedAccount(@NonNull String accountId, long restoredAt);

    @Transaction
    public void updateAccount(@NonNull AccountEntity account) {
        AccountEntity previous = requirePresent(getActiveAccount(account.getId()), "Required value was null.");
        if (previous.getOpeningBalanceMinor() != account.getOpeningBalanceMinor()
                || !previous.getCurrencyCode().equals(account.getCurrencyCode())) {
            deleteAccountRate(account.getId());
        }
        require(updateAccountEntity(account) == 1, "Account could not be updated");
    }

    @Transaction
    public void archiveAccount(@NonNull String accountId, long archivedAt) {
        require(archiveActiveAccount(accountId, archivedAt) == 1, "Account is missing or already archived");
    }

    @Transaction
    public void restoreAccount(@NonNull String accountId, long restoredAt) {
        require(restoreArchivedAccount(accountId, restoredAt) == 1, "Account is missing or active");
    }

    @Query("SELECT kind FROM categories WHERE id = :categoryId AND archivedAt IS NULL")
    public abstract @Nullable String getActiveCategoryKind(@NonNull String categoryId);

    @Query(
        """
            SELECT CASE
                WHEN c.archivedAt IS NULL THEN c.id
                WHEN target.archivedAt IS NULL THEN target.id
                ELSE NULL
            END
            FROM categories c
            LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            WHERE c.id = :categoryId
            """
    )
    public abstract @Nullable String getCanonicalActiveCategoryId(@NonNull String categoryId);

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    protected abstract @Nullable CategoryEntity getCategory(@NonNull String categoryId);

    @Query("UPDATE categories SET name = :name WHERE id = :categoryId AND archivedAt IS NULL")
    protected abstract int renameActiveCategory(@NonNull String categoryId, @NonNull String name);

    @Query(
        "UPDATE categories SET archivedAt = :archivedAt, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NULL"
    )
    protected abstract int archiveActiveCategory(@NonNull String categoryId, long archivedAt);

    @Query(
        "UPDATE categories SET archivedAt = :mergedAt, mergedIntoCategoryId = :targetCategoryId " +
            "WHERE id = :sourceCategoryId AND archivedAt IS NULL"
    )
    protected abstract int markCategoryMerged(@NonNull String sourceCategoryId,
            @NonNull String targetCategoryId,
            long mergedAt);

    @Query(
        "UPDATE categories SET archivedAt = NULL, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NOT NULL AND mergedIntoCategoryId IS NULL"
    )
    protected abstract int restoreArchivedCategory(@NonNull String categoryId);

    @Query(
        "UPDATE categories SET archivedAt = NULL, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NOT NULL AND mergedIntoCategoryId IS NOT NULL"
    )
    protected abstract int undoMergedCategory(@NonNull String categoryId);

    @Transaction
    public void renameCategory(@NonNull String categoryId, @NonNull String name) {
        require(renameActiveCategory(categoryId, name) == 1, "Category is missing or archived");
    }

    @Transaction
    public void archiveCategory(@NonNull String categoryId, long archivedAt) {
        require(countMergedChildren(categoryId) == 0, "Undo incoming category merges before archiving this category");
        require(archiveActiveCategory(categoryId, archivedAt) == 1, "Category is missing or already archived");
    }

    @Transaction
    public void mergeCategory(@NonNull String sourceCategoryId, @NonNull String targetCategoryId, long mergedAt) {
        require(!sourceCategoryId.equals(targetCategoryId), "A category cannot be merged into itself");
        CategoryEntity source = requirePresent(getCategory(sourceCategoryId), "Source category is missing");
        CategoryEntity target = requirePresent(getCategory(targetCategoryId), "Target category is missing");
        require(source.getArchivedAt() == null && source.getMergedIntoCategoryId() == null, "Source category is not active");
        require(target.getArchivedAt() == null && target.getMergedIntoCategoryId() == null, "Target category is not active");
        require(source.getKind().equals(target.getKind()), "Categories must have the same type");
        require(countMergedChildren(sourceCategoryId) == 0, "Undo incoming category merges before merging this category");
        require(markCategoryMerged(sourceCategoryId, targetCategoryId, mergedAt) == 1, "Category could not be merged");
    }

    @Transaction
    public void restoreCategory(@NonNull String categoryId) {
        require(restoreArchivedCategory(categoryId) == 1, "Category is missing, active, or was merged");
    }

    @Transaction
    public void undoCategoryMerge(@NonNull String categoryId) {
        require(undoMergedCategory(categoryId) == 1, "Category is not an archived merge");
    }

    @Query("SELECT COUNT(*) FROM categories WHERE mergedIntoCategoryId = :id")
    protected abstract int countMergedChildren(@NonNull String id);

    @Query("SELECT * FROM recurring_schedules WHERE id = :id AND deletedAt IS NULL")
    public abstract @Nullable RecurringScheduleEntity getRecurringForEdit(@NonNull String id);

    @Query("SELECT * FROM recurring_schedules WHERE id = :id AND active = 1 AND deletedAt IS NULL")
    protected abstract @Nullable RecurringScheduleEntity getActiveRecurringForPosting(@NonNull String id);

    @Update
    protected abstract int updateRecurringEntity(@NonNull RecurringScheduleEntity recurring);

    @Query("UPDATE recurring_schedules SET active = 0 WHERE id = :id AND active = 1 AND deletedAt IS NULL")
    protected abstract int pauseActiveRecurring(@NonNull String id);

    @Query("UPDATE recurring_schedules SET active = 1 WHERE id = :id AND active = 0 AND deletedAt IS NULL")
    protected abstract int resumePausedRecurring(@NonNull String id);

    @Query("UPDATE recurring_schedules SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract int softDeleteRecurring(@NonNull String id, long deletedAt);

    @Query("UPDATE recurring_schedules SET deletedAt = NULL, active = 0 WHERE id = :id AND deletedAt IS NOT NULL")
    protected abstract int restoreDeletedRecurring(@NonNull String id);

    @Query(
        """
            UPDATE recurring_schedules SET nextLocalDate = :nextLocalDate, categoryId = :categoryId
            WHERE id = :id AND nextLocalDate = :expectedLocalDate AND active = 1 AND deletedAt IS NULL
            """
    )
    protected abstract int advanceRecurringAfterPosting(@NonNull String id,
            @NonNull String expectedLocalDate,
            @NonNull String nextLocalDate,
            @NonNull String categoryId);

    @Transaction
    public void updateRecurring(@NonNull RecurringScheduleEntity recurring) {
        require(updateRecurringEntity(recurring) == 1, "Recurring schedule could not be updated");
    }

    @Transaction
    public void pauseRecurring(@NonNull String id) {
        require(pauseActiveRecurring(id) == 1, "Recurring schedule is missing, paused, or deleted");
    }

    @Transaction
    public void resumeRecurring(@NonNull String id) {
        require(resumePausedRecurring(id) == 1, "Recurring schedule is missing, active, or deleted");
    }

    @Transaction
    public void deleteRecurring(@NonNull String id, long deletedAt) {
        require(softDeleteRecurring(id, deletedAt) == 1, "Recurring schedule is missing or deleted");
    }

    @Transaction
    public void restoreRecurring(@NonNull String id) {
        require(restoreDeletedRecurring(id) == 1, "Recurring schedule is not available for recovery");
    }

    @Transaction
    public boolean postRecurringOccurrence(@NonNull String recurringId,
            @NonNull String occurrenceLocalDate,
            @NonNull String nextLocalDate,
            @NonNull String transactionId,
            @NonNull String entryId,
            long createdAt) {
        RecurringScheduleEntity recurring = getActiveRecurringForPosting(recurringId);
        if (recurring == null || !recurring.getNextLocalDate().equals(occurrenceLocalDate)) return false;
        if (!recurring.getCurrencyCode().equals(getActiveAccountCurrency(recurring.getAccountId()))) return false;
        String sourceCategoryId = recurring.getCategoryId();
        if (sourceCategoryId == null) return false;
        String categoryId = getCanonicalActiveCategoryId(sourceCategoryId);
        if (categoryId == null || !recurring.getKind().equals(getActiveCategoryKind(categoryId))) return false;
        if (advanceRecurringAfterPosting(recurring.getId(), occurrenceLocalDate, nextLocalDate, categoryId) != 1) {
            return false;
        }

        insertTransactionEntity(new TransactionEntity(transactionId, recurring.getKind(), occurrenceLocalDate,
                recurring.getName(), "Automatically posted recurring transaction", createdAt, createdAt));
        long amountMinor = "INCOME".equals(recurring.getKind()) ? recurring.getAmountMinor() : -recurring.getAmountMinor();
        insertEntry(new TransactionEntryEntity(entryId, transactionId, recurring.getAccountId(), categoryId,
                amountMinor, recurring.getCurrencyCode(), false));
        return true;
    }

    @Transaction
    public void insertReconciliationWithAdjustment(@NonNull ReconciliationEntity reconciliation,
            @Nullable TransactionEntity adjustment,
            @Nullable TransactionEntryEntity entry) {
        if (adjustment != null && entry != null) {
            insertTransactionEntity(adjustment);
            insertEntry(entry);
        }
        insertReconciliation(reconciliation);
    }

    @Query(
        """
            UPDATE transaction_entries SET cleared = :cleared
            WHERE transactionId = :transactionId
              AND EXISTS (SELECT 1 FROM transactions WHERE id = :transactionId AND deletedAt IS NULL)
            """
    )
    protected abstract int updateTransactionCleared(@NonNull String transactionId, boolean cleared);

    @Transaction
    public void setTransactionCleared(@NonNull String transactionId, boolean cleared) {
        require(cleared || countReconciliationReferences(transactionId) == 0,
                "A reconciliation adjustment must remain cleared");
        require(updateTransactionCleared(transactionId, cleared) > 0, "Transaction is missing or deleted");
    }

    @Transaction
    public @Nullable TransactionEntryEntity reconcileAccount(@NonNull String accountId,
            @NonNull String currencyCode,
            @NonNull String statementLocalDate,
            long statementBalanceMinor,
            boolean createAdjustment,
            @NonNull String reconciliationId,
            @NonNull String adjustmentTransactionId,
            @NonNull String adjustmentEntryId,
            long completedAt) {
        AccountEntity account = requirePresent(getActiveAccount(accountId), "Account is missing or archived");
        require(account.getCurrencyCode().equals(currencyCode), "Statement currency does not match the account");
        long calculatedBalance = requirePresent(getClearedAccountBalance(accountId, statementLocalDate),
                "Cleared balance is unavailable");
        long difference = Math.subtractExact(statementBalanceMinor, calculatedBalance);
        require(difference == 0 || createAdjustment,
                "Cleared balance does not match the statement; clear matching transactions or create an adjustment");

        TransactionEntity adjustment = null;
        TransactionEntryEntity entry = null;
        if (difference != 0) {
            adjustment = new TransactionEntity(adjustmentTransactionId, difference > 0 ? "INCOME" : "EXPENSE",
                    statementLocalDate, "Reconciliation adjustment", "", completedAt, completedAt);
            entry = new TransactionEntryEntity(adjustmentEntryId, adjustmentTransactionId, accountId, null,
                    difference, currencyCode, true);
        }
        insertReconciliationWithAdjustment(new ReconciliationEntity(reconciliationId, accountId, statementLocalDate,
                statementBalanceMinor, calculatedBalance, difference, adjustment == null ? null : adjustment.getId(),
                completedAt), adjustment, entry);
        return entry;
    }

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract int softDeleteTransaction(@NonNull String id, long deletedAt);

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id AND deletedAt IS NOT NULL")
    public abstract int restoreTransaction(@NonNull String id, long restoredAt);

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    protected abstract @Nullable TransactionEntity getActiveTransaction(@NonNull String id);

    @Query("SELECT * FROM transaction_entries WHERE transactionId = :transactionId ORDER BY rowid")
    protected abstract @NonNull List<TransactionEntryEntity> getTransactionEntries(@NonNull String transactionId);

    @Query("SELECT COUNT(*) FROM reconciliations WHERE adjustmentTransactionId = :transactionId")
    protected abstract int countReconciliationReferences(@NonNull String transactionId);

    @Transaction
    public int deleteTransaction(@NonNull String id, long deletedAt) {
        require(countReconciliationReferences(id) == 0, "Reconciliation adjustments cannot be deleted");
        return softDeleteTransaction(id, deletedAt);
    }

    @Update
    protected abstract int updateTransactionEntity(@NonNull TransactionEntity transaction);

    @Update
    protected abstract int updateTransactionEntries(@NonNull List<TransactionEntryEntity> entries);

    @Transaction
    public @NonNull StoredTransaction getTransactionForEdit(@NonNull String id) {
        TransactionEntity transaction = requirePresent(getActiveTransaction(id), "Transaction is missing or deleted");
        return new StoredTransaction(transaction, getTransactionEntries(id), countReconciliationReferences(id) > 0);
    }

    @Transaction
    public void updateTransaction(@NonNull TransactionEntity transaction,
            @NonNull List<TransactionEntryEntity> entries) {
        requirePresent(getActiveTransaction(transaction.getId()), "Transaction is missing or deleted");
        for (TransactionEntryEntity entry : entries) validateEntry(transaction, entry);

        List<TransactionExchangeRateEntity> preservedRates = new ArrayList<>();
        for (TransactionEntryEntity entry : entries) {
            TransactionExchangeRateEntity rate = getTransactionExchangeRate(entry.getId());
            if (rate == null || !"AVAILABLE".equals(rate.getStatus()) || rate.getRateMicros() == null
                    || !rate.getRateLocalDate().equals(transaction.getLocalDate())
                    || !rate.getBaseCurrencyCode().equals(entry.getCurrencyCode())) {
                continue;
            }
            long convertedAmount = CurrencyMath.convertCurrencyMinor(entry.getAmountMinor(), entry.getCurrencyCode(),
                    rate.getQuoteCurrencyCode(), rate.getRateMicros());
            preservedRates.add(rate.withConvertedAmount(convertedAmount));
        }
        deleteTransactionRates(transaction.getId());
        require(updateTransactionEntity(transaction) == 1, "Transaction could not be updated");
        require(!entries.isEmpty() && updateTransactionEntries(entries) == entries.size(),
                "Transaction entries could not be updated");
        for (TransactionExchangeRateEntity rate : preservedRates) upsertTransactionExchangeRate(rate);
    }

    @Query(
        """
            SELECT COUNT(*) FROM transactions t
            JOIN transaction_entries e ON e.transactionId = t.id
            WHERE t.deletedAt IS NULL AND t.localDate = :localDate AND t.payee = :payee
              AND e.accountId = :accountId AND e.categoryId = :categoryId
              AND e.amountMinor = :amountMinor AND e.currencyCode = :currencyCode
            """
    )
    public abstract int countMatchingTransaction(@NonNull String localDate,
            @NonNull String payee,
            @NonNull String accountId,
            @NonNull String categoryId,
            long amountMinor,
            @NonNull String currencyCode);

    @Transaction
    public @NonNull TransactionImportWriteResult importTransactions(@NonNull List<TransactionImportItem> items) {
        int imported = 0;
        int duplicates = 0;
        for (TransactionImportItem item : items) {
            TransactionEntity transaction = item.getTransaction();
            TransactionEntryEntity entry = item.getEntry();
            if (countMatchingTransaction(transaction.getLocalDate(), transaction.getPayee(), entry.getAccountId(),
                    requirePresent(entry.getCategoryId(), "Required value was null."),
                    entry.getAmountMinor(), entry.getCurrencyCode()) > 0) {
                duplicates++;
            } else {
                validateEntry(transaction, entry);
                insertTransactionEntity(transaction);
                insertEntry(entry);
                imported++;
            }
        }
        return new TransactionImportWriteResult(imported, duplicates);
    }

    @Query("SELECT * FROM accounts ORDER BY id")
    protected abstract @NonNull List<AccountEntity> getAllAccountsForBackup();

    @Query("SELECT * FROM categories ORDER BY id")
    protected abstract @NonNull List<CategoryEntity> getAllCategoriesForBackup();

    @Query("SELECT * FROM transactions ORDER BY id")
    protected abstract @NonNull List<TransactionEntity> getAllTransactionsForBackup();

    @Query("SELECT * FROM transaction_entries ORDER BY id")
    protected abstract @NonNull List<TransactionEntryEntity> getAllTransactionEntriesForBackup();

    @Query("SELECT * FROM reconciliations ORDER BY id")
    protected abstract @NonNull List<ReconciliationEntity> getAllReconciliationsForBackup();

    @Query("SELECT * FROM budget_allocations ORDER BY id")
    protected abstract @NonNull List<BudgetAllocationEntity> getAllBudgetAllocationsForBackup();

    @Query("SELECT * FROM goals ORDER BY id")
    protected abstract @NonNull List<GoalEntity> getAllGoalsForBackup();

    @Query("SELECT * FROM goal_contributions ORDER BY id")
    protected abstract @NonNull List<GoalContributionEntity> getAllGoalContributionsForBackup();

    @Query("SELECT * FROM recurring_schedules ORDER BY id")
    protected abstract @NonNull List<RecurringScheduleEntity> getAllRecurringSchedulesForBackup();

    @Query("SELECT * FROM debt_profiles ORDER BY id")
    protected abstract @NonNull List<DebtProfileEntity> getAllDebtProfilesForBackup();

    @Query("SELECT * FROM receipt_attachments ORDER BY id")
    protected abstract @NonNull List<ReceiptAttachmentEntity> getAllReceiptAttachmentsForBackup();

    @Query("SELECT * FROM transaction_exchange_rates ORDER BY id")
    protected abstract @NonNull List<TransactionExchangeRateEntity> getAllTransactionExchangeRatesForBackup();

    @Query("DELETE FROM reconciliations")
    protected abstract void deleteAllReconciliations();

    @Query("DELETE FROM budget_allocations")
    protected abstract void deleteAllBudgetAllocations();

    @Query("DELETE FROM recurring_schedules")
    protected abstract void deleteAllRecurringSchedules();

    @Query("DELETE FROM debt_profiles")
    protected abstract void deleteAllDebtProfiles();

    @Query("DELETE FROM receipt_attachments")
    protected abstract void deleteAllReceiptAttachments();

    @Query("DELETE FROM transaction_exchange_rates")
    protected abstract void deleteAllTransactionExchangeRates();

    @Query("DELETE FROM transaction_entries")
    protected abstract void deleteAllTransactionEntries();

    @Query("DELETE FROM transactions")
    protected abstract void deleteAllTransactions();

    @Query("DELETE FROM goals")
    protected abstract void deleteAllGoals();

    @Query("DELETE FROM goal_contributions")
    protected abstract void deleteAllGoalContributions();

    @Query("DELETE FROM categories")
    protected abstract void deleteAllCategories();

    @Query("DELETE FROM accounts")
    protected abstract void deleteAllAccounts();

    @Insert
    protected abstract void restoreAccounts(@NonNull List<AccountEntity> items);

    @Insert
    protected abstract void restoreCategories(@NonNull List<CategoryEntity> items);

    @Insert
    protected abstract void restoreTransactions(@NonNull List<TransactionEntity> items);

    @Insert
    protected abstract void restoreTransactionEntries(@NonNull List<TransactionEntryEntity> items);

    @Insert
    protected abstract void restoreReconciliations(@NonNull List<ReconciliationEntity> items);

    @Insert
    protected abstract void restoreBudgetAllocations(@NonNull List<BudgetAllocationEntity> items);

    @Insert
    protected abstract void restoreGoals(@NonNull List<GoalEntity> items);

    @Insert
    protected abstract void restoreGoalContributions(@NonNull List<GoalContributionEntity> items);

    @Insert
    protected abstract void restoreRecurringSchedules(@NonNull List<RecurringScheduleEntity> items);

    @Insert
    protected abstract void restoreDebtProfiles(@NonNull List<DebtProfileEntity> items);

    @Insert
    protected abstract void restoreReceiptAttachments(@NonNull List<ReceiptAttachmentEntity> items);

    @Insert
    protected abstract void restoreTransactionExchangeRates(@NonNull List<TransactionExchangeRateEntity> items);

    @Transaction
    public @NonNull DatabaseSnapshot exportSnapshot() {
        return new DatabaseSnapshot(getAllAccountsForBackup(), getAllAccountExchangeRates(),
                getAllCategoriesForBackup(), getAllTransactionsForBackup(), getAllTransactionEntriesForBackup(),
                getAllReconciliationsForBackup(), getAllBudgetAllocationsForBackup(), getAllGoalsForBackup(),
                getAllGoalContributionsForBackup(), getAllRecurringSchedulesForBackup(), getAllDebtProfilesForBackup(),
                getAllReceiptAttachmentsForBackup(), getAllTransactionExchangeRatesForBackup());
    }

    @Transaction
    public void replaceWithSnapshot(@NonNull DatabaseSnapshot snapshot) {
        deleteAllAccountExchangeRates();
        deleteAllReconciliations();
        deleteAllBudgetAllocations();
        deleteAllRecurringSchedules();
        deleteAllDebtProfiles();
        deleteAllReceiptAttachments();
        deleteAllTransactionExchangeRates();
        deleteAllTransactionEntries();
        deleteAllTransactions();
        deleteAllGoalContributions();
        deleteAllGoals();
        deleteAllCategories();
        deleteAllAccounts();

        restoreAccounts(snapshot.getAccounts());
        for (AccountExchangeRateEntity rate : snapshot.getAccountExchangeRates()) upsertAccountExchangeRate(rate);
        restoreCategories(snapshot.getCategories());
        restoreTransactions(snapshot.getTransactions());
        restoreTransactionEntries(snapshot.getTransactionEntries());
        restoreReconciliations(snapshot.getReconciliations());
        restoreBudgetAllocations(snapshot.getBudgetAllocations());
        restoreGoals(snapshot.getGoals());
        restoreGoalContributions(snapshot.getGoalContributions());
        restoreRecurringSchedules(snapshot.getRecurringSchedules());
        restoreDebtProfiles(snapshot.getDebtProfiles());
        restoreReceiptAttachments(snapshot.getReceiptAttachments());
        restoreTransactionExchangeRates(snapshot.getTransactionExchangeRates());
    }

    @Query("DELETE FROM transaction_exchange_rates WHERE transactionId = :id")
    protected abstract void deleteTransactionRates(@NonNull String id);

    @Query("DELETE FROM account_exchange_rates WHERE accountId = :id")
    protected abstract void deleteAccountRate(@NonNull String id);

    @Query("""
            SELECT COALESCE(target.name, c.name) AS categoryName,
            -SUM(CASE WHEN e.currencyCode = :currency THEN e.amountMinor ELSE fx.convertedAmountMinor END) AS amountMinor
            FROM transaction_entries e JOIN transactions t ON t.id = e.transactionId
            JOIN accounts a ON a.id = e.accountId
            LEFT JOIN categories c ON c.id = e.categoryId LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            LEFT JOIN transaction_exchange_rates fx ON fx.entryId = e.id AND fx.status = 'AVAILABLE'
                AND fx.quoteCurrencyCode = :currency AND fx.baseCurrencyCode = e.currencyCode
            WHERE t.deletedAt IS NULL AND t.kind = 'EXPENSE' AND a.archivedAt IS NULL
                AND (:accountId IS NULL OR e.accountId = :accountId)
                AND (e.currencyCode = :currency OR fx.id IS NOT NULL)
            GROUP BY COALESCE(target.id, c.id) ORDER BY amountMinor DESC
            """)
    public abstract @NonNull Flow<List<CategorySpendingRow>> observeCategorySpending(@Nullable String accountId,
            @NonNull String currency);

    @Query("""
            SELECT substr(t.localDate, 1, 7) || '-01' AS monthStart,
            COALESCE(-SUM(CASE WHEN e.currencyCode = :currency THEN e.amountMinor ELSE fx.convertedAmountMinor END), 0) AS amountMinor
            FROM transaction_entries e JOIN transactions t ON t.id = e.transactionId
            JOIN categories c ON c.id = e.categoryId LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
            LEFT JOIN transaction_exchange_rates fx ON fx.entryId = e.id AND fx.status = 'AVAILABLE'
                AND fx.quoteCurrencyCode = :currency AND fx.baseCurrencyCode = e.currencyCode
            WHERE t.deletedAt IS NULL AND t.kind = 'EXPENSE' AND COALESCE(target.id, c.id) = :categoryId
                AND t.localDate >= :start AND t.localDate < :end
            GROUP BY substr(t.localDate, 1, 7) ORDER BY monthStart
            """)
    public abstract @NonNull Flow<List<MonthlyExpenseRow>> observeMonthlyExpenses(@NonNull String categoryId,
            @NonNull String currency,
            @NonNull String start,
            @NonNull String end);

    private void validateEntry(@NonNull TransactionEntity transaction, @NonNull TransactionEntryEntity entry) {
        require(entry.getCurrencyCode().equals(getActiveAccountCurrency(entry.getAccountId())),
                "Account is missing, archived or has a different currency");
        require(entry.getTransactionId().equals(transaction.getId()), "Failed requirement.");
        String categoryId = entry.getCategoryId();
        if (categoryId != null) {
            require(transaction.getKind().equals(getActiveCategoryKind(categoryId)),
                    "Category is missing, archived or incompatible");
        }
    }

    @Query("SELECT * FROM accounts WHERE archivedAt IS NULL")
    public abstract @NonNull List<AccountEntity> getActiveAccountsForRates();

    @Query("SELECT * FROM account_exchange_rates ORDER BY accountId")
    public abstract @NonNull List<AccountExchangeRateEntity> getAllAccountExchangeRates();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertAccountExchangeRate(@NonNull AccountExchangeRateEntity item);

    @Query("DELETE FROM account_exchange_rates")
    protected abstract void deleteAllAccountExchangeRates();

    @Transaction
    public boolean storeRateIfUnchanged(@NonNull TransactionExchangeRateEntity rate,
            @NonNull TransactionEntryEntity expected,
            @NonNull String localDate) {
        TransactionEntity transaction = getActiveTransaction(rate.getTransactionId());
        if (transaction == null || !transaction.getLocalDate().equals(localDate)
                || !getTransactionEntries(transaction.getId()).contains(expected)) {
            return false;
        }
        upsertTransactionExchangeRate(rate);
        return true;
    }

    @Transaction
    public void storeOpeningRateIfUnchanged(@NonNull AccountExchangeRateEntity rate) {
        AccountEntity account = getActiveAccount(rate.getAccountId());
        if (account != null && account.getCurrencyCode().equals(rate.getBaseCurrencyCode())
                && account.getOpeningBalanceMinor() == rate.getOpeningBalanceMinor()) {
            upsertAccountExchangeRate(rate);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static <T> T requirePresent(T value, String message) {
        require(value != null, message);
        return value;
    }
}
