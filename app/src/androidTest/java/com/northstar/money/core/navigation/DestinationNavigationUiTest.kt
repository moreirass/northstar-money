package com.northstar.money.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.northstar.money.MainActivity
import org.junit.Rule
import org.junit.Test

class DestinationNavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomDestinationParticipatesInAndroidBackStack() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Skip introduction").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Skip introduction").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.onNodeWithText("Search transactions").assertIsDisplayed()

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("MEUS ORÇAMENTOS").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty())
    }
}
