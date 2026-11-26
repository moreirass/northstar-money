package com.northstar.money.data.repository

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.domain.model.Account
import kotlinx.coroutines.CancellationException

internal suspend fun SuspendingFinanceDao.requireAccountCurrency(accountId: String, currencyCode: String) {
    val accountCurrency = requireNotNull(getActiveAccountCurrency(accountId)) {
        "Account is missing or archived"
    }
    require(accountCurrency == currencyCode) {
        "Account uses $accountCurrency; $currencyCode operations are not supported for it"
    }
}

internal suspend fun <T> suspendResult(operation: suspend () -> T): Result<T> = try {
    Result.success(operation())
} catch (error: CancellationException) { throw error }
catch (error: Exception) { Result.failure(error) }
