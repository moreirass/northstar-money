package com.northstar.money.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("northstar_settings")

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

class UserPreferences(private val context: Context) {
    private val lockKey = booleanPreferencesKey("app_lock")
    private val remindersKey = booleanPreferencesKey("reminders")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val moneyValuesHiddenKey = booleanPreferencesKey("money_values_hidden")
    private val baseCurrencyCodeKey = stringPreferencesKey("base_currency_code")
    private val spendingLimitMinorKey = longPreferencesKey("spending_limit_minor")
    private val spendingLimitCurrencyKey = stringPreferencesKey("spending_limit_currency")
    private val spendingLimitPeriodKey = stringPreferencesKey("spending_limit_period")
    private val spendingLimitStartKey = stringPreferencesKey("spending_limit_start")
    private val spendingLimitEndKey = stringPreferencesKey("spending_limit_end")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map {
        val spendingLimit = it[spendingLimitMinorKey]?.let { amountMinor ->
            SpendingLimit(
                amountMinor = amountMinor,
                currencyCode = it[spendingLimitCurrencyKey] ?: "EUR",
                period = runCatching { BudgetPeriod.valueOf(it[spendingLimitPeriodKey].orEmpty()) }
                    .getOrDefault(BudgetPeriod.MONTH),
                startDate = it[spendingLimitStartKey].orEmpty(),
                endDate = it[spendingLimitEndKey].orEmpty(),
            )
        }
        AppSettings(
            appLockEnabled = it[lockKey] ?: false,
            remindersEnabled = it[remindersKey] ?: true,
            onboardingCompleted = it[onboardingCompletedKey] ?: false,
            moneyValuesHidden = it[moneyValuesHiddenKey] ?: false,
            baseCurrencyCode = it[baseCurrencyCodeKey] ?: "EUR",
            spendingLimit = spendingLimit,
        )
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.settingsDataStore.edit { it[lockKey] = enabled }
    }

    suspend fun setReminders(enabled: Boolean) {
        context.settingsDataStore.edit { it[remindersKey] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[onboardingCompletedKey] = completed }
    }

    suspend fun setMoneyValuesHidden(hidden: Boolean) {
        context.settingsDataStore.edit { it[moneyValuesHiddenKey] = hidden }
    }

    suspend fun setBaseCurrencyCode(currencyCode: String) {
        val normalized = currencyCode.trim().uppercase()
        require(normalized in SUPPORTED_CURRENCY_CODES) { "Unsupported base currency: $currencyCode" }
        context.settingsDataStore.edit { it[baseCurrencyCodeKey] = normalized }
    }

    suspend fun setSpendingLimit(limit: SpendingLimit) {
        require(limit.amountMinor > 0) { "Spending limit must be positive" }
        require(limit.currencyCode in SUPPORTED_CURRENCY_CODES) { "Unsupported spending limit currency" }
        val start = LocalDate.parse(limit.startDate)
        val end = LocalDate.parse(limit.endDate)
        require(!end.isBefore(start)) { "Spending limit end date must not precede its start date" }
        context.settingsDataStore.edit {
            it[spendingLimitMinorKey] = limit.amountMinor
            it[spendingLimitCurrencyKey] = limit.currencyCode
            it[spendingLimitPeriodKey] = limit.period.name
            it[spendingLimitStartKey] = limit.startDate
            it[spendingLimitEndKey] = limit.endDate
        }
    }

    suspend fun clearSpendingLimit() {
        context.settingsDataStore.edit {
            it.remove(spendingLimitMinorKey)
            it.remove(spendingLimitCurrencyKey)
            it.remove(spendingLimitPeriodKey)
            it.remove(spendingLimitStartKey)
            it.remove(spendingLimitEndKey)
        }
    }

    companion object {
        val SUPPORTED_CURRENCY_CODES = setOf("EUR", "USD", "GBP", "BRL", "CHF", "JPY")
    }
}
