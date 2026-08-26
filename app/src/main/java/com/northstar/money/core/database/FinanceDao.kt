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
               a.openingBalanceMinor + COALESCE(SUM(e.amountMinor), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN transaction_entries e ON e.accountId = a.id
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
        WHERE e.rowid = (
            SELECT MIN(e2.rowid) FROM transaction_entries e2 WHERE e2.transactionId = t.id
        )
        ORDER BY t.localDate DESC, t.createdAt DESC
        """
    )
    abstract fun observeTransactions(): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(openingBalanceMinor) FROM accounts WHERE archivedAt IS NULL), 0)
            + COALESCE(SUM(e.amountMinor), 0) AS balanceMinor,
          COALESCE(SUM(CASE WHEN t.kind = 'INCOME' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS incomeMinor,
          COALESCE(-SUM(CASE WHEN t.kind = 'EXPENSE' AND t.localDate >= :monthStart THEN e.amountMinor ELSE 0 END), 0) AS expenseMinor
        FROM transaction_entries e
        JOIN transactions t ON t.id = e.transactionId
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
        LEFT JOIN transactions t ON t.id = e.transactionId
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
        SELECT a.openingBalanceMinor + COALESCE(SUM(e.amountMinor), 0)
        FROM accounts a
        LEFT JOIN transaction_entries e ON e.accountId = a.id
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

    @Query("DELETE FROM transactions WHERE id = :id")
    abstract suspend fun deleteTransaction(id: String)

    @Query(
        """
        SELECT COUNT(*) FROM transactions t
        JOIN transaction_entries e ON e.transactionId = t.id
        WHERE t.localDate = :localDate AND t.payee = :payee
          AND e.accountId = :accountId AND e.amountMinor = :amountMinor
        """
    )
    abstract suspend fun countMatchingTransaction(
        localDate: String,
        payee: String,
        accountId: String,
        amountMinor: Long,
    ): Int
}
