package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.feature.finance.FinanceUiState
import org.junit.Rule
import org.junit.Test

class AccessibilityScaleUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val transaction = TransactionItem(
        id = "transaction",
        payee = "Neighbourhood grocery market with a long name",
        categoryName = "Groceries",
        accountName = "Household current account",
        kind = TransactionKind.EXPENSE,
        amount = Money(12_34, "EUR"),
        localDate = "2026-08-26",
        cleared = true,
    )

    @Test
    fun activityActionsRemainReachableAtTwoHundredPercentTextScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                NorthstarTheme {
                    ActivityScreen(
                        state = FinanceUiState(transactions = listOf(transaction)),
                        padding = PaddingValues(0.dp),
                        onEdit = {},
                        onSetCleared = { _, _ -> },
                        onDelete = {},
                    )
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edit = context.getString(R.string.action_edit_named, transaction.payee)
        val delete = context.getString(R.string.action_delete_named, transaction.payee)
        composeRule.onNodeWithContentDescription(edit).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(delete).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun transactionExposesOneTalkBackSummary() {
        composeRule.setContent {
            NorthstarTheme { TransactionRow(transaction) }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = context.getString(
            R.string.accessibility_transaction,
            transaction.payee,
            transaction.categoryName,
            transaction.accountName,
            transaction.amount.formatted(),
            context.getString(R.string.ui_cleared_lowercase),
        )
        composeRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }

    @Test
    fun budgetChartHasACompleteTextAlternative() {
        val budget = BudgetProgress(
            categoryId = "food",
            categoryName = "Food",
            planned = Money(10_000, "EUR"),
            spent = Money(12_500, "EUR"),
        )
        composeRule.setContent { NorthstarTheme { BudgetProgressChart(budget) } }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = context.getString(
            R.string.accessibility_budget_usage,
            budget.categoryName,
            budget.spent.formatted(),
            budget.planned.formatted(),
            125,
        )
        composeRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }
}
