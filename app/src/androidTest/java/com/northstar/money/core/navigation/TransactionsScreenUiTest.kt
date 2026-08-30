package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.R
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.feature.finance.FinanceUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import androidx.test.core.app.ApplicationProvider

class TransactionsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenShowsMonthlySummaryAndGroupedTransaction() {
        val today = LocalDate.now()
        val state = FinanceUiState(
            summary = FinanceSummary(balance = Money(100_000)),
            transactions = listOf(
                TransactionItem(
                    id = "coffee",
                    payee = "Starbucks Café",
                    categoryName = "Alimentação",
                    accountName = "Conta Principal",
                    kind = TransactionKind.EXPENSE,
                    amount = Money(580),
                    localDate = today.toString(),
                    cleared = true,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )

        composeRule.setContent {
            NorthstarTheme {
                ActivityScreen(state, PaddingValues(0.dp), {}, { _, _ -> }, {})
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.transactions_expenses)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transactions_income)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transactions_month_balance)).assertIsDisplayed()
        composeRule.onNodeWithText("Starbucks Café").assertIsDisplayed()
    }
}
