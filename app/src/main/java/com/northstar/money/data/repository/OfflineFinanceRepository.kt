package com.northstar.money.data.repository


import androidx.concurrent.futures.await
import com.northstar.money.domain.repository.AccountsRepository
import com.northstar.money.domain.repository.CategoriesRepository
import com.northstar.money.domain.repository.TransactionsRepository
import com.northstar.money.domain.repository.BudgetsRepository
import com.northstar.money.domain.repository.GoalsRepository
import com.northstar.money.domain.repository.RecurringRepository
import com.northstar.money.domain.repository.DebtsRepository
import com.northstar.money.domain.repository.ReceiptsRepository
import com.northstar.money.domain.repository.ExchangeRatesRepository
import com.northstar.money.domain.repository.ImportsRepository
import com.northstar.money.domain.repository.BackupsRepository
import com.northstar.money.domain.repository.FinanceRepository
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.ArchivedCategory
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import com.northstar.money.domain.model.EditableAccount
import com.northstar.money.domain.model.EditableRecurring
import com.northstar.money.domain.model.EditableGoal
import com.northstar.money.domain.model.GoalContribution
import com.northstar.money.domain.model.DebtProfile
import com.northstar.money.domain.model.ReceiptAttachment
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.domain.model.TransactionWindow
import com.northstar.money.domain.model.TransactionPage
import com.northstar.money.domain.model.CategorySpending
import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.data.backup.FullBackupJsonCodec
import com.northstar.money.data.backup.DatabaseSnapshotValidator
import com.northstar.money.data.backup.PortableBackupCodec
import com.northstar.money.data.backup.RestoreRecoveryStore
import com.northstar.money.data.importing.TransactionCsvValidator
import com.northstar.money.data.receipt.ReceiptOcrEngine
import com.northstar.money.data.exchange.HistoricalRateClient
import com.northstar.money.data.exchange.HistoricalRateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class OfflineFinanceRepository private constructor(val domains: Domains) : FinanceRepository {
    constructor(
        dao: SuspendingFinanceDao,
        backupCodec: FullBackupJsonCodec = FullBackupJsonCodec(),
        restoreRecoveryStore: RestoreRecoveryStore? = null,
        portableBackupCodec: PortableBackupCodec = PortableBackupCodec(),
        snapshotValidator: DatabaseSnapshotValidator = DatabaseSnapshotValidator(),
        csvValidator: TransactionCsvValidator = TransactionCsvValidator(),
        receiptOcrEngine: ReceiptOcrEngine = ReceiptOcrEngine(),
        historicalRateProvider: HistoricalRateProvider = HistoricalRateClient(),
        baseCurrencyCode: Flow<String> = flowOf("EUR"),
        scheduleRateRefresh: (() -> Unit)? = null,
    ) : this(Domains(
        dao, backupCodec, restoreRecoveryStore, portableBackupCodec, snapshotValidator,
        csvValidator, receiptOcrEngine, historicalRateProvider, baseCurrencyCode, scheduleRateRefresh,
    ))

    override fun observeAccounts(): Flow<List<Account>> =
        domains.accounts.observeAccounts().repositoryFlow()

    override fun observeArchivedAccounts(): Flow<List<Account>> =
        domains.accounts.observeArchivedAccounts().repositoryFlow()

    override suspend fun seedIfEmpty() {
        domains.accounts.seedIfEmpty().await()
    }

    override suspend fun createAccount(name: String, type: com.northstar.money.domain.model.AccountType, openingBalance: Money) {
        domains.accounts.createAccount(name, type, openingBalance).await()
    }

    override suspend fun configureInitialAccount(name: String, openingBalance: Money) {
        domains.accounts.configureInitialAccount(name, openingBalance).await()
    }

    override suspend fun getAccountForEdit(id: String): EditableAccount =
        domains.accounts.getAccountForEdit(id).await()

    override suspend fun updateAccount(account: EditableAccount) {
        domains.accounts.updateAccount(account).await()
    }

    override suspend fun archiveAccount(id: String) {
        domains.accounts.archiveAccount(id).await()
    }

    override suspend fun restoreAccount(id: String) {
        domains.accounts.restoreAccount(id).await()
    }

    override fun observeCategories(): Flow<List<Category>> =
        domains.categories.observeCategories().repositoryFlow()

    override fun observeArchivedCategories(): Flow<List<ArchivedCategory>> =
        domains.categories.observeArchivedCategories().repositoryFlow()

    override suspend fun createCategory(name: String, kind: com.northstar.money.domain.model.CategoryKind) {
        domains.categories.createCategory(name, kind).await()
    }

    override suspend fun renameCategory(id: String, name: String) {
        domains.categories.renameCategory(id, name).await()
    }

    override suspend fun archiveCategory(id: String) {
        domains.categories.archiveCategory(id).await()
    }

    override suspend fun restoreCategory(id: String) {
        domains.categories.restoreCategory(id).await()
    }

    override suspend fun mergeCategory(sourceId: String, targetId: String) {
        domains.categories.mergeCategory(sourceId, targetId).await()
    }

    override suspend fun undoCategoryMerge(id: String) {
        domains.categories.undoCategoryMerge(id).await()
    }

    override fun observeTransactions(): Flow<List<TransactionItem>> =
        domains.transactions.observeTransactions().repositoryFlow()

    override fun observeTransactionWindow(window: TransactionWindow): Flow<TransactionPage> =
        domains.transactions.observeTransactionWindow(window).repositoryFlow()

    override fun observeRecentTransactions(): Flow<List<TransactionItem>> =
        domains.transactions.observeRecentTransactions().repositoryFlow()

    override fun observeDeletedTransactions(): Flow<List<TransactionItem>> =
        domains.transactions.observeDeletedTransactions().repositoryFlow()

    override suspend fun addTransaction(kind: TransactionKind, amount: Money, accountId: String, categoryId: String, payee: String) {
        domains.transactions.addTransaction(kind, amount, accountId, categoryId, payee).await()
    }

    override suspend fun deleteTransaction(id: String) {
        domains.transactions.deleteTransaction(id).await()
    }

    override suspend fun restoreTransaction(id: String) {
        domains.transactions.restoreTransaction(id).await()
    }

    override suspend fun getTransactionForEdit(id: String): EditableTransaction =
        domains.transactions.getTransactionForEdit(id).await()

    override suspend fun updateTransaction(transaction: EditableTransaction) {
        domains.transactions.updateTransaction(transaction).await()
    }

    override suspend fun transfer(
        sourceAmount: Money,
        destinationAmount: Money,
        sourceAccountId: String,
        destinationAccountId: String,
        note: String,
    ) {
        domains.transactions.transfer(sourceAmount, destinationAmount, sourceAccountId, destinationAccountId, note).await()
    }

    override suspend fun setTransactionCleared(id: String, cleared: Boolean) {
        domains.transactions.setTransactionCleared(id, cleared).await()
    }

    override suspend fun reconcile(
        accountId: String,
        statementLocalDate: String,
        statementBalance: Money,
        createAdjustment: Boolean,
    ) {
        domains.transactions.reconcile(accountId, statementLocalDate, statementBalance, createAdjustment).await()
    }

    override suspend fun allTransactions(): List<TransactionItem> =
        domains.transactions.allTransactions().await()

    override fun observeMonthSummary(monthStart: String, currency: String): Flow<FinanceSummary> =
        domains.budgets.observeMonthSummary(monthStart, currency).repositoryFlow()

    override fun observeCategorySpending(accountId: String?, currency: String): Flow<List<CategorySpending>> =
        domains.budgets.observeCategorySpending(accountId, currency).repositoryFlow()

    override fun observeMonthlyExpenses(categoryId: String, currency: String, start: String, end: String): Flow<List<com.northstar.money.domain.model.MonthlyExpense>> =
        domains.budgets.observeMonthlyExpenses(categoryId, currency, start, end).repositoryFlow()

    override fun observeSummary(): Flow<FinanceSummary> =
        domains.budgets.observeSummary().repositoryFlow()

    override fun observeBudgets(): Flow<List<com.northstar.money.domain.model.BudgetProgress>> =
        domains.budgets.observeBudgets().repositoryFlow()

    override suspend fun setBudget(categoryId: String, planned: Money) {
        domains.budgets.setBudget(categoryId, planned).await()
    }

    override fun observeGoals(): Flow<List<com.northstar.money.domain.model.SavingsGoal>> =
        domains.goals.observeGoals().repositoryFlow()

    override fun observeGoalContributions(): Flow<List<GoalContribution>> =
        domains.goals.observeGoalContributions().repositoryFlow()

    override fun observeDeletedGoalContributions(): Flow<List<GoalContribution>> =
        domains.goals.observeDeletedGoalContributions().repositoryFlow()

    override suspend fun createGoal(name: String, target: Money, saved: Money, targetDate: String?) {
        domains.goals.createGoal(name, target, saved, targetDate).await()
    }

    override suspend fun getGoalForEdit(id: String): EditableGoal =
        domains.goals.getGoalForEdit(id).await()

    override suspend fun updateGoal(goal: EditableGoal) {
        domains.goals.updateGoal(goal).await()
    }

    override suspend fun addGoalContribution(goalId: String, amount: Money, localDate: String, note: String) {
        domains.goals.addGoalContribution(goalId, amount, localDate, note).await()
    }

    override suspend fun getGoalContributionForEdit(id: String): GoalContribution =
        domains.goals.getGoalContributionForEdit(id).await()

    override suspend fun updateGoalContribution(contribution: GoalContribution) {
        domains.goals.updateGoalContribution(contribution).await()
    }

    override suspend fun deleteGoalContribution(id: String) {
        domains.goals.deleteGoalContribution(id).await()
    }

    override suspend fun restoreGoalContribution(id: String) {
        domains.goals.restoreGoalContribution(id).await()
    }

    override fun observeRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>> =
        domains.recurring.observeRecurring().repositoryFlow()

    override fun observePausedRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>> =
        domains.recurring.observePausedRecurring().repositoryFlow()

    override fun observeDeletedRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>> =
        domains.recurring.observeDeletedRecurring().repositoryFlow()

    override suspend fun createRecurring(
        name: String,
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String?,
        frequency: String,
        nextDate: String,
    ) {
        domains.recurring.createRecurring(name, kind, amount, accountId, categoryId, frequency, nextDate).await()
    }

    override suspend fun getRecurringForEdit(id: String): EditableRecurring =
        domains.recurring.getRecurringForEdit(id).await()

    override suspend fun updateRecurring(recurring: EditableRecurring) {
        domains.recurring.updateRecurring(recurring).await()
    }

    override suspend fun pauseRecurring(id: String) {
        domains.recurring.pauseRecurring(id).await()
    }

    override suspend fun resumeRecurring(id: String) {
        domains.recurring.resumeRecurring(id).await()
    }

    override suspend fun deleteRecurring(id: String) {
        domains.recurring.deleteRecurring(id).await()
    }

    override suspend fun restoreRecurring(id: String) {
        domains.recurring.restoreRecurring(id).await()
    }

    override suspend fun postDueRecurringOccurrences(throughLocalDate: String): Int =
        domains.recurring.postDueRecurringOccurrences(throughLocalDate).await()

    override fun observeDebts(): Flow<List<com.northstar.money.domain.model.DebtProfile>> =
        domains.debts.observeDebts().repositoryFlow()

    override suspend fun createDebt(accountId: String, annualRateBasisPoints: Int, minimumPayment: Money, dueDay: Int) {
        domains.debts.createDebt(accountId, annualRateBasisPoints, minimumPayment, dueDay).await()
    }

    override suspend fun getDebtForEdit(id: String): DebtProfile =
        domains.debts.getDebtForEdit(id).await()

    override suspend fun updateDebt(debt: DebtProfile) {
        domains.debts.updateDebt(debt).await()
    }

    override fun observeReceiptAttachments(): Flow<List<ReceiptAttachment>> =
        domains.receipts.observeReceiptAttachments().repositoryFlow()

    override suspend fun addReceiptAttachment(transactionId: String, originalName: String, mimeType: String, content: ByteArray) {
        domains.receipts.addReceiptAttachment(transactionId, originalName, mimeType, content).await()
    }

    override suspend fun deleteReceiptAttachment(id: String) {
        domains.receipts.deleteReceiptAttachment(id).await()
    }

    override suspend fun retryReceiptOcr(id: String) {
        domains.receipts.retryReceiptOcr(id).await()
    }

    override suspend fun applyReceiptOcr(id: String) {
        domains.receipts.applyReceiptOcr(id).await()
    }

    override fun observeHistoricalExchangeRates(): Flow<List<HistoricalExchangeRate>> =
        domains.rates.observeHistoricalExchangeRates().repositoryFlow()

    override suspend fun requestExchangeRateRefresh() {
        domains.rates.requestExchangeRateRefresh().await()
    }

    override suspend fun refreshPendingExchangeRates(): Int =
        domains.rates.refreshPendingExchangeRates().await()

    override suspend fun importCsv(csv: String): com.northstar.money.domain.model.ImportResult =
        domains.imports.importCsv(csv).await()

    override suspend fun createFullBackup(): String =
        domains.backups.createFullBackup().await()

    override suspend fun writeFullBackup(output: java.io.OutputStream, password: CharArray) {
        domains.backups.writeFullBackup(output, password).await()
    }

    override suspend fun restoreFullBackup(backup: String, recoveryPassword: CharArray) {
        domains.backups.restoreFullBackup(backup, recoveryPassword).await()
    }

    override suspend fun undoLastFullRestore(recoveryPassword: CharArray) {
        domains.backups.undoLastFullRestore(recoveryPassword).await()
    }

    class Domains internal constructor(
        dao: SuspendingFinanceDao,
        backupCodec: FullBackupJsonCodec,
        restoreRecoveryStore: RestoreRecoveryStore?,
        portableBackupCodec: PortableBackupCodec,
        snapshotValidator: DatabaseSnapshotValidator,
        csvValidator: TransactionCsvValidator,
        receiptOcrEngine: ReceiptOcrEngine,
        historicalRateProvider: HistoricalRateProvider,
        baseCurrencyCode: Flow<String>,
        scheduleRateRefresh: (() -> Unit)?,
    ) {
        private val ratesImpl = OfflineExchangeRatesRepository(dao, baseCurrencyCode, historicalRateProvider, scheduleRateRefresh)
        val rates: ExchangeRatesRepository get() = ratesImpl
        private val transactionsImpl = OfflineTransactionsRepository(dao, ratesImpl)
        val transactions: TransactionsRepository get() = transactionsImpl
        val accounts: AccountsRepository = OfflineAccountsRepository(dao, baseCurrencyCode, scheduleRateRefresh)
        val categories: CategoriesRepository = RoomCategoriesRepository(dao)
        val budgets: BudgetsRepository = OfflineBudgetsRepository(dao, baseCurrencyCode)
        val goals: GoalsRepository = RoomGoalsRepository(dao)
        val recurring: RecurringRepository = OfflineRecurringRepository(dao, ratesImpl)
        val debts: DebtsRepository = RoomDebtsRepository(dao)
        val receipts: ReceiptsRepository = OfflineReceiptsRepository(dao, receiptOcrEngine, transactions)
        val imports: ImportsRepository = OfflineImportsRepository(dao, csvValidator, ratesImpl)
        val backups: BackupsRepository = OfflineBackupsRepository(dao, backupCodec, baseCurrencyCode, portableBackupCodec, restoreRecoveryStore, snapshotValidator, scheduleRateRefresh)
    }
}
