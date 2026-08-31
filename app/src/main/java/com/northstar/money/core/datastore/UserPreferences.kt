package com.northstar.money.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
)

class UserPreferences(private val context: Context) {
    private val lockKey = booleanPreferencesKey("app_lock")
    private val remindersKey = booleanPreferencesKey("reminders")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val moneyValuesHiddenKey = booleanPreferencesKey("money_values_hidden")
    private val baseCurrencyCodeKey = stringPreferencesKey("base_currency_code")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map {
        AppSettings(
            appLockEnabled = it[lockKey] ?: false,
            remindersEnabled = it[remindersKey] ?: true,
            onboardingCompleted = it[onboardingCompletedKey] ?: false,
            moneyValuesHidden = it[moneyValuesHiddenKey] ?: false,
            baseCurrencyCode = it[baseCurrencyCodeKey] ?: "EUR",
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

    companion object {
        val SUPPORTED_CURRENCY_CODES = setOf("EUR", "USD", "GBP", "BRL", "CHF", "JPY")
    }
}
