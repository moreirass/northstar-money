package com.northstar.money.core.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.feature.finance.FinanceUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val HomeBackground = Color(0xFF08080A)
private val SurfaceStrong = Color(0xFF18181C)
private val SurfaceSoft = Color(0xFF121215)
private val Divider = Color(0xFF24242B)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF8E8E9F)
private val TextTertiary = Color(0xFF4F4F5F)
private val Positive = Color(0xFF10B981)
private val Negative = Color(0xFFF43F5E)
private val CategoryPalette = listOf(
    Negative,
    Color(0xFF3B82F6),
    Positive,
    Color(0xFFA855F7),
    TextSecondary,
)

internal data class HomeCategoryShare(
    val name: String,
    val amountMinor: Long,
    val fraction: Float,
    val color: Color,
)

internal fun homeCategoryShares(transactions: List<TransactionItem>): List<HomeCategoryShare> {
    val totals = transactions
        .asSequence()
        .filter { it.kind == TransactionKind.EXPENSE && it.amount.minor > 0 }
        .groupBy { it.categoryName?.takeIf(String::isNotBlank) ?: "Outros" }
        .mapValues { (_, entries) -> entries.sumOf { it.amount.minor } }
        .entries
        .sortedByDescending { it.value }
    val total = totals.sumOf { it.value }
    return totals.take(5).mapIndexed { index, entry ->
        HomeCategoryShare(
            name = entry.key,
            amountMinor = entry.value,
            fraction = if (total == 0L) 0f else entry.value.toFloat() / total.toFloat(),
            color = CategoryPalette[index % CategoryPalette.size],
        )
    }
}

@Composable
internal fun HomeScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
) {
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAccount = state.accounts.firstOrNull { it.id == selectedAccountId }
    val visibleTransactions = remember(state.transactions, selectedAccountId) {
        selectedAccount?.let { account -> state.transactions.filter { it.accountName == account.name } }
            ?: state.transactions
    }
    val categoryShares = remember(visibleTransactions) { homeCategoryShares(visibleTransactions) }
    val shownBalance = selectedAccount?.balance ?: state.summary.balance

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .padding(padding),
        contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BalanceHeader(
                accounts = state.accounts,
                selectedAccount = selectedAccount,
                balance = shownBalance,
                onAccountSelected = { selectedAccountId = it?.id },
            )
        }
        item {
            BudgetCarousel(
                budgets = state.budgets,
                onOpenBudgets = onOpenBudgets,
            )
        }
        item {
            CategorySpendingCard(categoryShares, shownBalance.currencyCode)
        }
        item {
            SectionHeader(
                title = "TRANSAÇÕES RECENTES",
                action = "Ver tudo",
                onAction = onOpenTransactions,
            )
        }
        if (visibleTransactions.isEmpty()) {
            item {
                Text(
                    text = "Adiciona a primeira transação para veres a tua atividade.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .background(SurfaceSoft, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                )
            }
        } else {
            items(visibleTransactions.take(5), key = { it.id }) { transaction ->
                HomeTransactionRow(transaction)
            }
        }
    }
}

@Composable
private fun BalanceHeader(
    accounts: List<Account>,
    selectedAccount: Account?,
    balance: Money,
    onAccountSelected: (Account?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accountLabel = selectedAccount?.name ?: accounts.singleOrNull()?.name ?: "Todas as contas"
    val displayedBalance = balance.displayValue()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Saldo de $accountLabel: $displayedBalance"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = accounts.isNotEmpty()) { menuExpanded = true }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(accountLabel, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (accounts.isNotEmpty()) {
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (accounts.size > 1) {
                    DropdownMenuItem(
                        text = { Text("Todas as contas") },
                        onClick = {
                            onAccountSelected(null)
                            menuExpanded = false
                        },
                    )
                }
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            onAccountSelected(account)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        Text(
            text = displayedBalance,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
        )
    }
}

@Composable
private fun BudgetCarousel(
    budgets: List<BudgetProgress>,
    onOpenBudgets: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("MEUS ORÇAMENTOS", Modifier.padding(horizontal = 24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            budgets.forEachIndexed { index, budget ->
                BudgetCard(budget, CategoryPalette[index % CategoryPalette.size], onOpenBudgets)
            }
            CreateBudgetCard(onOpenBudgets)
        }
    }
}

@Composable
private fun BudgetCard(budget: BudgetProgress, color: Color, onClick: () -> Unit) {
    val fraction = chartFraction(budget.spent.minor, budget.planned.minor)
    Column(
        modifier = Modifier
            .width(160.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceStrong)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                budget.categoryName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Divider, CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(color, CircleShape))
            }
            Text(
                budget.spent.displayValue(),
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
            Text(
                "de ${budget.planned.displayValue()}",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }
    }
}

@Composable
private fun CreateBudgetCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .height(105.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceStrong.copy(alpha = 0.35f))
            .drawBehind {
                drawRoundRect(
                    color = Divider,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Positive, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(10.dp))
        Text("Criar orçamento", color = Positive, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun CategorySpendingCard(shares: List<HomeCategoryShare>, currencyCode: String) {
    val totalMinor = shares.sumOf { it.amountMinor }
    val currency = Money(totalMinor, currencyCode).displayValue()
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("GASTO POR CATEGORIA")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceStrong, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                SpendingDonut(shares, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total gasto", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        currency,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (shares.isEmpty()) {
                    Text("Sem gastos", color = TextSecondary, fontSize = 12.sp)
                } else {
                    shares.forEach { share ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(share.color, CircleShape))
                            Text(
                                share.name,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Text(
                                "${(share.fraction * 100).roundToInt()}%",
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingDonut(shares: List<HomeCategoryShare>, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Distribuição dos gastos por categoria" }) {
        val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt)
        if (shares.isEmpty()) {
            drawArc(Divider, -90f, 360f, false, style = stroke)
            return@Canvas
        }
        var start = -90f
        shares.forEach { share ->
            val sweep = share.fraction * 360f
            drawArc(share.color, start, sweep, false, style = stroke)
            start += sweep
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(title)
        Text(
            action,
            color = Positive,
            fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = TextSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
        modifier = modifier,
    )
}

@Composable
private fun HomeTransactionRow(transaction: TransactionItem) {
    val positive = transaction.kind == TransactionKind.INCOME
    val accent = if (positive) Positive else Negative
    val icon: ImageVector = if (positive) Icons.Default.AccountBalanceWallet else Icons.Default.ShoppingBag
    val displayedAmount = transaction.amount.displayValue()
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(60.dp)
            .background(SurfaceSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${transaction.payee}, $displayedAmount, ${transaction.localDate}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    transaction.payee,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    transaction.categoryName ?: transaction.accountName,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = (if (positive) "+ " else "- ") + displayedAmount,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(homeDateLabel(transaction.localDate), color = TextTertiary, fontSize = 10.sp)
        }
    }
}

internal fun homeDateLabel(value: String, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
    return when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> {
            val locale = Locale.forLanguageTag("pt-PT")
            val (day, month) = date.format(DateTimeFormatter.ofPattern("d MMM", locale))
                .replace(".", "")
                .split(" ", limit = 2)
            "$day ${month.replaceFirstChar { it.uppercase(locale) }}"
        }
    }
}
