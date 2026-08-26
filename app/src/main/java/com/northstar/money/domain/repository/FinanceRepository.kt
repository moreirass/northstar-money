package com.northstar.money.domain.repository

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
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeArchivedAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>
    fun observeArchivedCategories(): Flow<List<ArchivedCategory>>
    fun observeTransactions(): Flow<List<TransactionItem>>
    fun observeDeletedTransactions(): Flow<List<TransactionItem>>
    fun observeSummary(): Flow<FinanceSummary>
    fun observeBudgets(): Flow<List<com.northstar.money.domain.model.BudgetProgress>>
    fun observeGoals(): Flow<List<com.northstar.money.domain.model.SavingsGoal>>
    fun observeGoalContributions(): Flow<List<GoalContribution>>
    fun observeDeletedGoalContributions(): Flow<List<GoalContribution>>
    fun observeRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>>
    fun observePausedRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>>
    fun observeDeletedRecurring(): Flow<List<com.northstar.money.domain.model.RecurringItem>>
    fun observeDebts(): Flow<List<com.northstar.money.domain.model.DebtProfile>>
    suspend fun seedIfEmpty()
    suspend fun addTransaction(
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String,
        payee: String,
    )
    suspend fun createAccount(name: String, type: com.northstar.money.domain.model.AccountType, openingBalance: Money)
    suspend fun getAccountForEdit(id: String): EditableAccount
    suspend fun updateAccount(account: EditableAccount)
    suspend fun archiveAccount(id: String)
    suspend fun restoreAccount(id: String)
    suspend fun transfer(
        sourceAmount: Money,
        destinationAmount: Money,
        sourceAccountId: String,
        destinationAccountId: String,
        note: String,
    )
    suspend fun reconcile(accountId: String, statementBalance: Money, createAdjustment: Boolean)
    suspend fun setBudget(categoryId: String, planned: Money)
    suspend fun createGoal(name: String, target: Money, saved: Money, targetDate: String?)
    suspend fun getGoalForEdit(id: String): EditableGoal
    suspend fun updateGoal(goal: EditableGoal)
    suspend fun addGoalContribution(goalId: String, amount: Money, localDate: String, note: String)
    suspend fun getGoalContributionForEdit(id: String): GoalContribution
    suspend fun updateGoalContribution(contribution: GoalContribution)
    suspend fun deleteGoalContribution(id: String)
    suspend fun restoreGoalContribution(id: String)
    suspend fun createRecurring(
        name: String, kind: TransactionKind, amount: Money, accountId: String,
        categoryId: String?, frequency: String, nextDate: String,
    )
    suspend fun getRecurringForEdit(id: String): EditableRecurring
    suspend fun updateRecurring(recurring: EditableRecurring)
    suspend fun pauseRecurring(id: String)
    suspend fun resumeRecurring(id: String)
    suspend fun deleteRecurring(id: String)
    suspend fun restoreRecurring(id: String)
    suspend fun postDueRecurringOccurrences(throughLocalDate: String): Int
    suspend fun createDebt(accountId: String, annualRateBasisPoints: Int, minimumPayment: Money, dueDay: Int)
    suspend fun getDebtForEdit(id: String): DebtProfile
    suspend fun updateDebt(debt: DebtProfile)
    suspend fun importCsv(csv: String): com.northstar.money.domain.model.ImportResult
    suspend fun createCategory(name: String, kind: com.northstar.money.domain.model.CategoryKind)
    suspend fun renameCategory(id: String, name: String)
    suspend fun archiveCategory(id: String)
    suspend fun restoreCategory(id: String)
    suspend fun mergeCategory(sourceId: String, targetId: String)
    suspend fun undoCategoryMerge(id: String)
    suspend fun deleteTransaction(id: String)
    suspend fun restoreTransaction(id: String)
    suspend fun getTransactionForEdit(id: String): EditableTransaction
    suspend fun updateTransaction(transaction: EditableTransaction)
    suspend fun createFullBackup(): String
    suspend fun restoreFullBackup(backup: String, recoveryPassword: CharArray)
    suspend fun undoLastFullRestore(recoveryPassword: CharArray)
}
