package com.northstar.money.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.northstar.money.core.designsystem.NorthstarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenStateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateExplainsDataSafetyAndOffersRealRetryAction() {
        var retried = false
        composeRule.setContent {
            NorthstarTheme {
                FinanceScreenState(isLoading = false, loadFailed = true, onRetry = { retried = true }) {}
            }
        }

        composeRule.onNodeWithText("Your data was not changed. Check the device and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }
}
