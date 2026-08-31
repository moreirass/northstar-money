package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.Money
import com.northstar.money.feature.finance.FinanceUiState
import org.junit.Rule
import org.junit.Test

class BudgetsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenShowsHeaderBudgetProgressAndAddAction() {
        val state = FinanceUiState(
            budgets = listOf(
                BudgetProgress(
                    categoryId = "food",
                    categoryName = "Alimentação",
                    planned = Money(40_000),
                    spent = Money(30_000),
                ),
            ),
        )

        composeRule.setContent {
            NorthstarTheme {
                PlanScreen(state, PaddingValues(0.dp), { _, _ -> }, {})
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.budgets_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Alimentação").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budgets_monthly)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budgets_percent_used, 75)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.budgets_add)).assertIsDisplayed()
    }
}
