package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.feature.finance.FinanceUiState
import org.junit.Rule
import org.junit.Test

class HomeScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardShowsAccountBalanceBudgetsAndRecentTransactions() {
        val account = Account("main", "Conta Principal", AccountType.CHECKING, "EUR", Money(345_020))
        val state = FinanceUiState(
            accounts = listOf(account),
            summary = FinanceSummary(balance = account.balance),
            budgets = listOf(BudgetProgress("food", "Alimentação", Money(50_000), Money(32_000))),
            transactions = listOf(
                TransactionItem(
                    id = "tx",
                    payee = "Continente Supermercado",
                    categoryName = "Alimentação",
                    accountName = account.name,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(4_520),
                    localDate = "2026-08-31",
                    cleared = true,
                ),
            ),
        )

        composeRule.setContent {
            NorthstarTheme {
                HomeScreen(state, PaddingValues(0.dp), {}, {})
            }
        }

        composeRule.onNodeWithText("Conta Principal").assertIsDisplayed()
        composeRule.onNodeWithText("MEUS ORÇAMENTOS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Alimentação").assertCountEquals(3)
        composeRule.onNodeWithText("TRANSAÇÕES RECENTES").assertIsDisplayed()
    }
}
