package com.northstar.money.core.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.CategoryKind
import com.northstar.money.domain.model.Money
import com.northstar.money.feature.finance.FinanceUiState
import org.junit.Rule
import org.junit.Test

class FormSelectionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = FinanceUiState(
        accounts = (1..6).map { index ->
            Account("account-$index", "Account $index", AccountType.CHECKING, "EUR", Money(0))
        },
        categories = (1..6).map { index ->
            Category("category-$index", "Category $index", CategoryKind.EXPENSE)
        } + Category("income", "Salary", CategoryKind.INCOME),
    )

    @Test
    fun addTransactionSheet_exposesLastAccountAndCategory() {
        composeRule.setContent {
            MaterialTheme {
                AddTransactionSheet(state, onDismiss = {}) { _, _, _, _, _, _ -> }
            }
        }

        composeRule.onNodeWithText("Account 6").fetchSemanticsNode()
        composeRule.onNodeWithText("Category 6").fetchSemanticsNode()
    }

    @Test
    fun recurringDialog_exposesLastAccountAndCategory() {
        composeRule.setContent {
            MaterialTheme {
                RecurringDialog(state, onDismiss = {}) { _, _, _, _, _, _, _ -> }
            }
        }

        composeRule.onNodeWithText("Account 6").fetchSemanticsNode()
        composeRule.onNodeWithText("Category 6").fetchSemanticsNode()
    }

    @Test
    fun debtDialog_exposesLastAccount() {
        composeRule.setContent {
            MaterialTheme {
                DebtDialog(state, onDismiss = {}) { _, _, _, _ -> }
            }
        }

        composeRule.onNodeWithText("Account 6").fetchSemanticsNode()
    }
}
