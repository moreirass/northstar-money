package com.northstar.money.feature.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Category
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
import com.northstar.money.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinanceUiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val summary: FinanceSummary = FinanceSummary(),
    val budgets: List<BudgetProgress> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val recurring: List<RecurringItem> = emptyList(),
    val debts: List<DebtProfile> = emptyList(),
    val forecast: CashFlowForecast = CashFlowForecast(Money(0), Money(0), LocalDate.now().toString(), 0),
    val importMessage: String? = null,
    val settings: AppSettings = AppSettings(),
)

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val preferences: UserPreferences,
) : ViewModel() {
    private val importMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val coreState = combine(
        repository.observeAccounts(),
        repository.observeCategories(),
        repository.observeTransactions(),
        repository.observeSummary(),
    ) { accounts, categories, transactions, summary ->
        FinanceUiState(accounts, categories, transactions, summary)
    }

    private val planningState = combine(
        coreState,
        repository.observeBudgets(),
        repository.observeGoals(),
    ) { core, budgets, goals ->
        core.copy(budgets = budgets, goals = goals)
    }

    val uiState: StateFlow<FinanceUiState> = combine(
        planningState,
        repository.observeRecurring(),
        repository.observeDebts(),
        importMessage,
        preferences.settings,
    ) { state, recurring, debts, message, settings ->
        state.copy(
            recurring = recurring,
            debts = debts,
            forecast = calculateForecast(state.summary.balance, recurring),
            importMessage = message,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    fun addTransaction(
        kind: TransactionKind,
        amountText: String,
        accountId: String,
        categoryId: String,
        payee: String,
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                kind = kind,
                amount = Money.parseMajor(amountText),
                accountId = accountId,
                categoryId = categoryId,
                payee = payee.ifBlank { if (kind == TransactionKind.INCOME) "Income" else "Expense" },
            )
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    fun createAccount(name: String, type: AccountType, openingBalance: String) {
        viewModelScope.launch {
            repository.createAccount(name, type, Money.parseMajor(openingBalance.ifBlank { "0" }))
        }
    }

    fun transfer(amount: String, sourceId: String, destinationId: String, note: String) {
        viewModelScope.launch {
            repository.transfer(Money.parseMajor(amount), sourceId, destinationId, note)
        }
    }

    fun reconcile(accountId: String, statementBalance: String, createAdjustment: Boolean) {
        viewModelScope.launch {
            repository.reconcile(accountId, Money.parseMajor(statementBalance), createAdjustment)
        }
    }

    fun setBudget(categoryId: String, planned: String) {
        viewModelScope.launch { repository.setBudget(categoryId, Money.parseMajor(planned)) }
    }

    fun createGoal(name: String, target: String, saved: String, targetDate: String?) {
        viewModelScope.launch {
            repository.createGoal(name, Money.parseMajor(target), Money.parseMajor(saved.ifBlank { "0" }), targetDate)
        }
    }

    fun createRecurring(
        name: String, kind: TransactionKind, amount: String, accountId: String,
        categoryId: String?, frequency: String, nextDate: String,
    ) {
        viewModelScope.launch {
            repository.createRecurring(name, kind, Money.parseMajor(amount), accountId, categoryId, frequency, nextDate)
        }
    }

    fun createDebt(accountId: String, ratePercent: String, minimumPayment: String, dueDay: String) {
        viewModelScope.launch {
            val basisPoints = ratePercent.toBigDecimal().movePointRight(2).intValueExact()
            repository.createDebt(accountId, basisPoints, Money.parseMajor(minimumPayment), dueDay.toInt())
        }
    }

    fun importCsv(csv: String) {
        viewModelScope.launch {
            val result = repository.importCsv(csv)
            importMessage.value =
                "Imported ${result.imported}; skipped ${result.skippedDuplicates} duplicates; ${result.errors} errors"
        }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { preferences.setAppLock(enabled) }
    }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch { preferences.setReminders(enabled) }
    }

    fun createCategory(name: String, kind: com.northstar.money.domain.model.CategoryKind) {
        viewModelScope.launch { repository.createCategory(name, kind) }
    }

    suspend fun createFullBackup(): String = repository.createFullBackup()

    private fun calculateForecast(balance: Money, schedules: List<RecurringItem>): CashFlowForecast {
        val today = LocalDate.now()
        val end = today.plusDays(30)
        var projected = balance.minor
        var lowest = projected
        var lowestDate = today
        var count = 0
        schedules.flatMap { schedule ->
            val dates = mutableListOf<LocalDate>()
            var date = LocalDate.parse(schedule.nextLocalDate)
            while (!date.isAfter(end)) {
                if (!date.isBefore(today)) dates += date
                date = when (schedule.frequency) {
                    "WEEKLY" -> date.plusWeeks(1)
                    "YEARLY" -> date.plusYears(1)
                    else -> date.plusMonths(1)
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
        return CashFlowForecast(Money(projected), Money(lowest), lowestDate.toString(), count)
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
