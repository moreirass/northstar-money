package com.northstar.money.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FinanceDao {
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
    abstract fun observeAccounts(): Flow<List<AccountBalanceRow>>

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
        """,
    )
    abstract fun observeArchivedAccounts(): Flow<List<AccountBalanceRow>>

    @Query("SELECT * FROM categories WHERE archivedAt IS NULL ORDER BY kind, sortOrder, name")
    abstract fun observeCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.id, c.name, c.kind, c.mergedIntoCategoryId,
               target.name AS mergedIntoCategoryName
        FROM categories c
        LEFT JOIN categories target ON target.id = c.mergedIntoCategoryId
        WHERE c.archivedAt IS NOT NULL
        ORDER BY c.kind, c.name
        """,
    )
    abstract fun observeArchivedCategories(): Flow<List<ArchivedCategoryRow>>

    @Query(
        """
        SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
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
        WHERE t.deletedAt IS NULL AND e.rowid = (
            SELECT MIN(e2.rowid) FROM transaction_entries e2 WHERE e2.transactionId = t.id
        )
        ORDER BY t.localDate DESC, t.createdAt DESC
        """
    )
    abstract fun observeTransactions(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id, t.payee, t.kind, t.localDate, t.createdAt, e.amountMinor, e.currencyCode,
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
        WHERE t.deletedAt IS NOT NULL AND e.rowid = (
            SELECT MIN(e2.rowid) FROM transaction_entries e2 WHERE e2.transactionId = t.id
        )
        ORDER BY t.deletedAt DESC
        """
    )
    abstract fun observeDeletedTransactions(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(openingBalanceMinor) FROM accounts
                    WHERE archivedAt IS NULL AND currencyCode = :baseCurrencyCode), 0)
            + COALESCE(SUM(e.amountMinor), 0) AS balanceMinor,
          COALESCE(SUM(CASE WHEN t.kind = 'INCOME' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS incomeMinor,
          COALESCE(-SUM(CASE WHEN t.kind = 'EXPENSE' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transaction_entries e
        JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
        JOIN accounts a ON a.id = e.accountId
        WHERE a.archivedAt IS NULL AND a.currencyCode = :baseCurrencyCode
          AND e.currencyCode = :baseCurrencyCode
        """
    )
    abstract fun observeSummary(monthStart: String, baseCurrencyCode: String): Flow<SummaryRow>

    @Query(
        """
        WITH budget_values AS (
            SELECT c.id AS categoryId, c.name AS categoryName, c.sortOrder,
                   COALESCE((
                       SELECT SUM(currentAllocation.plannedMinor)
                       FROM budget_allocations currentAllocation
                       WHERE currentAllocation.categoryId = c.id
                         AND currentAllocation.monthStart = :monthStart
                   ), 0) AS allocatedMinor,
                   CASE WHEN EXISTS (
                       SELECT 1
                       FROM budget_allocations earlierAllocation
                       JOIN categories earlierCategory ON earlierCategory.id = earlierAllocation.categoryId
                       WHERE COALESCE(earlierCategory.mergedIntoCategoryId, earlierCategory.id) = c.id
                         AND earlierAllocation.monthStart < :monthStart
                   ) THEN
                       COALESCE((
                           SELECT SUM(priorAllocation.plannedMinor)
                           FROM budget_allocations priorAllocation
                           JOIN categories allocatedCategory ON allocatedCategory.id = priorAllocation.categoryId
                           WHERE COALESCE(allocatedCategory.mergedIntoCategoryId, allocatedCategory.id) = c.id
                             AND priorAllocation.monthStart < :monthStart
                       ), 0) - COALESCE((
                           SELECT -SUM(entry.amountMinor)
                           FROM transaction_entries entry
                           JOIN transactions transactionRecord ON transactionRecord.id = entry.transactionId
                           JOIN categories spentCategory ON spentCategory.id = entry.categoryId
                           WHERE COALESCE(spentCategory.mergedIntoCategoryId, spentCategory.id) = c.id
                             AND entry.currencyCode = :baseCurrencyCode
                             AND transactionRecord.kind = 'EXPENSE' AND transactionRecord.deletedAt IS NULL
                             AND transactionRecord.localDate >= (
                                 SELECT MIN(firstAllocation.monthStart)
                                 FROM budget_allocations firstAllocation
                                 JOIN categories firstCategory ON firstCategory.id = firstAllocation.categoryId
                                 WHERE COALESCE(firstCategory.mergedIntoCategoryId, firstCategory.id) = c.id
                             )
                             AND transactionRecord.localDate < :monthStart
                       ), 0)
                   ELSE 0 END AS rolloverMinor,
                   COALESCE((
                       SELECT -SUM(entry.amountMinor)
                       FROM transaction_entries entry
                       JOIN transactions transactionRecord ON transactionRecord.id = entry.transactionId
                       JOIN categories spentCategory ON spentCategory.id = entry.categoryId
                       WHERE COALESCE(spentCategory.mergedIntoCategoryId, spentCategory.id) = c.id
                         AND entry.currencyCode = :baseCurrencyCode
                         AND transactionRecord.kind = 'EXPENSE' AND transactionRecord.deletedAt IS NULL
                         AND transactionRecord.localDate >= :monthStart
                         AND transactionRecord.localDate < :nextMonthStart
                   ), 0) AS spentMinor
            FROM categories c
            WHERE c.kind = 'EXPENSE' AND c.archivedAt IS NULL
        )
        SELECT categoryId, categoryName, allocatedMinor, rolloverMinor,
               allocatedMinor + rolloverMinor AS plannedMinor, spentMinor
        FROM budget_values
        ORDER BY sortOrder, categoryName
        """
    )
    abstract fun observeBudgets(
        monthStart: String,
        nextMonthStart: String,
        baseCurrencyCode: String,
    ): Flow<List<BudgetRow>>

    @Query(
        """
        SELECT g.id, g.name, g.targetMinor,
               g.savedMinor + COALESCE(SUM(CASE WHEN contribution.deletedAt IS NULL THEN contribution.amountMinor ELSE 0 END), 0) AS savedMinor,
               g.currencyCode, g.targetLocalDate, g.status, g.createdAt
        FROM goals g
        LEFT JOIN goal_contributions contribution ON contribution.goalId = g.id
        GROUP BY g.id
        ORDER BY g.createdAt
        """,
    )
    abstract fun observeGoals(): Flow<List<GoalEntity>>

    @Query(
        """
        SELECT contribution.id, contribution.goalId, goal.name AS goalName,
               contribution.amountMinor, goal.currencyCode,
               contribution.localDate, contribution.note
        FROM goal_contributions contribution
        JOIN goals goal ON goal.id = contribution.goalId
        WHERE contribution.deletedAt IS NULL
        ORDER BY contribution.localDate DESC, contribution.createdAt DESC
        """,
    )
    abstract fun observeGoalContributions(): Flow<List<GoalContributionRow>>

    @Query(
        """
        SELECT contribution.id, contribution.goalId, goal.name AS goalName,
               contribution.amountMinor, goal.currencyCode,
               contribution.localDate, contribution.note
        FROM goal_contributions contribution
        JOIN goals goal ON goal.id = contribution.goalId
        WHERE contribution.deletedAt IS NOT NULL
        ORDER BY contribution.deletedAt DESC
        """,
    )
    abstract fun observeDeletedGoalContributions(): Flow<List<GoalContributionRow>>

    @Query(
        """
        SELECT r.* FROM recurring_schedules r
        JOIN accounts a ON a.id = r.accountId
        WHERE r.active = 1 AND r.deletedAt IS NULL AND a.archivedAt IS NULL
        ORDER BY r.nextLocalDate
        """,
    )
    abstract fun observeRecurring(): Flow<List<RecurringScheduleEntity>>

    @Query("SELECT * FROM recurring_schedules WHERE active = 0 AND deletedAt IS NULL ORDER BY nextLocalDate")
    abstract fun observePausedRecurring(): Flow<List<RecurringScheduleEntity>>

    @Query("SELECT * FROM recurring_schedules WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    abstract fun observeDeletedRecurring(): Flow<List<RecurringScheduleEntity>>

    @Query(
        """
        SELECT recurring.* FROM recurring_schedules recurring
        JOIN accounts account ON account.id = recurring.accountId
        WHERE recurring.active = 1 AND recurring.deletedAt IS NULL
          AND recurring.nextLocalDate <= :throughLocalDate AND account.archivedAt IS NULL
        ORDER BY recurring.nextLocalDate, recurring.id
        """,
    )
    abstract suspend fun getDueRecurringSchedules(throughLocalDate: String): List<RecurringScheduleEntity>

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
    abstract fun observeDebts(): Flow<List<DebtProfileRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBudget(item: BudgetAllocationEntity)

    @Insert
    abstract suspend fun insertGoal(item: GoalEntity)

    @Insert
    protected abstract suspend fun insertGoalContributionEntity(item: GoalContributionEntity)

    @Transaction
    open suspend fun insertGoalWithInitialContribution(
        goal: GoalEntity,
        initialContribution: GoalContributionEntity?,
    ) {
        insertGoal(goal)
        initialContribution?.let { insertGoalContributionEntity(it) }
    }

    @Query("SELECT * FROM goals WHERE id = :id")
    abstract suspend fun getGoal(id: String): GoalEntity?

    @Update
    protected abstract suspend fun updateGoalEntity(goal: GoalEntity): Int

    @Query("SELECT * FROM goal_contributions WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getGoalContributionForEdit(id: String): GoalContributionEntity?

    @Update
    protected abstract suspend fun updateGoalContributionEntity(contribution: GoalContributionEntity): Int

    @Query("UPDATE goal_contributions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract suspend fun softDeleteGoalContribution(id: String, deletedAt: Long): Int

    @Query("UPDATE goal_contributions SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id AND deletedAt IS NOT NULL")
    protected abstract suspend fun restoreDeletedGoalContribution(id: String, restoredAt: Long): Int

    @Transaction
    open suspend fun updateGoal(goal: GoalEntity) {
        require(updateGoalEntity(goal) == 1) { "Savings goal could not be updated" }
    }

    @Transaction
    open suspend fun insertGoalContribution(contribution: GoalContributionEntity) {
        require(getGoal(contribution.goalId) != null) { "Savings goal is missing" }
        insertGoalContributionEntity(contribution)
    }

    @Transaction
    open suspend fun updateGoalContribution(contribution: GoalContributionEntity) {
        require(updateGoalContributionEntity(contribution) == 1) { "Contribution could not be updated" }
    }

    @Transaction
    open suspend fun deleteGoalContribution(id: String, deletedAt: Long) {
        require(softDeleteGoalContribution(id, deletedAt) == 1) { "Contribution is missing or deleted" }
    }

    @Transaction
    open suspend fun restoreGoalContribution(id: String, restoredAt: Long) {
        require(restoreDeletedGoalContribution(id, restoredAt) == 1) { "Contribution is not available for recovery" }
    }

    @Insert
    abstract suspend fun insertRecurring(item: RecurringScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDebt(item: DebtProfileEntity)

    @Query("SELECT * FROM debt_profiles WHERE id = :id")
    abstract suspend fun getDebtProfile(id: String): DebtProfileEntity?

    @Query("SELECT COUNT(*) FROM debt_profiles WHERE accountId = :accountId")
    protected abstract suspend fun countDebtProfilesForAccount(accountId: String): Int

    @Insert
    protected abstract suspend fun insertDebtProfileEntity(item: DebtProfileEntity)

    @Update
    protected abstract suspend fun updateDebtProfileEntity(item: DebtProfileEntity): Int

    @Transaction
    open suspend fun insertDebtProfile(item: DebtProfileEntity) {
        require(countDebtProfilesForAccount(item.accountId) == 0) { "Account already has a debt profile" }
        insertDebtProfileEntity(item)
    }

    @Transaction
    open suspend fun updateDebtProfile(item: DebtProfileEntity) {
        require(updateDebtProfileEntity(item) == 1) { "Debt profile could not be updated" }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert
    abstract suspend fun insertAccount(item: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCategories(items: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM accounts")
    protected abstract suspend fun countAccounts(): Int

    @Query("SELECT COUNT(*) FROM categories")
    protected abstract suspend fun countCategories(): Int

    @Transaction
    open suspend fun seedIfEmpty(
        defaultAccounts: List<AccountEntity>,
        defaultCategories: List<CategoryEntity>,
    ) {
        if (countAccounts() == 0) insertAccounts(defaultAccounts)
        if (countCategories() == 0) insertCategories(defaultCategories)
    }

    @Insert
    abstract suspend fun insertCategory(item: CategoryEntity)

    @Insert
    protected abstract suspend fun insertTransactionEntity(item: TransactionEntity)

    @Insert
    protected abstract suspend fun insertEntry(item: TransactionEntryEntity)

    @Insert
    protected abstract suspend fun insertEntries(items: List<TransactionEntryEntity>)

    @Insert
    protected abstract suspend fun insertReconciliation(item: ReconciliationEntity)

    @Transaction
    open suspend fun insertTransaction(
        transaction: TransactionEntity,
        entry: TransactionEntryEntity,
    ) {
        insertTransactionEntity(transaction)
        insertEntry(entry)
    }

    @Transaction
    open suspend fun insertTransaction(
        transaction: TransactionEntity,
        entries: List<TransactionEntryEntity>,
    ) {
        insertTransactionEntity(transaction)
        insertEntries(entries)
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
    abstract suspend fun getAccountBalance(accountId: String): Long

    @Query(
        """
        SELECT a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0)
        FROM accounts a
        LEFT JOIN transaction_entries e ON e.accountId = a.id AND e.cleared = 1
        LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
            AND t.localDate <= :statementLocalDate AND e.currencyCode = a.currencyCode
        WHERE a.id = :accountId AND a.archivedAt IS NULL
        GROUP BY a.id
        """,
    )
    protected abstract suspend fun getClearedAccountBalance(
        accountId: String,
        statementLocalDate: String,
    ): Long?

    @Query("SELECT currencyCode FROM accounts WHERE id = :accountId AND archivedAt IS NULL")
    abstract suspend fun getActiveAccountCurrency(accountId: String): String?

    @Query("SELECT * FROM accounts WHERE id = :accountId AND archivedAt IS NULL")
    abstract suspend fun getActiveAccount(accountId: String): AccountEntity?

    @Update
    protected abstract suspend fun updateAccountEntity(account: AccountEntity): Int

    @Query("UPDATE accounts SET archivedAt = :archivedAt, updatedAt = :archivedAt WHERE id = :accountId AND archivedAt IS NULL")
    protected abstract suspend fun archiveActiveAccount(accountId: String, archivedAt: Long): Int

    @Query("UPDATE accounts SET archivedAt = NULL, updatedAt = :restoredAt WHERE id = :accountId AND archivedAt IS NOT NULL")
    protected abstract suspend fun restoreArchivedAccount(accountId: String, restoredAt: Long): Int

    @Transaction
    open suspend fun updateAccount(account: AccountEntity) {
        require(updateAccountEntity(account) == 1) { "Account could not be updated" }
    }

    @Transaction
    open suspend fun archiveAccount(accountId: String, archivedAt: Long) {
        require(archiveActiveAccount(accountId, archivedAt) == 1) { "Account is missing or already archived" }
    }

    @Transaction
    open suspend fun restoreAccount(accountId: String, restoredAt: Long) {
        require(restoreArchivedAccount(accountId, restoredAt) == 1) { "Account is missing or active" }
    }

    @Query("SELECT kind FROM categories WHERE id = :categoryId AND archivedAt IS NULL")
    abstract suspend fun getActiveCategoryKind(categoryId: String): String?

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
        """,
    )
    abstract suspend fun getCanonicalActiveCategoryId(categoryId: String): String?

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    protected abstract suspend fun getCategory(categoryId: String): CategoryEntity?

    @Query("UPDATE categories SET name = :name WHERE id = :categoryId AND archivedAt IS NULL")
    protected abstract suspend fun renameActiveCategory(categoryId: String, name: String): Int

    @Query(
        "UPDATE categories SET archivedAt = :archivedAt, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NULL",
    )
    protected abstract suspend fun archiveActiveCategory(categoryId: String, archivedAt: Long): Int

    @Query(
        "UPDATE categories SET archivedAt = :mergedAt, mergedIntoCategoryId = :targetCategoryId " +
            "WHERE id = :sourceCategoryId AND archivedAt IS NULL",
    )
    protected abstract suspend fun markCategoryMerged(
        sourceCategoryId: String,
        targetCategoryId: String,
        mergedAt: Long,
    ): Int

    @Query(
        "UPDATE categories SET archivedAt = NULL, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NOT NULL AND mergedIntoCategoryId IS NULL",
    )
    protected abstract suspend fun restoreArchivedCategory(categoryId: String): Int

    @Query(
        "UPDATE categories SET archivedAt = NULL, mergedIntoCategoryId = NULL " +
            "WHERE id = :categoryId AND archivedAt IS NOT NULL AND mergedIntoCategoryId IS NOT NULL",
    )
    protected abstract suspend fun undoMergedCategory(categoryId: String): Int

    @Transaction
    open suspend fun renameCategory(categoryId: String, name: String) {
        require(renameActiveCategory(categoryId, name) == 1) { "Category is missing or archived" }
    }

    @Transaction
    open suspend fun archiveCategory(categoryId: String, archivedAt: Long) {
        require(archiveActiveCategory(categoryId, archivedAt) == 1) { "Category is missing or already archived" }
    }

    @Transaction
    open suspend fun mergeCategory(sourceCategoryId: String, targetCategoryId: String, mergedAt: Long) {
        require(sourceCategoryId != targetCategoryId) { "A category cannot be merged into itself" }
        val source = requireNotNull(getCategory(sourceCategoryId)) { "Source category is missing" }
        val target = requireNotNull(getCategory(targetCategoryId)) { "Target category is missing" }
        require(source.archivedAt == null && source.mergedIntoCategoryId == null) { "Source category is not active" }
        require(target.archivedAt == null && target.mergedIntoCategoryId == null) { "Target category is not active" }
        require(source.kind == target.kind) { "Categories must have the same type" }
        require(markCategoryMerged(sourceCategoryId, targetCategoryId, mergedAt) == 1) { "Category could not be merged" }
    }

    @Transaction
    open suspend fun restoreCategory(categoryId: String) {
        require(restoreArchivedCategory(categoryId) == 1) { "Category is missing, active, or was merged" }
    }

    @Transaction
    open suspend fun undoCategoryMerge(categoryId: String) {
        require(undoMergedCategory(categoryId) == 1) { "Category is not an archived merge" }
    }

    @Query("SELECT * FROM recurring_schedules WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getRecurringForEdit(id: String): RecurringScheduleEntity?

    @Query("SELECT * FROM recurring_schedules WHERE id = :id AND active = 1 AND deletedAt IS NULL")
    protected abstract suspend fun getActiveRecurringForPosting(id: String): RecurringScheduleEntity?

    @Update
    protected abstract suspend fun updateRecurringEntity(recurring: RecurringScheduleEntity): Int

    @Query("UPDATE recurring_schedules SET active = 0 WHERE id = :id AND active = 1 AND deletedAt IS NULL")
    protected abstract suspend fun pauseActiveRecurring(id: String): Int

    @Query("UPDATE recurring_schedules SET active = 1 WHERE id = :id AND active = 0 AND deletedAt IS NULL")
    protected abstract suspend fun resumePausedRecurring(id: String): Int

    @Query("UPDATE recurring_schedules SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract suspend fun softDeleteRecurring(id: String, deletedAt: Long): Int

    @Query("UPDATE recurring_schedules SET deletedAt = NULL, active = 0 WHERE id = :id AND deletedAt IS NOT NULL")
    protected abstract suspend fun restoreDeletedRecurring(id: String): Int

    @Query(
        """
        UPDATE recurring_schedules SET nextLocalDate = :nextLocalDate, categoryId = :categoryId
        WHERE id = :id AND nextLocalDate = :expectedLocalDate AND active = 1 AND deletedAt IS NULL
        """,
    )
    protected abstract suspend fun advanceRecurringAfterPosting(
        id: String,
        expectedLocalDate: String,
        nextLocalDate: String,
        categoryId: String,
    ): Int

    @Transaction
    open suspend fun updateRecurring(recurring: RecurringScheduleEntity) {
        require(updateRecurringEntity(recurring) == 1) { "Recurring schedule could not be updated" }
    }

    @Transaction
    open suspend fun pauseRecurring(id: String) {
        require(pauseActiveRecurring(id) == 1) { "Recurring schedule is missing, paused, or deleted" }
    }

    @Transaction
    open suspend fun resumeRecurring(id: String) {
        require(resumePausedRecurring(id) == 1) { "Recurring schedule is missing, active, or deleted" }
    }

    @Transaction
    open suspend fun deleteRecurring(id: String, deletedAt: Long) {
        require(softDeleteRecurring(id, deletedAt) == 1) { "Recurring schedule is missing or deleted" }
    }

    @Transaction
    open suspend fun restoreRecurring(id: String) {
        require(restoreDeletedRecurring(id) == 1) { "Recurring schedule is not available for recovery" }
    }

    @Transaction
    open suspend fun postRecurringOccurrence(
        recurringId: String,
        occurrenceLocalDate: String,
        nextLocalDate: String,
        transactionId: String,
        entryId: String,
        createdAt: Long,
    ): Boolean {
        val recurring = getActiveRecurringForPosting(recurringId) ?: return false
        if (recurring.nextLocalDate != occurrenceLocalDate) return false
        val accountCurrency = getActiveAccountCurrency(recurring.accountId) ?: return false
        if (accountCurrency != recurring.currencyCode) return false
        val sourceCategoryId = recurring.categoryId ?: return false
        val categoryId = getCanonicalActiveCategoryId(sourceCategoryId) ?: return false
        if (getActiveCategoryKind(categoryId) != recurring.kind) return false
        if (
            advanceRecurringAfterPosting(
                recurring.id,
                occurrenceLocalDate,
                nextLocalDate,
                categoryId,
            ) != 1
        ) return false

        insertTransactionEntity(
            TransactionEntity(
                id = transactionId,
                kind = recurring.kind,
                localDate = occurrenceLocalDate,
                payee = recurring.name,
                note = "Automatically posted recurring transaction",
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
        insertEntry(
            TransactionEntryEntity(
                id = entryId,
                transactionId = transactionId,
                accountId = recurring.accountId,
                categoryId = categoryId,
                amountMinor = if (recurring.kind == "INCOME") recurring.amountMinor else -recurring.amountMinor,
                currencyCode = recurring.currencyCode,
                cleared = false,
            ),
        )
        return true
    }

    @Transaction
    open suspend fun insertReconciliationWithAdjustment(
        reconciliation: ReconciliationEntity,
        adjustment: TransactionEntity?,
        entry: TransactionEntryEntity?,
    ) {
        if (adjustment != null && entry != null) {
            insertTransactionEntity(adjustment)
            insertEntry(entry)
        }
        insertReconciliation(reconciliation)
    }

    @Query(
        """
        UPDATE transaction_entries SET cleared = :cleared
        WHERE transactionId = :transactionId
          AND EXISTS (SELECT 1 FROM transactions WHERE id = :transactionId AND deletedAt IS NULL)
        """,
    )
    protected abstract suspend fun updateTransactionCleared(transactionId: String, cleared: Boolean): Int

    @Transaction
    open suspend fun setTransactionCleared(transactionId: String, cleared: Boolean) {
        require(cleared || countReconciliationReferences(transactionId) == 0) {
            "A reconciliation adjustment must remain cleared"
        }
        require(updateTransactionCleared(transactionId, cleared) > 0) { "Transaction is missing or deleted" }
    }

    @Transaction
    open suspend fun reconcileAccount(
        accountId: String,
        currencyCode: String,
        statementLocalDate: String,
        statementBalanceMinor: Long,
        createAdjustment: Boolean,
        reconciliationId: String,
        adjustmentTransactionId: String,
        adjustmentEntryId: String,
        completedAt: Long,
    ) {
        val account = requireNotNull(getActiveAccount(accountId)) { "Account is missing or archived" }
        require(account.currencyCode == currencyCode) { "Statement currency does not match the account" }
        val calculatedBalance = requireNotNull(getClearedAccountBalance(accountId, statementLocalDate)) {
            "Cleared balance is unavailable"
        }
        val difference = Math.subtractExact(statementBalanceMinor, calculatedBalance)
        require(difference == 0L || createAdjustment) {
            "Cleared balance does not match the statement; clear matching transactions or create an adjustment"
        }
        val adjustment = if (difference != 0L) {
            TransactionEntity(
                adjustmentTransactionId,
                if (difference > 0) "INCOME" else "EXPENSE",
                statementLocalDate,
                "Reconciliation adjustment",
                "",
                completedAt,
                completedAt,
            )
        } else {
            null
        }
        val entry = adjustment?.let {
            TransactionEntryEntity(
                adjustmentEntryId,
                adjustmentTransactionId,
                accountId,
                null,
                difference,
                currencyCode,
                true,
            )
        }
        insertReconciliationWithAdjustment(
            ReconciliationEntity(
                reconciliationId,
                accountId,
                statementLocalDate,
                statementBalanceMinor,
                calculatedBalance,
                difference,
                adjustment?.id,
                completedAt,
            ),
            adjustment,
            entry,
        )
    }

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    protected abstract suspend fun softDeleteTransaction(id: String, deletedAt: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id AND deletedAt IS NOT NULL")
    abstract suspend fun restoreTransaction(id: String, restoredAt: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    protected abstract suspend fun getActiveTransaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transaction_entries WHERE transactionId = :transactionId ORDER BY rowid")
    protected abstract suspend fun getTransactionEntries(transactionId: String): List<TransactionEntryEntity>

    @Query("SELECT COUNT(*) FROM reconciliations WHERE adjustmentTransactionId = :transactionId")
    protected abstract suspend fun countReconciliationReferences(transactionId: String): Int

    @Transaction
    open suspend fun deleteTransaction(id: String, deletedAt: Long): Int {
        require(countReconciliationReferences(id) == 0) { "Reconciliation adjustments cannot be deleted" }
        return softDeleteTransaction(id, deletedAt)
    }

    @Update
    protected abstract suspend fun updateTransactionEntity(transaction: TransactionEntity): Int

    @Update
    protected abstract suspend fun updateTransactionEntries(entries: List<TransactionEntryEntity>): Int

    @Transaction
    open suspend fun getTransactionForEdit(id: String): StoredTransaction {
        val transaction = requireNotNull(getActiveTransaction(id)) { "Transaction is missing or deleted" }
        return StoredTransaction(
            transaction = transaction,
            entries = getTransactionEntries(id),
            isReconciliationAdjustment = countReconciliationReferences(id) > 0,
        )
    }

    @Transaction
    open suspend fun updateTransaction(
        transaction: TransactionEntity,
        entries: List<TransactionEntryEntity>,
    ) {
        require(updateTransactionEntity(transaction) == 1) { "Transaction could not be updated" }
        require(entries.isNotEmpty() && updateTransactionEntries(entries) == entries.size) {
            "Transaction entries could not be updated"
        }
    }

    @Query(
        """
        SELECT COUNT(*) FROM transactions t
        JOIN transaction_entries e ON e.transactionId = t.id
        WHERE t.localDate = :localDate AND t.payee = :payee
          AND e.accountId = :accountId AND e.categoryId = :categoryId
          AND e.amountMinor = :amountMinor AND e.currencyCode = :currencyCode
        """
    )
    abstract suspend fun countMatchingTransaction(
        localDate: String,
        payee: String,
        accountId: String,
        categoryId: String,
        amountMinor: Long,
        currencyCode: String,
    ): Int

    @Transaction
    open suspend fun importTransactions(items: List<TransactionImportItem>): TransactionImportWriteResult {
        var imported = 0
        var duplicates = 0
        items.forEach { item ->
            if (
                countMatchingTransaction(
                    item.transaction.localDate,
                    item.transaction.payee,
                    item.entry.accountId,
                    requireNotNull(item.entry.categoryId),
                    item.entry.amountMinor,
                    item.entry.currencyCode,
                ) > 0
            ) {
                duplicates++
            } else {
                insertTransactionEntity(item.transaction)
                insertEntry(item.entry)
                imported++
            }
        }
        return TransactionImportWriteResult(imported, duplicates)
    }

    @Query("SELECT * FROM accounts ORDER BY id")
    protected abstract suspend fun getAllAccountsForBackup(): List<AccountEntity>

    @Query("SELECT * FROM categories ORDER BY id")
    protected abstract suspend fun getAllCategoriesForBackup(): List<CategoryEntity>

    @Query("SELECT * FROM transactions ORDER BY id")
    protected abstract suspend fun getAllTransactionsForBackup(): List<TransactionEntity>

    @Query("SELECT * FROM transaction_entries ORDER BY id")
    protected abstract suspend fun getAllTransactionEntriesForBackup(): List<TransactionEntryEntity>

    @Query("SELECT * FROM reconciliations ORDER BY id")
    protected abstract suspend fun getAllReconciliationsForBackup(): List<ReconciliationEntity>

    @Query("SELECT * FROM budget_allocations ORDER BY id")
    protected abstract suspend fun getAllBudgetAllocationsForBackup(): List<BudgetAllocationEntity>

    @Query("SELECT * FROM goals ORDER BY id")
    protected abstract suspend fun getAllGoalsForBackup(): List<GoalEntity>

    @Query("SELECT * FROM goal_contributions ORDER BY id")
    protected abstract suspend fun getAllGoalContributionsForBackup(): List<GoalContributionEntity>

    @Query("SELECT * FROM recurring_schedules ORDER BY id")
    protected abstract suspend fun getAllRecurringSchedulesForBackup(): List<RecurringScheduleEntity>

    @Query("SELECT * FROM debt_profiles ORDER BY id")
    protected abstract suspend fun getAllDebtProfilesForBackup(): List<DebtProfileEntity>

    @Query("DELETE FROM reconciliations")
    protected abstract suspend fun deleteAllReconciliations()

    @Query("DELETE FROM budget_allocations")
    protected abstract suspend fun deleteAllBudgetAllocations()

    @Query("DELETE FROM recurring_schedules")
    protected abstract suspend fun deleteAllRecurringSchedules()

    @Query("DELETE FROM debt_profiles")
    protected abstract suspend fun deleteAllDebtProfiles()

    @Query("DELETE FROM transaction_entries")
    protected abstract suspend fun deleteAllTransactionEntries()

    @Query("DELETE FROM transactions")
    protected abstract suspend fun deleteAllTransactions()

    @Query("DELETE FROM goals")
    protected abstract suspend fun deleteAllGoals()

    @Query("DELETE FROM goal_contributions")
    protected abstract suspend fun deleteAllGoalContributions()

    @Query("DELETE FROM categories")
    protected abstract suspend fun deleteAllCategories()

    @Query("DELETE FROM accounts")
    protected abstract suspend fun deleteAllAccounts()

    @Insert
    protected abstract suspend fun restoreAccounts(items: List<AccountEntity>)

    @Insert
    protected abstract suspend fun restoreCategories(items: List<CategoryEntity>)

    @Insert
    protected abstract suspend fun restoreTransactions(items: List<TransactionEntity>)

    @Insert
    protected abstract suspend fun restoreTransactionEntries(items: List<TransactionEntryEntity>)

    @Insert
    protected abstract suspend fun restoreReconciliations(items: List<ReconciliationEntity>)

    @Insert
    protected abstract suspend fun restoreBudgetAllocations(items: List<BudgetAllocationEntity>)

    @Insert
    protected abstract suspend fun restoreGoals(items: List<GoalEntity>)

    @Insert
    protected abstract suspend fun restoreGoalContributions(items: List<GoalContributionEntity>)

    @Insert
    protected abstract suspend fun restoreRecurringSchedules(items: List<RecurringScheduleEntity>)

    @Insert
    protected abstract suspend fun restoreDebtProfiles(items: List<DebtProfileEntity>)

    @Transaction
    open suspend fun exportSnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        accounts = getAllAccountsForBackup(),
        categories = getAllCategoriesForBackup(),
        transactions = getAllTransactionsForBackup(),
        transactionEntries = getAllTransactionEntriesForBackup(),
        reconciliations = getAllReconciliationsForBackup(),
        budgetAllocations = getAllBudgetAllocationsForBackup(),
        goals = getAllGoalsForBackup(),
        goalContributions = getAllGoalContributionsForBackup(),
        recurringSchedules = getAllRecurringSchedulesForBackup(),
        debtProfiles = getAllDebtProfilesForBackup(),
    )

    @Transaction
    open suspend fun replaceWithSnapshot(snapshot: DatabaseSnapshot) {
        deleteAllReconciliations()
        deleteAllBudgetAllocations()
        deleteAllRecurringSchedules()
        deleteAllDebtProfiles()
        deleteAllTransactionEntries()
        deleteAllTransactions()
        deleteAllGoalContributions()
        deleteAllGoals()
        deleteAllCategories()
        deleteAllAccounts()

        restoreAccounts(snapshot.accounts)
        restoreCategories(snapshot.categories)
        restoreTransactions(snapshot.transactions)
        restoreTransactionEntries(snapshot.transactionEntries)
        restoreReconciliations(snapshot.reconciliations)
        restoreBudgetAllocations(snapshot.budgetAllocations)
        restoreGoals(snapshot.goals)
        restoreGoalContributions(snapshot.goalContributions)
        restoreRecurringSchedules(snapshot.recurringSchedules)
        restoreDebtProfiles(snapshot.debtProfiles)
    }
}
