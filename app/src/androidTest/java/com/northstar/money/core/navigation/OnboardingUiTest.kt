package com.northstar.money.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
        var submittedAccountName = ""
        var submittedBalance = ""
        var submittedBudgetAmount = ""
        var submittedBudgetPeriod = ""
        composeRule.setContent {
            NorthstarTheme {
                OnboardingScreen(
                    onCurrencySelected = { selectedCurrency = it },
                    onInitialAccountSubmitted = { name, balance ->
                        submittedAccountName = name
                        submittedBalance = balance
                    },
                    onBudgetSubmitted = { amount, period, _, _ ->
                        submittedBudgetAmount = amount
                        submittedBudgetPeriod = period
                    },
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
        composeRule.onNodeWithText(context.getString(R.string.onboarding_balance_title)).assertIsDisplayed()
        composeRule.onNodeWithText("0,00").performTextReplacement("1234,56")
        composeRule.onNodeWithText("Conta Principal").performTextReplacement("Conta Casa")
        composeRule.onNodeWithText(context.getString(R.string.onboarding_continue)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_budget_title)).assertIsDisplayed()
        composeRule.onNodeWithText("500,00").performTextReplacement("750,00")
        composeRule.onNodeWithText("Mês").performClick()
        composeRule.onNodeWithText(context.getString(R.string.onboarding_budget_finish)).performClick()

        composeRule.runOnIdle {
            assertEquals("USD", selectedCurrency)
            assertEquals("Conta Casa", submittedAccountName)
            assertEquals("1234,56", submittedBalance)
            assertEquals("750,00", submittedBudgetAmount)
            assertEquals("MONTH", submittedBudgetPeriod)
            assertTrue(completed)
        }
    }
}
