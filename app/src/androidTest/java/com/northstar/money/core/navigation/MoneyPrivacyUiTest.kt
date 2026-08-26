package com.northstar.money.core.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.domain.model.Money
import org.junit.Rule
import org.junit.Test

class MoneyPrivacyUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hiddenModeMasksTheAmountRenderedByFinancialComponents() {
        val money = Money(12_34, "EUR")
        composeRule.setContent {
            CompositionLocalProvider(LocalMoneyValuesHidden provides true) {
                NorthstarTheme { SummaryCard("Balance", money) }
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.money_hidden_value)).assertIsDisplayed()
        composeRule.onAllNodesWithText(money.formatted()).assertCountEquals(0)
    }
}
