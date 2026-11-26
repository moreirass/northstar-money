package com.northstar.money.data.repository

import com.northstar.money.domain.model.CategorySpending
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.northstar.money.domain.repository.BudgetsRepository

internal class OfflineBudgetsRepository(
    private val dao: SuspendingFinanceDao,
    private val baseCurrencyCode: Flow<String>,
) : BudgetsRepository {
    override fun observeMonthSummary(monthStart: String, currency: String): LiveData<FinanceSummary> = RepositoryTasks.observe {
        dao.observeSummary(monthStart, currency).map {
            FinanceSummary(Money(it.balanceMinor, currency), Money(it.incomeMinor, currency), Money(it.expenseMinor, currency), it.missingConversions)
        }
    }

    override fun observeCategorySpending(accountId: String?, currency: String): LiveData<List<CategorySpending>> = RepositoryTasks.observe {
        dao.observeCategorySpending(accountId, currency).map { rows -> rows.map { com.northstar.money.domain.model.CategorySpending(it.categoryName, Money(it.amountMinor, currency)) } }
    }

    override fun observeMonthlyExpenses(categoryId: String, currency: String, start: String, end: String): LiveData<List<com.northstar.money.domain.model.MonthlyExpense>> = RepositoryTasks.observe {
        dao.observeMonthlyExpenses(categoryId, currency, start, end).map { rows -> rows.map { com.northstar.money.domain.model.MonthlyExpense(it.monthStart, Money(it.amountMinor, currency)) } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSummary(): LiveData<FinanceSummary> = RepositoryTasks.observe {
        return@observe currentDates().combine(baseCurrencyCode.distinctUntilChanged()) { date, currency -> date to currency }
            .flatMapLatest { (date, currencyCode) ->
                dao.observeSummary(date.withDayOfMonth(1).toString(), currencyCode).map {
                    FinanceSummary(Money(it.balanceMinor, currencyCode), Money(it.incomeMinor, currencyCode),
                        Money(it.expenseMinor, currencyCode), it.missingConversions)
                }
            }
    }

    private fun currentDates() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LocalDate.now())
            val now = java.time.ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            kotlinx.coroutines.delay(java.time.Duration.between(now, next).toMillis().coerceIn(1_000L, 60_000L))
        }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeBudgets(): LiveData<List<com.northstar.money.domain.model.BudgetProgress>> = RepositoryTasks.observe {
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(LocalDate.now().withDayOfMonth(1))
                val now = java.time.ZonedDateTime.now()
                val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                kotlinx.coroutines.delay(
                    java.time.Duration.between(now, nextDay).toMillis().coerceIn(1_000L, 60_000L),
                )
            }
        }.distinctUntilChanged().combine(baseCurrencyCode.distinctUntilChanged()) { month, currencyCode ->
            month to currencyCode
        }.flatMapLatest { (month, currencyCode) ->
            dao.observeBudgets(
                monthStart = month.toString(),
                nextMonthStart = month.plusMonths(1).toString(),
                baseCurrencyCode = currencyCode,
            ).map { rows -> currencyCode to rows }
        }.map { (currencyCode, rows) ->
            rows.map {
                com.northstar.money.domain.model.BudgetProgress(
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    planned = Money(it.plannedMinor, currencyCode),
                    spent = Money(it.spentMinor, currencyCode),
                    allocated = Money(it.allocatedMinor, currencyCode),
                    rollover = Money(it.rolloverMinor, currencyCode),
                    missingConversions = it.missingConversions,
                )
            }
        }
    }

    override fun setBudget(categoryId: String, planned: Money): ListenableFuture<Void?> = RepositoryTasks.write {
        val currencyCode = baseCurrencyCode.first()
        require(planned.currencyCode == currencyCode) { "Budgets use $currencyCode" }
        require(planned.minor >= 0) { "Budget must not be negative" }
        require(dao.getActiveCategoryKind(categoryId) == "EXPENSE") { "Choose an active expense category" }
        val month = LocalDate.now().withDayOfMonth(1).toString()
        dao.upsertBudget(
            com.northstar.money.core.database.BudgetAllocationEntity(
                "$month|$categoryId|$currencyCode", month, categoryId, planned.minor, currencyCode
            )
        )
    }

}
