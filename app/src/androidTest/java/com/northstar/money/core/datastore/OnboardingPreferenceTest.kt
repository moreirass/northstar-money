package com.northstar.money.core.datastore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
