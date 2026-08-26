package com.northstar.money.feature.finance

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
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import com.northstar.money.domain.model.EditableAccount
import com.northstar.money.domain.model.EditableRecurring
import com.northstar.money.domain.model.EditableGoal
import com.northstar.money.domain.model.GoalContribution
import com.northstar.money.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class FinanceUiState(
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
    val importMessage: String? = null,
    val settings: AppSettings = AppSettings(),
)

data class FinanceUiEvent(val message: String)

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

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val preferences: UserPreferences,
) : ViewModel() {
    private val importMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val eventChannel = Channel<FinanceUiEvent>(Channel.BUFFERED)
    val events: Flow<FinanceUiEvent> = eventChannel.receiveAsFlow()
    private val coreState = combine(
        repository.observeAccounts(),
        repository.observeCategories(),
        repository.observeTransactions(),
        repository.observeSummary(),
        repository.observeArchivedAccounts(),
    ) { accounts, categories, transactions, summary, archivedAccounts ->
        FinanceUiState(
            accounts = accounts,
            archivedAccounts = archivedAccounts,
            categories = categories,
            transactions = transactions,
            summary = summary,
        )
    }

    private val goalState = combine(
        repository.observeGoals(),
        repository.observeGoalContributions(),
        repository.observeDeletedGoalContributions(),
    ) { goals, contributions, deleted -> GoalUiState(goals, contributions, deleted) }

    private val planningState = combine(
        coreState,
        repository.observeBudgets(),
        goalState,
        repository.observeDeletedTransactions(),
        repository.observeArchivedCategories(),
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
        repository.observePausedRecurring(),
        repository.observeDeletedRecurring(),
    ) { active, paused, deleted -> RecurringUiState(active, paused, deleted) }

    val uiState: StateFlow<FinanceUiState> = combine(
        planningState,
        recurringState,
        repository.observeDebts(),
        importMessage,
        preferences.settings,
    ) { state, recurringState, debts, message, settings ->
        state.copy(
            recurring = recurringState.active,
            pausedRecurring = recurringState.paused,
            deletedRecurring = recurringState.deleted,
            debts = debts,
            forecast = calculateForecast(state.summary.balance, recurringState.active),
            importMessage = message,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    init {
        launchOperation("prepare your financial data") { repository.seedIfEmpty() }
    }

    fun addTransaction(
        kind: TransactionKind,
        amountText: String,
        accountId: String,
        categoryId: String,
        payee: String,
    ) {
        launchOperation("save the transaction") {
            repository.addTransaction(
                kind = kind,
                amount = Money.parseMajor(amountText),
                accountId = accountId,
                categoryId = categoryId,
                payee = payee.ifBlank { if (kind == TransactionKind.INCOME) "Income" else "Expense" },
            )
        }
    }

    suspend fun deleteTransaction(id: String) = repository.deleteTransaction(id)

    suspend fun restoreTransaction(id: String) = repository.restoreTransaction(id)

    suspend fun getTransactionForEdit(id: String): EditableTransaction = repository.getTransactionForEdit(id)

    fun updateTransaction(transaction: EditableTransaction) {
        launchOperation("update the transaction") { repository.updateTransaction(transaction) }
    }

    fun createAccount(name: String, type: AccountType, openingBalance: String) {
        launchOperation("create the account") {
            repository.createAccount(name, type, Money.parseMajor(openingBalance.ifBlank { "0" }))
        }
    }

    suspend fun getAccountForEdit(id: String): EditableAccount = repository.getAccountForEdit(id)

    fun updateAccount(account: EditableAccount) {
        launchOperation("update the account") { repository.updateAccount(account) }
    }

    fun archiveAccount(id: String) {
        launchOperation("archive the account") { repository.archiveAccount(id) }
    }

    fun restoreAccount(id: String) {
        launchOperation("restore the account") { repository.restoreAccount(id) }
    }

    fun transfer(amount: String, sourceId: String, destinationId: String, note: String) {
        launchOperation("save the transfer") {
            repository.transfer(Money.parseMajor(amount), sourceId, destinationId, note)
        }
    }

    fun reconcile(accountId: String, statementBalance: String, createAdjustment: Boolean) {
        launchOperation("reconcile the account") {
            repository.reconcile(accountId, Money.parseMajor(statementBalance), createAdjustment)
        }
    }

    fun setBudget(categoryId: String, planned: String) {
        launchOperation("save the budget") { repository.setBudget(categoryId, Money.parseMajor(planned)) }
    }

    fun createGoal(name: String, target: String, saved: String, targetDate: String?) {
        launchOperation("create the goal") {
            repository.createGoal(name, Money.parseMajor(target), Money.parseMajor(saved.ifBlank { "0" }), targetDate)
        }
    }

    suspend fun getGoalForEdit(id: String): EditableGoal = repository.getGoalForEdit(id)

    fun updateGoal(goal: EditableGoal) {
        launchOperation("update the savings goal") { repository.updateGoal(goal) }
    }

    fun addGoalContribution(goalId: String, amount: String, localDate: String, note: String) {
        launchOperation("add the goal contribution") {
            val currency = uiState.value.goals.firstOrNull { it.id == goalId }?.target?.currencyCode ?: "EUR"
            repository.addGoalContribution(goalId, Money.parseMajor(amount, currency), localDate, note)
        }
    }

    suspend fun getGoalContributionForEdit(id: String): GoalContribution =
        repository.getGoalContributionForEdit(id)

    fun updateGoalContribution(contribution: GoalContribution) {
        launchOperation("update the goal contribution") { repository.updateGoalContribution(contribution) }
    }

    fun deleteGoalContribution(id: String) {
        launchOperation("delete the goal contribution") { repository.deleteGoalContribution(id) }
    }

    fun restoreGoalContribution(id: String) {
        launchOperation("restore the goal contribution") { repository.restoreGoalContribution(id) }
    }

    fun createRecurring(
        name: String, kind: TransactionKind, amount: String, accountId: String,
        categoryId: String?, frequency: String, nextDate: String,
    ) {
        launchOperation("create the recurring item") {
            repository.createRecurring(name, kind, Money.parseMajor(amount), accountId, categoryId, frequency, nextDate)
        }
    }

    suspend fun getRecurringForEdit(id: String): EditableRecurring = repository.getRecurringForEdit(id)

    fun updateRecurring(recurring: EditableRecurring) {
        launchOperation("update the recurring item") { repository.updateRecurring(recurring) }
    }

    fun pauseRecurring(id: String) {
        launchOperation("pause the recurring item") { repository.pauseRecurring(id) }
    }

    fun resumeRecurring(id: String) {
        launchOperation("resume the recurring item") { repository.resumeRecurring(id) }
    }

    fun deleteRecurring(id: String) {
        launchOperation("delete the recurring item") { repository.deleteRecurring(id) }
    }

    fun restoreRecurring(id: String) {
        launchOperation("restore the recurring item") { repository.restoreRecurring(id) }
    }

    fun createDebt(accountId: String, ratePercent: String, minimumPayment: String, dueDay: String) {
        launchOperation("save the debt profile") {
            val basisPoints = ratePercent.toBigDecimal().movePointRight(2).intValueExact()
            repository.createDebt(accountId, basisPoints, Money.parseMajor(minimumPayment), dueDay.toInt())
        }
    }

    fun importCsv(csv: String) {
        launchOperation("import the CSV file") {
            val result = repository.importCsv(csv)
            importMessage.value =
                "Imported ${result.imported}; skipped ${result.skippedDuplicates} duplicates; ${result.errors} errors"
        }
    }

    fun setAppLock(enabled: Boolean) {
        launchOperation("update app lock") { preferences.setAppLock(enabled) }
    }

    fun setReminders(enabled: Boolean) {
        launchOperation("update reminders") { preferences.setReminders(enabled) }
    }

    fun createCategory(name: String, kind: com.northstar.money.domain.model.CategoryKind) {
        launchOperation("create the category") { repository.createCategory(name, kind) }
    }

    fun renameCategory(id: String, name: String) {
        launchOperation("rename the category") { repository.renameCategory(id, name) }
    }

    fun archiveCategory(id: String) {
        launchOperation("archive the category") { repository.archiveCategory(id) }
    }

    fun restoreCategory(id: String) {
        launchOperation("restore the category") { repository.restoreCategory(id) }
    }

    fun mergeCategory(sourceId: String, targetId: String) {
        launchOperation("merge the category") { repository.mergeCategory(sourceId, targetId) }
    }

    fun undoCategoryMerge(id: String) {
        launchOperation("undo the category merge") { repository.undoCategoryMerge(id) }
    }

    suspend fun createFullBackup(): String = repository.createFullBackup()

    suspend fun restoreFullBackup(backup: String, recoveryPassword: CharArray) =
        repository.restoreFullBackup(backup, recoveryPassword)

    suspend fun undoLastFullRestore(recoveryPassword: CharArray) =
        repository.undoLastFullRestore(recoveryPassword)

    private fun launchOperation(label: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            reportOperationFailure(label, { eventChannel.send(FinanceUiEvent(it)) }, operation)
        }
    }

}

internal fun calculateForecast(
    balance: Money,
    schedules: List<RecurringItem>,
    today: LocalDate = LocalDate.now(),
): CashFlowForecast {
    val end = today.plusDays(30)
    var projected = balance.minor
    var lowest = projected
    var lowestDate = today
    var count = 0
    schedules.filter {
        it.amount.currencyCode == balance.currencyCode && it.intervalCount > 0
    }.flatMap { schedule ->
        val dates = mutableListOf<LocalDate>()
        var date = LocalDate.parse(schedule.nextLocalDate)
        while (!date.isAfter(end)) {
            if (!date.isBefore(today)) dates += date
            date = when (schedule.frequency) {
                "WEEKLY" -> date.plusWeeks(schedule.intervalCount.toLong())
                "YEARLY" -> date.plusYears(schedule.intervalCount.toLong())
                else -> date.plusMonths(schedule.intervalCount.toLong())
            }
        }
        dates.map { it to schedule }
    }.sortedBy { it.first }.forEach { (date, schedule) ->
        projected += if (schedule.kind == TransactionKind.EXPENSE) -schedule.amount.minor else schedule.amount.minor
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
        count,
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
