package com.northstar.money.core.datastore

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AppSettings(
    val appLockEnabled: Boolean = false,
    val remindersEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val moneyValuesHidden: Boolean = false,
    val baseCurrencyCode: String = "EUR",
    val spendingLimit: SpendingLimit? = null,
)

enum class BudgetPeriod { WEEK, MONTH, YEAR, CUSTOM }

data class SpendingLimit(
    val amountMinor: Long,
    val currencyCode: String,
    val period: BudgetPeriod,
    val startDate: String,
    val endDate: String,
)

class UserPreferences(context: Context) {
    private val repository = SettingsRepository.get(context)
    val settings: Flow<AppSettings> = callbackFlow {
        val subscription = repository.settings().subscribe({ trySend(it) }, { close(it) })
        awaitClose { subscription.dispose() }
    }.buffer(Channel.CONFLATED)

    suspend fun setAppLock(enabled: Boolean) = repository.setAppLock(enabled).awaitWrite()
    suspend fun setReminders(enabled: Boolean) = repository.setReminders(enabled).awaitWrite()
    suspend fun setOnboardingCompleted(enabled: Boolean) = repository.setOnboardingCompleted(enabled).awaitWrite()
    suspend fun setMoneyValuesHidden(enabled: Boolean) = repository.setMoneyValuesHidden(enabled).awaitWrite()
    suspend fun setBaseCurrencyCode(currencyCode: String) = repository.setBaseCurrencyCode(currencyCode).awaitWrite()
    suspend fun setSpendingLimit(limit: SpendingLimit) = repository.setSpendingLimit(limit).awaitWrite()
    suspend fun clearSpendingLimit() = repository.clearSpendingLimit().awaitWrite()

    companion object {
        val SUPPORTED_CURRENCY_CODES: Set<String> = SettingsRepository.SUPPORTED_CURRENCIES
    }
}

private suspend fun Completable.awaitWrite(): Unit = suspendCancellableCoroutine { continuation ->
    val subscription = subscribe({ continuation.resume(Unit) }, { continuation.resumeWithException(it) })
    continuation.invokeOnCancellation { subscription.dispose() }
}
