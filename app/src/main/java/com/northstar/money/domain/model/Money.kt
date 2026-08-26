package com.northstar.money.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class Money(val minor: Long, val currencyCode: String = "EUR") {
    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode)
        return copy(minor = Math.addExact(minor, other.minor))
    }

    fun formatted(locale: Locale = Locale.getDefault()): String {
        val currency = Currency.getInstance(currencyCode)
        val divisor = BigDecimal.TEN.pow(currency.defaultFractionDigits)
        return NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
            .format(BigDecimal.valueOf(minor).divide(divisor))
    }

    companion object {
        fun parseMajor(text: String, currencyCode: String = "EUR"): Money {
            val digits = Currency.getInstance(currencyCode).defaultFractionDigits
            val normalized = text.trim().replace(',', '.')
            val minor = normalized.toBigDecimal()
                .movePointRight(digits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
            return Money(minor, currencyCode)
        }
    }
}

enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT }
enum class CategoryKind { INCOME, EXPENSE }
enum class TransactionKind { INCOME, EXPENSE, TRANSFER }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val balance: Money,
    val clearedBalance: Money = balance,
)

data class EditableAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
)

data class Category(val id: String, val name: String, val kind: CategoryKind)

data class ArchivedCategory(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val mergedIntoCategoryId: String?,
    val mergedIntoCategoryName: String?,
)

data class TransactionItem(
    val id: String,
    val payee: String,
    val categoryName: String?,
    val accountName: String,
    val kind: TransactionKind,
    val amount: Money,
    val localDate: String,
    val cleared: Boolean,
)

data class EditableTransaction(
    val id: String,
    val kind: TransactionKind,
    val localDate: String,
    val payee: String,
    val note: String,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    val destinationAccountId: String? = null,
    val destinationAmount: Money? = null,
)

data class FinanceSummary(
    val balance: Money = Money(0),
    val incomeThisMonth: Money = Money(0),
    val expensesThisMonth: Money = Money(0),
)

data class BudgetProgress(
    val categoryId: String,
    val categoryName: String,
    val planned: Money,
    val spent: Money,
    val allocated: Money = planned,
    val rollover: Money = Money(0, planned.currencyCode),
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Money,
    val saved: Money,
    val targetLocalDate: String?,
    val status: String = "ACTIVE",
)

data class EditableGoal(
    val id: String,
    val name: String,
    val target: Money,
    val targetLocalDate: String?,
    val status: String,
)

data class GoalContribution(
    val id: String,
    val goalId: String,
    val goalName: String,
    val amount: Money,
    val localDate: String,
    val note: String,
)

data class RecurringItem(
    val id: String,
    val name: String,
    val kind: TransactionKind,
    val amount: Money,
    val nextLocalDate: String,
    val frequency: String,
    val intervalCount: Int = 1,
)

data class EditableRecurring(
    val id: String,
    val name: String,
    val kind: TransactionKind,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    val frequency: String,
    val intervalCount: Int,
    val nextLocalDate: String,
)

data class DebtProfile(
    val id: String,
    val accountId: String,
    val annualRateBasisPoints: Int,
    val minimumPayment: Money,
    val dueDay: Int,
)

data class CashFlowForecast(
    val projectedBalance: Money,
    val lowestBalance: Money,
    val lowestDate: String,
    val scheduledEvents: Int,
)

data class ImportResult(val imported: Int, val skippedDuplicates: Int, val errors: Int)
