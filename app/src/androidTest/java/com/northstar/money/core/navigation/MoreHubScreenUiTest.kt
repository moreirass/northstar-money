package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import com.northstar.money.core.designsystem.NorthstarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreHubScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hubShowsDesignGroupsAndDispatchesRows() {
        var target: MoreHubTarget? = null
        var aboutOpened = false
        composeRule.setContent {
            NorthstarTheme {
                MoreHubScreen(
                    padding = PaddingValues(0.dp),
                    onOpen = { target = it },
                    onAbout = { aboutOpened = true },
                    onHelp = {},
                )
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.more_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.more_general)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.more_financial)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.more_data)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.more_settings)).performClick()
        composeRule.runOnIdle { assertEquals(MoreHubTarget.SETTINGS, target) }
        composeRule.onNodeWithText(context.getString(R.string.more_about)).performClick()
        composeRule.runOnIdle { assertEquals(true, aboutOpened) }
    }
}
