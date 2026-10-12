package com.northstar.money.feature.finance

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.ArchivedCategory
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.SavingsGoal
import com.northstar.money.domain.model.RecurringItem
import com.northstar.money.domain.model.DebtProfile
import com.northstar.money.domain.model.CashFlowForecast
import java.time.LocalDate
import com.northstar.money.core.datastore.AppSettings
import com.northstar.money.core.datastore.UserPreferences
import com.northstar.money.core.datastore.BudgetPeriod
import com.northstar.money.core.datastore.SpendingLimit
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import com.northstar.money.domain.model.EditableAccount
import com.northstar.money.domain.model.EditableRecurring
import com.northstar.money.domain.model.EditableGoal
import com.northstar.money.domain.model.GoalContribution
import com.northstar.money.domain.model.ReceiptAttachment
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.northstar.money.R

data class FinanceUiState(
    val isLoading: Boolean = false,
    val asOfLocalDate: String = LocalDate.now().toString(),
    val isSaving: Boolean = false,
    val hasMoreTransactions: Boolean = false,
    val selectedMonthSummary: FinanceSummary? = null,
    val categorySpending: List<com.northstar.money.domain.model.CategorySpending> = emptyList(),
    val categorySpendingLoaded: Boolean = false,
    val budgetMonthlyExpenses: List<com.northstar.money.domain.model.MonthlyExpense>? = null,
    val selectedMonthStart: String? = null,
    val loadFailed: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val archivedAccounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val archivedCategories: List<ArchivedCategory> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val summary: FinanceSummary = FinanceSummary(),
    val deletedTransactions: List<TransactionItem> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val goalContributions: List<GoalContribution> = emptyList(),
    val deletedGoalContributions: List<GoalContribution> = emptyList(),
    val recurring: List<RecurringItem> = emptyList(),
    val pausedRecurring: List<RecurringItem> = emptyList(),
    val deletedRecurring: List<RecurringItem> = emptyList(),
    val debts: List<DebtProfile> = emptyList(),
    val forecast: CashFlowForecast = CashFlowForecast(Money(0), Money(0), LocalDate.now().toString(), 0),
    val importSummary: CsvImportSummary? = null,
    val settings: AppSettings = AppSettings(),
    val receiptAttachments: List<ReceiptAttachment> = emptyList(),
    val historicalExchangeRates: List<HistoricalExchangeRate> = emptyList(),
)

data class FinanceUiEvent(@StringRes val messageRes: Int)

data class CsvImportSummary(val imported: Int, val skippedDuplicates: Int, val errors: Int)

private data class RecurringUiState(
    val active: List<RecurringItem>,
    val paused: List<RecurringItem>,
    val deleted: List<RecurringItem>,
)

private data class GoalUiState(
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
    val deletedContributions: List<GoalContribution>,
)

private data class AuxiliaryUiState(
    val isSaving: Boolean,
    val importSummary: CsvImportSummary?,
    val settings: AppSettings,
    val receiptAttachments: List<ReceiptAttachment>,
    val historicalExchangeRates: List<HistoricalExchangeRate>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(
    private val repository: FinanceRepository,
    private val preferences: UserPreferences,
) : ViewModel() {
    private val savingOperations = MutableStateFlow(0)
    private val importSummary = kotlinx.coroutines.flow.MutableStateFlow<CsvImportSummary?>(null)
    private val eventChannel = Channel<FinanceUiEvent>(Channel.BUFFERED)
    val events: Flow<FinanceUiEvent> = eventChannel.receiveAsFlow()
    private val recoveryVisible = MutableStateFlow(false)
    fun setRecoveryVisible(visible: Boolean) { recoveryVisible.value = visible }
    private val transactionWindow = MutableStateFlow(com.northstar.money.domain.model.TransactionWindow())
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val transactionPages = transactionWindow.flatMapLatest(repository::observeTransactionWindow)
    private val selectedMonthSummary = combine(transactionWindow, preferences.settings) { window, settings ->
        (window.startDate ?: LocalDate.now().withDayOfMonth(1).toString()) to settings.baseCurrencyCode
    }.flatMapLatest { (month, currency) -> repository.observeMonthSummary(month, currency).map { month to it } }
    private val categorySpending = combine(selectedAccountId, preferences.settings, repository.observeAccounts()) { id, settings, accounts ->
        id to (accounts.firstOrNull { it.id == id }?.currencyCode ?: settings.baseCurrencyCode)
    }.flatMapLatest { (id, currency) -> repository.observeCategorySpending(id, currency) }

    fun showRecentTransactions() { transactionWindow.value = com.northstar.money.domain.model.TransactionWindow() }
    fun showTransactionMonth(month: String) {
        val start = java.time.YearMonth.parse(month).atDay(1)
        transactionWindow.value = com.northstar.money.domain.model.TransactionWindow(start.toString(), start.plusMonths(1).toString())
    }
    fun showBudgetTransactions(categoryId: String) {
        val month = LocalDate.now().withDayOfMonth(1)
        transactionWindow.value = com.northstar.money.domain.model.TransactionWindow(month.minusMonths(5).toString(), month.plusMonths(1).toString(), categoryId)
    }
    fun loadMoreTransactions() { transactionWindow.value = transactionWindow.value.copy(limit = transactionWindow.value.limit + 200) }
    fun selectHomeAccount(id: String?) { selectedAccountId.value = id }
    suspend fun allTransactions() = repository.allTransactions()

    private val coreState = combine(
        repository.observeAccounts(),
        repository.observeCategories(),
        transactionPages,
        repository.observeSummary(),
        recoveryVisible.flatMapLatest { if (it) repository.observeArchivedAccounts() else kotlinx.coroutines.flow.flowOf(emptyList()) },
    ) { accounts, categories, transactions, summary, archivedAccounts ->
        FinanceUiState(
            accounts = accounts,
            archivedAccounts = archivedAccounts,
            categories = categories,
            transactions = transactions.items,
            hasMoreTransactions = transactions.hasMore,
            summary = summary,
        )
    }

    private val goalState = combine(
        repository.observeGoals(),
        repository.observeGoalContributions(),
        recoveryVisible.flatMapLatest { if (it) repository.observeDeletedGoalContributions() else kotlinx.coroutines.flow.flowOf(emptyList()) },
    ) { goals, contributions, deleted -> GoalUiState(goals, contributions, deleted) }

    private val planningState = combine(
        coreState,
        repository.observeBudgets(),
        goalState,
        recoveryVisible.flatMapLatest { if (it) repository.observeDeletedTransactions() else kotlinx.coroutines.flow.flowOf(emptyList()) },
        recoveryVisible.flatMapLatest { if (it) repository.observeArchivedCategories() else kotlinx.coroutines.flow.flowOf(emptyList()) },
    ) { core, budgets, goalState, deletedTransactions, archivedCategories ->
        core.copy(
            budgets = budgets,
            goals = goalState.goals,
            goalContributions = goalState.contributions,
            deletedGoalContributions = goalState.deletedContributions,
            deletedTransactions = deletedTransactions,
            archivedCategories = archivedCategories,
        )
    }

    private val recurringState = combine(
        repository.observeRecurring(),
        recoveryVisible.flatMapLatest { if (it) repository.observePausedRecurring() else kotlinx.coroutines.flow.flowOf(emptyList()) },
        recoveryVisible.flatMapLatest { if (it) repository.observeDeletedRecurring() else kotlinx.coroutines.flow.flowOf(emptyList()) },
    ) { active, paused, deleted -> RecurringUiState(active, paused, deleted) }

    private val auxiliaryState = combine(
        savingOperations,
        importSummary,
        preferences.settings,
        repository.observeReceiptAttachments(),
        repository.observeHistoricalExchangeRates(),
    ) { saving, summary, settings, receipts, rates -> AuxiliaryUiState(saving > 0, summary, settings, receipts, rates) }

    private val budgetMonthlyExpenses = combine(transactionWindow, preferences.settings) { window, settings -> window to settings.baseCurrencyCode }
        .flatMapLatest { (window, currency) ->
            if (window.categoryId != null && window.startDate != null && window.endDate != null)
                repository.observeMonthlyExpenses(window.categoryId, currency, window.startDate, window.endDate)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
    private val dashboardState = combine(planningState, selectedMonthSummary, categorySpending, budgetMonthlyExpenses) { state, monthly, spending, expenses ->
        state.copy(selectedMonthSummary = monthly.second, selectedMonthStart = monthly.first,
            categorySpending = spending, categorySpendingLoaded = true, budgetMonthlyExpenses = expenses)
    }

    private val loadedState: Flow<FinanceUiState> = combine(
        dashboardState,
        recurringState,
        repository.observeDebts(),
        auxiliaryState,
    ) { state, recurringState, debts, auxiliary ->
        state.copy(
            recurring = recurringState.active,
            pausedRecurring = recurringState.paused,
            deletedRecurring = recurringState.deleted,
            debts = debts,
            forecast = calculateForecast(state.summary.balance, recurringState.active, rates = auxiliary.historicalExchangeRates),
            isSaving = auxiliary.isSaving,
            importSummary = auxiliary.importSummary,
            settings = auxiliary.settings,
            receiptAttachments = auxiliary.receiptAttachments,
            historicalExchangeRates = auxiliary.historicalExchangeRates,
        )
    }

    private val refreshRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FinanceUiState> = refreshRequests.flatMapLatest {
        loadedState
            .onStart { emit(FinanceUiState(isLoading = true)) }
            .catch { emit(FinanceUiState(loadFailed = true)) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FinanceUiState(isLoading = true),
    )

    init {
        launchOperation {
            repository.seedIfEmpty()
            repository.requestExchangeRateRefresh()
        }
    }

    fun addTransaction(
        kind: TransactionKind,
        amountText: String,
        accountId: String,
        categoryId: String,
        payee: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        launchOperation(onSuccess) {
            val currency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == accountId }) {
                "Transaction account is missing or archived"
            }.currencyCode
            repository.addTransaction(
                kind = kind,
                amount = Money.parseMajor(amountText, currency),
                accountId = accountId,
                categoryId = categoryId,
                payee = payee.ifBlank { if (kind == TransactionKind.INCOME) "Income" else "Expense" },
            )
        }
    }

    suspend fun deleteTransaction(id: String) = repository.deleteTransaction(id)

    suspend fun restoreTransaction(id: String) = repository.restoreTransaction(id)

    suspend fun getTransactionForEdit(id: String): EditableTransaction = repository.getTransactionForEdit(id)

    fun updateTransaction(transaction: EditableTransaction, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateTransaction(transaction) }
    }

    fun addReceiptAttachment(transactionId: String, originalName: String, mimeType: String, content: ByteArray) {
        launchOperation { repository.addReceiptAttachment(transactionId, originalName, mimeType, content) }
    }

    fun deleteReceiptAttachment(id: String) {
        launchOperation { repository.deleteReceiptAttachment(id) }
    }

    fun retryReceiptOcr(id: String) {
        launchOperation { repository.retryReceiptOcr(id) }
    }

    fun applyReceiptOcr(id: String) {
        launchOperation { repository.applyReceiptOcr(id) }
    }

    fun refreshExchangeRates() {
        launchOperation { repository.requestExchangeRateRefresh() }
    }

    fun setTransactionCleared(id: String, cleared: Boolean) {
        launchOperation {
            repository.setTransactionCleared(id, cleared)
        }
    }

    fun createAccount(name: String, type: AccountType, openingBalance: String, currencyCode: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            repository.createAccount(
                name,
                type,
                Money.parseMajor(openingBalance.ifBlank { "0" }, currencyCode.trim().uppercase()),
            )
        }
    }

    fun configureInitialAccount(name: String, openingBalance: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            repository.configureInitialAccount(
                name = name,
                openingBalance = Money.parseMajor(openingBalance, preferences.settings.first().baseCurrencyCode),
            )
        }
    }

    suspend fun getAccountForEdit(id: String): EditableAccount = repository.getAccountForEdit(id)

    fun updateAccount(account: EditableAccount, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateAccount(account) }
    }

    fun archiveAccount(id: String) {
        launchOperation { repository.archiveAccount(id) }
    }

    fun restoreAccount(id: String) {
        launchOperation { repository.restoreAccount(id) }
    }

    fun transfer(
        sourceAmount: String,
        destinationAmount: String,
        sourceId: String,
        destinationId: String,
        note: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        launchOperation(onSuccess) {
            val sourceCurrency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == sourceId }) {
                "Transfer source account is missing or archived"
            }.currencyCode
            val destinationCurrency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == destinationId }) {
                "Transfer destination account is missing or archived"
            }.currencyCode
            repository.transfer(
                sourceAmount = Money.parseMajor(sourceAmount, sourceCurrency),
                destinationAmount = Money.parseMajor(destinationAmount, destinationCurrency),
                sourceAccountId = sourceId,
                destinationAccountId = destinationId,
                note = note,
            )
        }
    }

    fun reconcile(
        accountId: String,
        statementLocalDate: String,
        statementBalance: String,
        createAdjustment: Boolean,
        onSuccess: (() -> Unit)? = null,
    ) {
        launchOperation(onSuccess) {
            val currency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == accountId }) {
                "Reconciliation account is missing or archived"
            }.currencyCode
            repository.reconcile(
                accountId,
                statementLocalDate,
                Money.parseMajor(statementBalance, currency),
                createAdjustment,
            )
        }
    }

    fun setBudget(categoryId: String, planned: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            repository.setBudget(categoryId, Money.parseMajor(planned, uiState.value.settings.baseCurrencyCode))
        }
    }

    fun createGoal(name: String, target: String, saved: String, targetDate: String?, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            repository.createGoal(name, Money.parseMajor(target, uiState.value.settings.baseCurrencyCode), Money.parseMajor(saved.ifBlank { "0" }, uiState.value.settings.baseCurrencyCode), targetDate)
        }
    }

    suspend fun getGoalForEdit(id: String): EditableGoal = repository.getGoalForEdit(id)

    fun updateGoal(goal: EditableGoal, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateGoal(goal) }
    }

    fun addGoalContribution(goalId: String, amount: String, localDate: String, note: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            val currency = uiState.value.goals.firstOrNull { it.id == goalId }?.target?.currencyCode ?: "EUR"
            repository.addGoalContribution(goalId, Money.parseMajor(amount, currency), localDate, note)
        }
    }

    suspend fun getGoalContributionForEdit(id: String): GoalContribution =
        repository.getGoalContributionForEdit(id)

    fun updateGoalContribution(contribution: GoalContribution, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateGoalContribution(contribution) }
    }

    fun deleteGoalContribution(id: String) {
        launchOperation { repository.deleteGoalContribution(id) }
    }

    fun restoreGoalContribution(id: String) {
        launchOperation { repository.restoreGoalContribution(id) }
    }

    fun createRecurring(
        name: String, kind: TransactionKind, amount: String, accountId: String,
        categoryId: String?, frequency: String, nextDate: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        launchOperation(onSuccess) {
            val currency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == accountId }) {
                "Recurring account is missing or archived"
            }.currencyCode
            repository.createRecurring(
                name,
                kind,
                Money.parseMajor(amount, currency),
                accountId,
                categoryId,
                frequency,
                nextDate,
            )
        }
    }

    suspend fun getRecurringForEdit(id: String): EditableRecurring = repository.getRecurringForEdit(id)

    fun updateRecurring(recurring: EditableRecurring, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateRecurring(recurring) }
    }

    fun pauseRecurring(id: String) {
        launchOperation { repository.pauseRecurring(id) }
    }

    fun resumeRecurring(id: String) {
        launchOperation { repository.resumeRecurring(id) }
    }

    fun deleteRecurring(id: String) {
        launchOperation { repository.deleteRecurring(id) }
    }

    fun restoreRecurring(id: String) {
        launchOperation { repository.restoreRecurring(id) }
    }

    fun createDebt(accountId: String, ratePercent: String, minimumPayment: String, dueDay: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            val basisPoints = ratePercent.toBigDecimal().movePointRight(2).intValueExact()
            val currency = requireNotNull(uiState.value.accounts.firstOrNull { it.id == accountId }) {
                "Debt account is missing or archived"
            }.currencyCode
            repository.createDebt(accountId, basisPoints, Money.parseMajor(minimumPayment, currency), dueDay.toInt())
        }
    }

    suspend fun getDebtForEdit(id: String): DebtProfile = repository.getDebtForEdit(id)

    fun updateDebt(debt: DebtProfile, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.updateDebt(debt) }
    }

    fun importCsv(csv: String) {
        launchOperation {
            val result = repository.importCsv(csv)
            importSummary.value = CsvImportSummary(result.imported, result.skippedDuplicates, result.errors)
        }
    }

    fun setAppLock(enabled: Boolean) {
        launchOperation { preferences.setAppLock(enabled) }
    }

    fun setReminders(enabled: Boolean) {
        launchOperation { preferences.setReminders(enabled) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        launchOperation { preferences.setOnboardingCompleted(completed) }
    }

    fun setMoneyValuesHidden(hidden: Boolean) {
        launchOperation { preferences.setMoneyValuesHidden(hidden) }
    }

    fun setBaseCurrencyCode(currencyCode: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { preferences.setBaseCurrencyCode(currencyCode); repository.requestExchangeRateRefresh() }
    }

    fun completeOnboarding(amount: String, period: String, startDate: String, endDate: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) {
            val currencyCode = preferences.settings.first().baseCurrencyCode
            preferences.setSpendingLimit(
                SpendingLimit(
                    amountMinor = Money.parseMajor(amount, currencyCode).minor,
                    currencyCode = currencyCode,
                    period = BudgetPeriod.valueOf(period),
                    startDate = startDate,
                    endDate = endDate,
                ),
            )
            preferences.setOnboardingCompleted(true)
        }
    }

    fun retryLoading() {
        refreshRequests.value += 1
    }

    fun createCategory(name: String, kind: com.northstar.money.domain.model.CategoryKind, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.createCategory(name, kind) }
    }

    fun renameCategory(id: String, name: String, onSuccess: (() -> Unit)? = null) {
        launchOperation(onSuccess) { repository.renameCategory(id, name) }
    }

    fun archiveCategory(id: String) {
        launchOperation { repository.archiveCategory(id) }
    }

    fun restoreCategory(id: String) {
        launchOperation { repository.restoreCategory(id) }
    }

    fun mergeCategory(sourceId: String, targetId: String) {
        launchOperation { repository.mergeCategory(sourceId, targetId) }
    }

    fun undoCategoryMerge(id: String) {
        launchOperation { repository.undoCategoryMerge(id) }
    }

    suspend fun createFullBackup(): String = repository.createFullBackup()

    suspend fun writeFullBackup(output: java.io.OutputStream, password: CharArray) = repository.writeFullBackup(output, password)

    suspend fun restoreFullBackup(backup: String, recoveryPassword: CharArray) =
        repository.restoreFullBackup(backup, recoveryPassword)

    suspend fun undoLastFullRestore(recoveryPassword: CharArray) =
        repository.undoLastFullRestore(recoveryPassword)

    private fun launchOperation(onSuccess: (() -> Unit)? = null, operation: suspend () -> Unit) {
        val saving = onSuccess != null
        if (saving && savingOperations.value > 0) return
        if (saving) savingOperations.value++
        viewModelScope.launch {
            try {
                operation()
                onSuccess?.invoke()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                eventChannel.send(FinanceUiEvent(R.string.operation_failed_generic))
            } finally { if (saving) savingOperations.value-- }
        }
    }

}

internal fun calculateForecast(
    balance: Money,
    schedules: List<RecurringItem>,
    today: LocalDate = LocalDate.now(),
    rates: List<HistoricalExchangeRate> = emptyList(),
): CashFlowForecast {
    val end = today.plusDays(30)
    var projected = balance.minor
    var lowest = projected
    var lowestDate = today
    var count = 0
    var missing = 0
    val converted = schedules.mapNotNull { schedule ->
        if (schedule.amount.currencyCode == balance.currencyCode) schedule else {
            val rate = rates.filter { it.baseCurrencyCode == schedule.amount.currencyCode &&
                it.quoteCurrencyCode == balance.currencyCode && it.status == "AVAILABLE" && it.rateMicros != null }
                .maxByOrNull { it.rateLocalDate }
            if (rate == null) { missing++; null } else schedule.copy(amount = Money(
                com.northstar.money.data.exchange.HistoricalRateClient.convertMinor(schedule.amount.minor,
                    schedule.amount.currencyCode, balance.currencyCode, requireNotNull(rate.rateMicros)), balance.currencyCode))
        }
    }
    converted.filter { it.intervalCount > 0 }.flatMap { schedule ->
        val dates = mutableListOf<LocalDate>()
        var date = LocalDate.parse(schedule.nextLocalDate)
        // Jump over old occurrences instead of iterating through years on the UI thread.
        if (date.isBefore(today)) {
            val steps = when (schedule.frequency) {
                "WEEKLY" -> java.time.temporal.ChronoUnit.WEEKS.between(date, today) / schedule.intervalCount
                "YEARLY" -> (today.year - date.year).toLong() / schedule.intervalCount
                else -> java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(date), java.time.YearMonth.from(today)) / schedule.intervalCount
            }
            if (steps > 0) date = when (schedule.frequency) {
                "WEEKLY" -> date.plusWeeks(steps * schedule.intervalCount)
                "YEARLY" -> java.time.YearMonth.of(date.year + (steps * schedule.intervalCount).toInt(), schedule.anchorMonth).let { it.atDay(schedule.anchorDay.coerceAtMost(it.lengthOfMonth())) }
                else -> java.time.YearMonth.from(date).plusMonths(steps * schedule.intervalCount).let { it.atDay(schedule.anchorDay.coerceAtMost(it.lengthOfMonth())) }
            }
        }
        while (!date.isAfter(end)) {
            if (!date.isBefore(today)) dates += date
            date = com.northstar.money.domain.model.nextOccurrence(date, schedule.frequency,
                schedule.intervalCount, schedule.anchorDay, schedule.anchorMonth)
        }
        dates.map { it to schedule }
    }.sortedBy { it.first }.forEach { (date, schedule) ->
        projected = Math.addExact(projected, if (schedule.kind == TransactionKind.EXPENSE) Math.negateExact(schedule.amount.minor) else schedule.amount.minor)
        count++
        if (projected < lowest) {
            lowest = projected
            lowestDate = date
        }
    }
    return CashFlowForecast(
        Money(projected, balance.currencyCode),
        Money(lowest, balance.currencyCode),
        lowestDate.toString(),
        count, missing,
    )
}

internal suspend fun reportOperationFailure(
    label: String,
    report: suspend (String) -> Unit,
    operation: suspend () -> Unit,
) {
    try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        val detail = if (error is IllegalArgumentException && !error.message.isNullOrBlank()) {
            " ${error.message}."
        } else {
            " Please try again."
        }
        report("Could not $label.$detail")
    }
}

class FinanceViewModelFactory(
    private val repository: FinanceRepository,
    private val preferences: UserPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
        return FinanceViewModel(repository, preferences) as T
    }
}
