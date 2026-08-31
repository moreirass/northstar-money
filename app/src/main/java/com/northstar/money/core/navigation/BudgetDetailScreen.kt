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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.northstar.money.R
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val DetailBackground = Color(0xFF08080A)
private val DetailSurface = Color(0xFF18181C)
private val DetailRowSurface = Color(0xFF121215)
private val DetailBorder = Color(0xFF24242B)
private val DetailPrimary = Color.White
private val DetailSecondary = Color(0xFF8E8E9F)
private val DetailTertiary = Color(0xFF4F4F5F)
private val DetailPositive = Color(0xFF10B981)
private val DetailWarning = Color(0xFFF59E0B)
private val DetailNegative = Color(0xFFF43F5E)

internal data class BudgetTrendPoint(val month: YearMonth, val spentMinor: Long)

internal fun transactionsForBudgetMonth(
    items: List<TransactionItem>,
    categoryName: String,
    month: YearMonth,
): List<TransactionItem> = items.filter { item ->
    item.kind == TransactionKind.EXPENSE &&
        item.categoryName.equals(categoryName, ignoreCase = true) &&
        runCatching { YearMonth.from(LocalDate.parse(item.localDate)) }.getOrNull() == month
}

internal fun budgetTrend(
    items: List<TransactionItem>,
    categoryName: String,
    endMonth: YearMonth,
): List<BudgetTrendPoint> = (5 downTo 0).map { offset ->
    val month = endMonth.minusMonths(offset.toLong())
    BudgetTrendPoint(
        month = month,
        spentMinor = transactionsForBudgetMonth(items, categoryName, month).sumOf { it.amount.minor },
    )
}

internal fun budgetDetailStatusColor(spentMinor: Long, plannedMinor: Long): Color {
    if (plannedMinor <= 0L) return DetailPositive
    val percent = spentMinor * 100.0 / plannedMinor
    return when {
        percent >= 100.0 -> DetailNegative
        percent > 70.0 -> DetailWarning
        else -> DetailPositive
    }
}

@Composable
internal fun BudgetDetailScreen(
    budget: BudgetProgress?,
    transactions: List<TransactionItem>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onEditTransaction: (String) -> Unit,
) {
    val currentMonth = remember { YearMonth.now() }
    val currentTransactions = remember(transactions, budget, currentMonth) {
        budget?.let { transactionsForBudgetMonth(transactions, it.categoryName, currentMonth) }.orEmpty()
    }
    val trend = remember(transactions, budget, currentMonth) {
        budget?.let { budgetTrend(transactions, it.categoryName, currentMonth) }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DetailBackground).padding(padding),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { BudgetDetailHeader(budget?.categoryName.orEmpty(), onBack) }
        if (budget == null) {
            item {
                Text(
                    stringResource(R.string.budget_detail_not_found),
                    color = DetailSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        } else {
            item { BudgetHighlight(budget) }
            item { BudgetTrendPanel(trend, budget.planned.minor) }
            item {
                Text(
                    stringResource(R.string.budget_detail_transactions),
                    color = DetailSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (currentTransactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.budget_detail_empty_transactions),
                        color = DetailSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                items(currentTransactions, key = { it.id }) { transaction ->
                    BudgetDetailTransactionRow(transaction, onClick = { onEditTransaction(transaction.id) })
                }
            }
        }
    }
}

@Composable
private fun BudgetDetailHeader(categoryName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp).border(1.dp, DetailBorder, CircleShape).background(DetailRowSurface, CircleShape),
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.budget_detail_back),
                tint = DetailPositive,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(categoryName, color = DetailPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun BudgetHighlight(budget: BudgetProgress) {
    val percent = budgetUsagePercent(budget.spent.minor, budget.planned.minor)
    val statusColor = budgetDetailStatusColor(budget.spent.minor, budget.planned.minor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, DetailBorder, RoundedCornerShape(16.dp))
            .background(DetailSurface, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.budget_detail_spent_so_far), color = DetailSecondary, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    budget.spent.displayValue(),
                    color = statusColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    maxLines = 1,
                )
                Text(
                    "/ ${budget.planned.displayValue()}",
                    color = DetailSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Box(
            modifier = Modifier.size(64.dp).background(statusColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$percent%",
                color = statusColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun BudgetTrendPanel(points: List<BudgetTrendPoint>, plannedMinor: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val maximum = maxOf(plannedMinor, points.maxOfOrNull { it.spentMinor } ?: 0L, 1L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, DetailBorder, RoundedCornerShape(16.dp))
            .background(DetailRowSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.budget_detail_trend),
            color = DetailSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height((48f * point.spentMinor / maximum).coerceAtLeast(3f).dp)
                            .background(
                                budgetDetailStatusColor(point.spentMinor, plannedMinor),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                    Text(
                        point.month.month.getDisplayName(TextStyle.SHORT, locale).replace(".", ""),
                        color = DetailSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetDetailTransactionRow(transaction: TransactionItem, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DetailRowSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(DetailNegative.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = DetailNegative, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    transaction.payee,
                    color = DetailPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(transaction.categoryName.orEmpty(), color = DetailSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "- ${Money(abs(transaction.amount.minor), transaction.amount.currencyCode).displayValue()}",
                color = DetailNegative,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(budgetTransactionDateLabel(transaction, locale = locale), color = DetailTertiary, fontSize = 10.sp)
        }
    }
}

internal fun budgetTransactionDateLabel(
    item: TransactionItem,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val date = runCatching { LocalDate.parse(item.localDate) }.getOrNull() ?: return item.localDate
    val day = when (date) {
        today -> if (locale.language == "pt") "Hoje" else "Today"
        today.minusDays(1) -> if (locale.language == "pt") "Ontem" else "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", locale)).replace(".", "")
    }
    val time = if (item.createdAt > 0L) {
        Instant.ofEpochMilli(item.createdAt).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        "—"
    }
    return "$day, $time"
}
