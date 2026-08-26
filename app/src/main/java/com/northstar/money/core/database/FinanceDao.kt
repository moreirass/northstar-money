package com.northstar.money.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FinanceDao {
    @Query(
        """
        SELECT a.id, a.name, a.type, a.currencyCode,
               a.openingBalanceMinor + COALESCE(SUM(CASE WHEN t.id IS NOT NULL THEN e.amountMinor ELSE 0 END), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN transaction_entries e ON e.accountId = a.id
        LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
        WHERE a.archivedAt IS NULL
        GROUP BY a.id
        ORDER BY a.createdAt
        """
    )
    abstract fun observeAccounts(): Flow<List<AccountBalanceRow>>

    @Query("SELECT * FROM categories WHERE archivedAt IS NULL ORDER BY kind, sortOrder, name")
    abstract fun observeCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT t.id, t.payee, t.kind, t.localDate, e.amountMinor, e.currencyCode,
               a.name AS accountName, c.name AS categoryName
        FROM transactions t
        JOIN transaction_entries e ON e.transactionId = t.id
        JOIN accounts a ON a.id = e.accountId
        LEFT JOIN categories c ON c.id = e.categoryId
        WHERE t.deletedAt IS NULL AND e.rowid = (
            SELECT MIN(e2.rowid) FROM transaction_entries e2 WHERE e2.transactionId = t.id
        )
        ORDER BY t.localDate DESC, t.createdAt DESC
        """
    )
    abstract fun observeTransactions(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id, t.payee, t.kind, t.localDate, e.amountMinor, e.currencyCode,
               a.name AS accountName, c.name AS categoryName
        FROM transactions t
        JOIN transaction_entries e ON e.transactionId = t.id
        JOIN accounts a ON a.id = e.accountId
        LEFT JOIN categories c ON c.id = e.categoryId
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
          COALESCE((SELECT SUM(openingBalanceMinor) FROM accounts WHERE archivedAt IS NULL), 0)
            + COALESCE(SUM(e.amountMinor), 0) AS balanceMinor,
          COALESCE(SUM(CASE WHEN t.kind = 'INCOME' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS incomeMinor,
          COALESCE(-SUM(CASE WHEN t.kind = 'EXPENSE' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transaction_entries e
        JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
        """
    )
    abstract fun observeSummary(monthStart: String): Flow<SummaryRow>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName,
               COALESCE(b.plannedMinor, 0) AS plannedMinor,
               COALESCE(-SUM(CASE WHEN t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS spentMinor
        FROM categories c
        LEFT JOIN budget_allocations b ON b.categoryId = c.id AND b.monthStart = :monthStart
        LEFT JOIN transaction_entries e ON e.categoryId = c.id
        LEFT JOIN transactions t ON t.id = e.transactionId AND t.deletedAt IS NULL
        WHERE c.kind = 'EXPENSE' AND c.archivedAt IS NULL
        GROUP BY c.id, b.plannedMinor
        ORDER BY c.sortOrder, c.name
        """
    )
    abstract fun observeBudgets(monthStart: String): Flow<List<BudgetRow>>

    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY createdAt")
    abstract fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM recurring_schedules WHERE active = 1 ORDER BY nextLocalDate")
    abstract fun observeRecurring(): Flow<List<RecurringScheduleEntity>>

    @Query("SELECT * FROM debt_profiles ORDER BY createdAt")
    abstract fun observeDebts(): Flow<List<DebtProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBudget(item: BudgetAllocationEntity)

    @Insert
    abstract suspend fun insertGoal(item: GoalEntity)

    @Insert
    abstract suspend fun insertRecurring(item: RecurringScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDebt(item: DebtProfileEntity)

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
        WHERE a.id = :accountId
        GROUP BY a.id
        """
    )
    abstract suspend fun getAccountBalance(accountId: String): Long

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

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun deleteTransaction(id: String, deletedAt: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id AND deletedAt IS NOT NULL")
    abstract suspend fun restoreTransaction(id: String, restoredAt: Long): Int

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
        restoreRecurringSchedules(snapshot.recurringSchedules)
        restoreDebtProfiles(snapshot.debtProfiles)
    }
}
