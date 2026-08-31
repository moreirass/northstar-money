package com.northstar.money.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.northstar.money.core.designsystem.NorthstarTheme
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingCoversAllPagesAndCompletes() {
        var completed = false
        var selectedCurrency = ""
        composeRule.setContent {
            NorthstarTheme {
                OnboardingScreen(
                    onCurrencySelected = { selectedCurrency = it },
                    onComplete = { completed = true },
                )
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_new_welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_start)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_currency_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Dólar Americano").performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_continue)).performClick()
        composeRule.onNodeWithText("Your data stays under your control").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Build your financial Northstar").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.runOnIdle {
            assertEquals("USD", selectedCurrency)
            assertTrue(completed)
        }
    }
}
