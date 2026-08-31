package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.northstar.money.R
import com.northstar.money.core.designsystem.NorthstarTheme
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class BudgetDetailScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailShowsBudgetTrendAndCurrentTransaction() {
        val budget = BudgetProgress("food", "Alimentação", Money(60_000), Money(42_000))
        val transaction = TransactionItem(
            id = "market",
            payee = "Continente Supermercado",
            categoryName = "Alimentação",
            accountName = "Conta principal",
            kind = TransactionKind.EXPENSE,
            amount = Money(4_520),
            localDate = LocalDate.now().toString(),
            cleared = true,
            createdAt = System.currentTimeMillis(),
        )

        composeRule.setContent {
            NorthstarTheme {
                BudgetDetailScreen(budget, listOf(transaction), PaddingValues(0.dp), {}, {})
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onAllNodesWithText("Alimentação").assertCountEquals(2)
        composeRule.onNodeWithText("70%").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budget_detail_trend)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.budget_detail_transactions)).assertIsDisplayed()
        composeRule.onNodeWithText("Continente Supermercado").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.budget_detail_back)).assertIsDisplayed()
    }
}
