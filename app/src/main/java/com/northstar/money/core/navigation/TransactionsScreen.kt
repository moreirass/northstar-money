package com.northstar.money.core.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.northstar.money.domain.model.Money
import com.northstar.money.R
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.feature.finance.FinanceUiState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TransactionsBackground = Color(0xFF08080A)
private val TransactionsSurface = Color(0xFF18181C)
private val TransactionRowSurface = Color(0xFF121215)
private val TransactionsDivider = Color(0xFF24242B)
private val TransactionsPrimary = Color.White
private val TransactionsSecondary = Color(0xFF8E8E9F)
private val TransactionsTertiary = Color(0xFF4F4F5F)
private val TransactionsPositive = Color(0xFF10B981)
private val TransactionsNegative = Color(0xFFF43F5E)
private val TransactionsBalance = Color(0xFF3B82F6)

internal data class TransactionMonthSummary(
    val expenses: Money,
    val income: Money,
) {
    val balance: Money get() = Money(income.minor - expenses.minor, income.currencyCode)
}

internal fun transactionsInMonth(items: List<TransactionItem>, month: YearMonth): List<TransactionItem> =
    items.filter { item ->
        runCatching { YearMonth.from(LocalDate.parse(item.localDate)) }.getOrNull() == month
    }

internal fun transactionMonthSummary(
    items: List<TransactionItem>,
    currencyCode: String,
): TransactionMonthSummary {
    val compatible = items.filter { it.amount.currencyCode == currencyCode }
    return TransactionMonthSummary(
        expenses = Money(compatible.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amount.minor }, currencyCode),
        income = Money(compatible.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amount.minor }, currencyCode),
    )
}

@Composable
internal fun ActivityScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onEdit: (String) -> Unit,
    onSetCleared: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = remember(monthText) { YearMonth.parse(monthText) }
    val monthTransactions = remember(state.transactions, month) { transactionsInMonth(state.transactions, month) }
    val currencyCode = state.summary.balance.currencyCode
    val summary = remember(monthTransactions, currencyCode) { transactionMonthSummary(monthTransactions, currencyCode) }
    val grouped = remember(monthTransactions) {
        monthTransactions.groupBy { it.localDate }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TransactionsBackground).padding(padding),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthSelector(
                month = month,
                onPrevious = { monthText = month.minusMonths(1).toString() },
                onNext = { monthText = month.plusMonths(1).toString() },
            )
        }
        item { MonthSummaryCard(summary) }
        if (grouped.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.transactions_empty_month),
                    color = TransactionsSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        } else {
            grouped.forEach { (date, transactions) ->
                item(key = "header-$date") { DayHeader(date) }
                items(transactions, key = { it.id }) { transaction ->
                    DesignedTransactionRow(
                        transaction = transaction,
                        onEdit = { onEdit(transaction.id) },
                        onSetCleared = { onSetCleared(transaction.id, !transaction.cleared) },
                        onDelete = { onDelete(transaction.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.transactions_previous_month),
                tint = TransactionsPositive,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            monthTitle(month, locale),
            color = TransactionsPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.2.sp,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.transactions_next_month),
                tint = TransactionsPositive,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun monthTitle(month: YearMonth, locale: Locale = Locale.getDefault()): String {
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
    return "${name.replaceFirstChar { it.titlecase(locale) }} ${month.year}"
}

@Composable
private fun MonthSummaryCard(summary: TransactionMonthSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TransactionsDivider, RoundedCornerShape(16.dp))
            .background(TransactionsSurface, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryValue(stringResource(R.string.transactions_expenses), "- ${summary.expenses.displayValue()}", TransactionsNegative, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(32.dp).background(TransactionsDivider))
        SummaryValue(stringResource(R.string.transactions_income), "+ ${summary.income.displayValue()}", TransactionsPositive, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(32.dp).background(TransactionsDivider))
        val balanceSign = if (summary.balance.minor >= 0) "+ " else "- "
        SummaryValue(
            stringResource(R.string.transactions_month_balance),
            balanceSign + Money(kotlin.math.abs(summary.balance.minor), summary.balance.currencyCode).displayValue(),
            TransactionsBalance,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TransactionsSecondary, fontSize = 11.sp, maxLines = 1)
        Text(
            value,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayHeader(localDate: String) {
    val date = runCatching { LocalDate.parse(localDate) }.getOrNull()
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            date?.format(DateTimeFormatter.ofPattern("d MMM", locale))?.replace(".", "") ?: localDate,
            color = TransactionsSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
        Text(
            date?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale)?.replaceFirstChar { it.titlecase(locale) }.orEmpty(),
            color = TransactionsTertiary,
            fontSize = 11.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesignedTransactionRow(
    transaction: TransactionItem,
    onEdit: () -> Unit,
    onSetCleared: () -> Unit,
    onDelete: () -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    val accent = when (transaction.kind) {
        TransactionKind.INCOME -> TransactionsPositive
        TransactionKind.EXPENSE -> TransactionsNegative
        TransactionKind.TRANSFER -> TransactionsBalance
    }
    val icon = transactionIcon(transaction)
    val displayedAmount = transaction.amount.displayValue()
    val signedAmount = when (transaction.kind) {
        TransactionKind.INCOME -> "+ $displayedAmount"
        TransactionKind.EXPENSE -> "- $displayedAmount"
        TransactionKind.TRANSFER -> displayedAmount
    }
    Box(Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TransactionRowSurface)
                .combinedClickable(onClick = onEdit, onLongClick = { actionsExpanded = true })
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${transaction.payee}, $signedAmount, ${transaction.localDate}"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        transaction.payee,
                        color = TransactionsPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        transaction.categoryName ?: transaction.accountName,
                        color = TransactionsSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    signedAmount,
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(transactionTime(transaction), color = TransactionsTertiary, fontSize = 10.sp)
            }
        }
        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (transaction.cleared) R.string.transactions_mark_pending
                            else R.string.transactions_mark_cleared,
                        )
                    )
                },
                onClick = {
                    actionsExpanded = false
                    onSetCleared()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.transactions_delete)) },
                leadingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                onClick = {
                    actionsExpanded = false
                    onDelete()
                },
            )
        }
    }
}

private fun transactionIcon(transaction: TransactionItem): ImageVector {
    if (transaction.kind == TransactionKind.INCOME) return Icons.Default.WorkOutline
    if (transaction.kind == TransactionKind.TRANSFER) return Icons.Default.SwapHoriz
    val category = transaction.categoryName.orEmpty().lowercase()
    return when {
        category.contains("aliment") || category.contains("food") -> Icons.Default.LocalCafe
        category.contains("lazer") || category.contains("entertain") -> Icons.Default.Tv
        category.contains("shopping") -> Icons.Default.ShoppingBag
        else -> Icons.Default.AccountBalanceWallet
    }
}

internal fun transactionTime(item: TransactionItem, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (item.createdAt <= 0L) return "—"
    return Instant.ofEpochMilli(item.createdAt).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
}
