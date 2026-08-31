package com.northstar.money.core.datastore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnboardingPreferenceTest {
    @Test
    fun onboardingCompletionIsPersisted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = UserPreferences(context)
        val original = preferences.settings.first().onboardingCompleted
        try {
            preferences.setOnboardingCompleted(!original)
            assertEquals(!original, preferences.settings.first().onboardingCompleted)
        } finally {
            preferences.setOnboardingCompleted(original)
        }
    }

    @Test
    fun supportedBaseCurrencyIsNormalizedAndPersisted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = UserPreferences(context)
        val original = preferences.settings.first().baseCurrencyCode
        try {
            preferences.setBaseCurrencyCode(" usd ")
            assertEquals("USD", preferences.settings.first().baseCurrencyCode)
        } finally {
            preferences.setBaseCurrencyCode(original)
        }
    }

    @Test
    fun unsupportedBaseCurrencyIsRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = UserPreferences(context)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { preferences.setBaseCurrencyCode("BTC") }
        }
    }
}
