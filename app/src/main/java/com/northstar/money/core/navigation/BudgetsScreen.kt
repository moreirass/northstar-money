package com.northstar.money.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.northstar.money.R
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.feature.finance.FinanceUiState

private val BudgetsBackground = Color(0xFF08080A)
private val BudgetSurface = Color(0xFF18181C)
private val BudgetDivider = Color(0xFF24242B)
private val BudgetPrimary = Color.White
private val BudgetSecondary = Color(0xFF8E8E9F)
private val BudgetPositive = Color(0xFF10B981)
private val BudgetWarning = Color(0xFFF59E0B)
private val BudgetNegative = Color(0xFFF43F5E)
private val BudgetBlue = Color(0xFF3B82F6)
private val BudgetPurple = Color(0xFFA855F7)

internal fun budgetStatusColor(percent: Int): Color = when {
    percent >= 100 -> BudgetNegative
    percent >= 70 -> BudgetWarning
    else -> BudgetPositive
}

@Composable
internal fun PlanScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onSetBudget: (String, String) -> Unit,
    onOpenBudget: (String) -> Unit,
) {
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    val activeBudgets = remember(state.budgets) { state.budgets.filter { it.planned.minor > 0L || it.spent.minor > 0L } }

    Box(Modifier.fillMaxSize().background(BudgetsBackground).padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 18.dp, start = 24.dp, end = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.budgets_title),
                        color = BudgetPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                    Text(
                        stringResource(R.string.budgets_subtitle),
                        color = BudgetSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
            if (activeBudgets.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.budgets_empty),
                        color = BudgetSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                items(activeBudgets, key = { it.categoryId }) { budget ->
                    DesignedBudgetCard(budget, onClick = { onOpenBudget(budget.categoryId) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showCategoryPicker = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp),
            shape = CircleShape,
            containerColor = BudgetPositive,
            contentColor = BudgetsBackground,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budgets_add))
        }
    }

    if (showCategoryPicker) {
        BudgetCategoryPicker(
            budgets = state.budgets,
            onDismiss = { showCategoryPicker = false },
            onSelected = { id ->
                showCategoryPicker = false
                editingCategoryId = id
            },
        )
    }

    editingCategoryId?.let { id ->
        val budget = state.budgets.firstOrNull { it.categoryId == id }
        if (budget != null) {
            AmountDialog(
                title = stringResource(R.string.budget_for_named, budget.categoryName),
                initial = if (budget.allocated.minor == 0L) "" else budget.allocated.minor.toBigDecimal().movePointLeft(2).toPlainString(),
                onDismiss = { editingCategoryId = null },
                onSave = {
                    onSetBudget(id, it)
                    editingCategoryId = null
                },
            )
        }
    }
}

@Composable
private fun DesignedBudgetCard(budget: BudgetProgress, onClick: () -> Unit) {
    val percent = budgetUsagePercent(budget.spent.minor, budget.planned.minor)
    val color = budgetStatusColor(percent)
    val categoryStyle = budgetCategoryStyle(budget.categoryName)
    val fraction = chartFraction(budget.spent.minor, budget.planned.minor)
    val description = stringResource(
        R.string.accessibility_budget_usage,
        budget.categoryName,
        budget.spent.displayValue(),
        budget.planned.displayValue(),
        percent,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BudgetDivider, RoundedCornerShape(16.dp))
            .background(BudgetSurface)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(32.dp).background(categoryStyle.color.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryStyle.icon, contentDescription = null, tint = categoryStyle.color, modifier = Modifier.size(16.dp))
                }
                Text(
                    budget.categoryName,
                    color = BudgetPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.budgets_monthly),
                color = BudgetSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.background(BudgetDivider, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(BudgetDivider, CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(color, CircleShape))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        budget.spent.displayValue(),
                        color = BudgetPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        stringResource(R.string.budgets_of_amount, budget.planned.displayValue()),
                        color = BudgetSecondary,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    stringResource(R.string.budgets_percent_used, percent),
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private data class BudgetCategoryStyle(val icon: ImageVector, val color: Color)

private fun budgetCategoryStyle(name: String): BudgetCategoryStyle {
    val normalized = name.lowercase()
    return when {
        normalized.contains("transport") -> BudgetCategoryStyle(Icons.Default.DirectionsCar, BudgetBlue)
        normalized.contains("lazer") || normalized.contains("entertain") -> BudgetCategoryStyle(Icons.Default.LocalCafe, BudgetNegative)
        normalized.contains("saúde") || normalized.contains("health") -> BudgetCategoryStyle(Icons.Default.FavoriteBorder, BudgetPurple)
        normalized.contains("compra") || normalized.contains("shopping") -> BudgetCategoryStyle(Icons.Default.ShoppingCart, BudgetWarning)
        else -> BudgetCategoryStyle(Icons.Default.LocalMall, BudgetNegative)
    }
}

@Composable
private fun BudgetCategoryPicker(
    budgets: List<BudgetProgress>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budgets_choose_category)) },
        text = {
            if (budgets.isEmpty()) {
                Text(stringResource(R.string.budgets_all_categories_set))
            } else {
                LazyColumn {
                    items(budgets, key = { it.categoryId }) { budget ->
                        TextButton(onClick = { onSelected(budget.categoryId) }, modifier = Modifier.fillMaxWidth()) {
                            Text(budget.categoryName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}
