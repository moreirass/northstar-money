package com.northstar.money.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.northstar.money.core.designsystem.NorthstarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingCoversAllPagesAndCompletes() {
        var completed = false
        composeRule.setContent {
            NorthstarTheme { OnboardingScreen(onComplete = { completed = true }) }
        }

        composeRule.onNodeWithText("A clear view of your money").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Your data stays under your control").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Build your financial Northstar").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.runOnIdle { assertTrue(completed) }
    }
}
