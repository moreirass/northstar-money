package com.northstar.money.core.database

import androidx.room.withTransaction
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Keeps Room's Java DAO off the caller thread while repositories still use coroutines. */
class SuspendingFinanceDao internal constructor(
    private val database: NorthstarDatabase,
    private val dao: FinanceDao,
) {
    private val queryDispatcher = database.queryExecutor.asCoroutineDispatcher()
    private val transactionDispatcher = database.transactionExecutor.asCoroutineDispatcher()

    fun observeAccounts() =
        dao.observeAccounts()

    fun observeArchivedAccounts() =
        dao.observeArchivedAccounts()

    fun observeCategories() =
        dao.observeCategories()

    fun observeArchivedCategories() =
        dao.observeArchivedCategories()

    fun observeTransactions() =
        dao.observeTransactions()

    fun observeRecentTransactions(limit: Int = 3) =
        dao.observeRecentTransactions(limit)

    fun observeTransactionWindow(startDate: String?, endDate: String?, categoryId: String?, limit: Int) =
        dao.observeTransactionWindow(startDate, endDate, categoryId, limit)

    fun observeReceiptAttachments() =
        dao.observeReceiptAttachments()

    suspend fun insertReceiptAttachment(item: ReceiptAttachmentEntity) =
        withContext(transactionDispatcher) { dao.insertReceiptAttachment(item) }

    suspend fun updateReceiptAttachment(item: ReceiptAttachmentEntity) =
        withContext(transactionDispatcher) { dao.updateReceiptAttachment(item) }

    suspend fun getReceiptAttachment(id: String) =
        withContext(queryDispatcher) { dao.getReceiptAttachment(id) }

    suspend fun deleteReceiptAttachment(id: String) =
        withContext(transactionDispatcher) { dao.deleteReceiptAttachment(id) }

    suspend fun upsertTransactionExchangeRate(item: TransactionExchangeRateEntity) =
        withContext(transactionDispatcher) { dao.upsertTransactionExchangeRate(item) }

    fun observeTransactionExchangeRates() =
        dao.observeTransactionExchangeRates()

    suspend fun getPendingTransactionExchangeRates() =
        withContext(queryDispatcher) { dao.getPendingTransactionExchangeRates() }

    suspend fun getTransactionExchangeRate(entryId: String) =
        withContext(queryDispatcher) { dao.getTransactionExchangeRate(entryId) }

    suspend fun getEntriesMissingExchangeRates(quoteCurrencyCode: String) =
        withContext(queryDispatcher) { dao.getEntriesMissingExchangeRates(quoteCurrencyCode) }

    fun observeDeletedTransactions() =
        dao.observeDeletedTransactions()

    fun observeSummary(
        monthStart: String,
        baseCurrencyCode: String,
        nextMonthStart: String = java.time.LocalDate.parse(monthStart).plusMonths(1).toString(),
    ) =
        dao.observeSummary(monthStart, baseCurrencyCode, nextMonthStart)

    fun observeBudgets(monthStart: String, nextMonthStart: String, baseCurrencyCode: String) =
        dao.observeBudgets(monthStart, nextMonthStart, baseCurrencyCode)

    fun observeGoals() =
        dao.observeGoals()

    fun observeGoalContributions() =
        dao.observeGoalContributions()

    fun observeDeletedGoalContributions() =
        dao.observeDeletedGoalContributions()

    fun observeRecurring() =
        dao.observeRecurring()

    fun observePausedRecurring() =
        dao.observePausedRecurring()

    fun observeDeletedRecurring() =
        dao.observeDeletedRecurring()

    suspend fun getDueRecurringSchedules(throughLocalDate: String) =
        withContext(queryDispatcher) { dao.getDueRecurringSchedules(throughLocalDate) }

    fun observeDebts() =
        dao.observeDebts()

    suspend fun upsertBudget(item: BudgetAllocationEntity) =
        withContext(transactionDispatcher) { dao.upsertBudget(item) }

    suspend fun insertGoal(item: GoalEntity) =
        withContext(transactionDispatcher) { dao.insertGoal(item) }

    suspend fun insertGoalWithInitialContribution(
        goal: GoalEntity,
        initialContribution: GoalContributionEntity?,
    ) =
        withContext(transactionDispatcher) { dao.insertGoalWithInitialContribution(goal, initialContribution) }

    suspend fun getGoal(id: String) =
        withContext(queryDispatcher) { dao.getGoal(id) }

    suspend fun getGoalContributionForEdit(id: String) =
        withContext(queryDispatcher) { dao.getGoalContributionForEdit(id) }

    suspend fun updateGoal(goal: GoalEntity) =
        withContext(transactionDispatcher) { dao.updateGoal(goal) }

    suspend fun insertGoalContribution(contribution: GoalContributionEntity) =
        withContext(transactionDispatcher) { dao.insertGoalContribution(contribution) }

    suspend fun updateGoalContribution(contribution: GoalContributionEntity) =
        withContext(transactionDispatcher) { dao.updateGoalContribution(contribution) }

    suspend fun deleteGoalContribution(id: String, deletedAt: Long) =
        withContext(transactionDispatcher) { dao.deleteGoalContribution(id, deletedAt) }

    suspend fun restoreGoalContribution(id: String, restoredAt: Long) =
        withContext(transactionDispatcher) { dao.restoreGoalContribution(id, restoredAt) }

    suspend fun insertRecurring(item: RecurringScheduleEntity) =
        withContext(transactionDispatcher) { dao.insertRecurring(item) }

    suspend fun upsertDebt(item: DebtProfileEntity) =
        withContext(transactionDispatcher) { dao.upsertDebt(item) }

    suspend fun getDebtProfile(id: String) =
        withContext(queryDispatcher) { dao.getDebtProfile(id) }

    suspend fun insertDebtProfile(item: DebtProfileEntity) =
        withContext(transactionDispatcher) { dao.insertDebtProfile(item) }

    suspend fun updateDebtProfile(item: DebtProfileEntity) =
        withContext(transactionDispatcher) { dao.updateDebtProfile(item) }

    suspend fun insertAccounts(items: List<AccountEntity>) =
        withContext(transactionDispatcher) { dao.insertAccounts(items) }

    suspend fun insertAccount(item: AccountEntity) =
        withContext(transactionDispatcher) { dao.insertAccount(item) }

    suspend fun countEntriesForAccount(accountId: String) =
        withContext(queryDispatcher) { dao.countEntriesForAccount(accountId) }

    suspend fun insertCategories(items: List<CategoryEntity>) =
        withContext(transactionDispatcher) { dao.insertCategories(items) }

    suspend fun seedIfEmpty(defaultAccounts: List<AccountEntity>, defaultCategories: List<CategoryEntity>) =
        withContext(transactionDispatcher) { dao.seedIfEmpty(defaultAccounts, defaultCategories) }

    suspend fun insertCategory(item: CategoryEntity) =
        withContext(transactionDispatcher) { dao.insertCategory(item) }

    suspend fun insertTransaction(transaction: TransactionEntity, entry: TransactionEntryEntity) =
        withContext(transactionDispatcher) { dao.insertTransaction(transaction, entry) }

    suspend fun insertTransaction(transaction: TransactionEntity, entries: List<TransactionEntryEntity>) =
        withContext(transactionDispatcher) { dao.insertTransaction(transaction, entries) }

    suspend fun getAccountBalance(accountId: String) =
        withContext(queryDispatcher) { dao.getAccountBalance(accountId) }

    suspend fun getActiveAccountCurrency(accountId: String) =
        withContext(queryDispatcher) { dao.getActiveAccountCurrency(accountId) }

    suspend fun getActiveAccount(accountId: String) =
        withContext(queryDispatcher) { dao.getActiveAccount(accountId) }

    suspend fun updateAccount(account: AccountEntity) =
        withContext(transactionDispatcher) { dao.updateAccount(account) }

    suspend fun archiveAccount(accountId: String, archivedAt: Long) =
        withContext(transactionDispatcher) { dao.archiveAccount(accountId, archivedAt) }

    suspend fun restoreAccount(accountId: String, restoredAt: Long) =
        withContext(transactionDispatcher) { dao.restoreAccount(accountId, restoredAt) }

    suspend fun getActiveCategoryKind(categoryId: String) =
        withContext(queryDispatcher) { dao.getActiveCategoryKind(categoryId) }

    suspend fun getCanonicalActiveCategoryId(categoryId: String) =
        withContext(queryDispatcher) { dao.getCanonicalActiveCategoryId(categoryId) }

    suspend fun renameCategory(categoryId: String, name: String) =
        withContext(transactionDispatcher) { dao.renameCategory(categoryId, name) }

    suspend fun archiveCategory(categoryId: String, archivedAt: Long) =
        withContext(transactionDispatcher) { dao.archiveCategory(categoryId, archivedAt) }

    suspend fun mergeCategory(sourceCategoryId: String, targetCategoryId: String, mergedAt: Long) =
        withContext(transactionDispatcher) { dao.mergeCategory(sourceCategoryId, targetCategoryId, mergedAt) }

    suspend fun restoreCategory(categoryId: String) =
        withContext(transactionDispatcher) { dao.restoreCategory(categoryId) }

    suspend fun undoCategoryMerge(categoryId: String) =
        withContext(transactionDispatcher) { dao.undoCategoryMerge(categoryId) }

    suspend fun getRecurringForEdit(id: String) =
        withContext(queryDispatcher) { dao.getRecurringForEdit(id) }

    suspend fun updateRecurring(recurring: RecurringScheduleEntity) =
        withContext(transactionDispatcher) { dao.updateRecurring(recurring) }

    suspend fun pauseRecurring(id: String) =
        withContext(transactionDispatcher) { dao.pauseRecurring(id) }

    suspend fun resumeRecurring(id: String) =
        withContext(transactionDispatcher) { dao.resumeRecurring(id) }

    suspend fun deleteRecurring(id: String, deletedAt: Long) =
        withContext(transactionDispatcher) { dao.deleteRecurring(id, deletedAt) }

    suspend fun restoreRecurring(id: String) =
        withContext(transactionDispatcher) { dao.restoreRecurring(id) }

    suspend fun postRecurringOccurrence(
        recurringId: String,
        occurrenceLocalDate: String,
        nextLocalDate: String,
        transactionId: String,
        entryId: String,
        createdAt: Long,
    ) =
        withContext(transactionDispatcher) { dao.postRecurringOccurrence(recurringId, occurrenceLocalDate, nextLocalDate, transactionId, entryId, createdAt) }

    suspend fun insertReconciliationWithAdjustment(
        reconciliation: ReconciliationEntity,
        adjustment: TransactionEntity?,
        entry: TransactionEntryEntity?,
    ) =
        withContext(transactionDispatcher) { dao.insertReconciliationWithAdjustment(reconciliation, adjustment, entry) }

    suspend fun setTransactionCleared(transactionId: String, cleared: Boolean) =
        withContext(transactionDispatcher) { dao.setTransactionCleared(transactionId, cleared) }

    suspend fun reconcileAccount(
        accountId: String,
        currencyCode: String,
        statementLocalDate: String,
        statementBalanceMinor: Long,
        createAdjustment: Boolean,
        reconciliationId: String,
        adjustmentTransactionId: String,
        adjustmentEntryId: String,
        completedAt: Long,
    ) =
        withContext(transactionDispatcher) { dao.reconcileAccount(accountId, currencyCode, statementLocalDate, statementBalanceMinor, createAdjustment, reconciliationId, adjustmentTransactionId, adjustmentEntryId, completedAt) }

    suspend fun restoreTransaction(id: String, restoredAt: Long) =
        withContext(transactionDispatcher) { dao.restoreTransaction(id, restoredAt) }

    suspend fun deleteTransaction(id: String, deletedAt: Long) =
        withContext(transactionDispatcher) { dao.deleteTransaction(id, deletedAt) }

    suspend fun getTransactionForEdit(id: String) =
        withContext(transactionDispatcher) { dao.getTransactionForEdit(id) }

    suspend fun updateTransaction(transaction: TransactionEntity, entries: List<TransactionEntryEntity>) =
        withContext(transactionDispatcher) { dao.updateTransaction(transaction, entries) }

    suspend fun countMatchingTransaction(
        localDate: String,
        payee: String,
        accountId: String,
        categoryId: String,
        amountMinor: Long,
        currencyCode: String,
    ) =
        withContext(queryDispatcher) { dao.countMatchingTransaction(localDate, payee, accountId, categoryId, amountMinor, currencyCode) }

    suspend fun importTransactions(items: List<TransactionImportItem>) =
        withContext(transactionDispatcher) { dao.importTransactions(items) }

    suspend fun exportSnapshot() =
        withContext(transactionDispatcher) { dao.exportSnapshot() }

    suspend fun replaceWithSnapshot(snapshot: DatabaseSnapshot) =
        withContext(transactionDispatcher) { dao.replaceWithSnapshot(snapshot) }

    fun observeCategorySpending(accountId: String?, currency: String) =
        dao.observeCategorySpending(accountId, currency)

    fun observeMonthlyExpenses(categoryId: String, currency: String, start: String, end: String) =
        dao.observeMonthlyExpenses(categoryId, currency, start, end)

    suspend fun getActiveAccountsForRates() =
        withContext(queryDispatcher) { dao.getActiveAccountsForRates() }

    suspend fun getAllAccountExchangeRates() =
        withContext(queryDispatcher) { dao.getAllAccountExchangeRates() }

    suspend fun upsertAccountExchangeRate(item: AccountExchangeRateEntity) =
        withContext(transactionDispatcher) { dao.upsertAccountExchangeRate(item) }

    suspend fun storeRateIfUnchanged(
        rate: TransactionExchangeRateEntity,
        expected: TransactionEntryEntity,
        localDate: String,
    ) =
        withContext(transactionDispatcher) { dao.storeRateIfUnchanged(rate, expected, localDate) }

    suspend fun storeOpeningRateIfUnchanged(rate: AccountExchangeRateEntity) =
        withContext(transactionDispatcher) { dao.storeOpeningRateIfUnchanged(rate) }

    suspend fun replaceWithRecovery(snapshot: DatabaseSnapshot, saveRecovery: suspend (DatabaseSnapshot) -> Unit) {
        database.withTransaction {
            saveRecovery(dao.exportSnapshot())
            dao.replaceWithSnapshot(snapshot)
        }
    }
}
