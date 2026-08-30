package com.northstar.money.core.navigation

import android.annotation.SuppressLint
import com.northstar.money.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.northstar.money.NorthstarApplication
import com.northstar.money.domain.model.CategoryKind
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import com.northstar.money.domain.model.EditableAccount
import com.northstar.money.domain.model.EditableRecurring
import com.northstar.money.domain.model.EditableGoal
import com.northstar.money.domain.model.GoalContribution
import com.northstar.money.domain.model.SavingsGoal
import com.northstar.money.domain.model.DebtProfile
import com.northstar.money.feature.finance.FinanceUiState
import com.northstar.money.feature.finance.FinanceViewModel
import com.northstar.money.feature.finance.FinanceViewModelFactory
import com.northstar.money.data.backup.SecureBackupCodec
import kotlin.math.roundToInt

@Composable
internal fun SummaryCard(label: String, money: Money, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.accessibility_summary_amount, label, money.displayValue())
    Card(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(money.displayValue(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

internal fun chartFraction(value: Long, maximum: Long): Float =
    if (value <= 0L || maximum <= 0L) 0f else (value.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0).toFloat()

internal fun budgetUsagePercent(spent: Long, planned: Long): Int =
    if (spent <= 0L || planned <= 0L) 0
    else ((spent.toDouble() / planned.toDouble()) * 100.0).coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()

@Composable
internal fun BudgetProgressChart(budget: com.northstar.money.domain.model.BudgetProgress) {
    val fraction = chartFraction(budget.spent.minor, budget.planned.minor)
    val percent = budgetUsagePercent(budget.spent.minor, budget.planned.minor)
    val description = stringResource(
        R.string.accessibility_budget_usage,
        budget.categoryName,
        budget.spent.displayValue(),
        budget.planned.displayValue(),
        percent,
    )
    val progressColor = if (percent > 100) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(stringResource(R.string.budget_usage_percent, percent), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun IncomeExpenseChart(income: Money, expenses: Money) {
    val maximum = maxOf(income.minor, expenses.minor, 0L)
    val incomeFraction = chartFraction(income.minor, maximum)
    val expenseFraction = chartFraction(expenses.minor, maximum)
    val incomePercent = (incomeFraction * 100).roundToInt()
    val expensePercent = (expenseFraction * 100).roundToInt()
    val incomeDescription = stringResource(R.string.accessibility_income_chart, income.displayValue(), incomePercent)
    val expenseDescription = stringResource(R.string.accessibility_expense_chart, expenses.displayValue(), expensePercent)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { incomeFraction },
            modifier = Modifier.fillMaxWidth().height(12.dp).semantics {
                contentDescription = incomeDescription
                progressBarRangeInfo = ProgressBarRangeInfo(incomeFraction, 0f..1f)
            },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        LinearProgressIndicator(
            progress = { expenseFraction },
            modifier = Modifier.fillMaxWidth().height(12.dp).semantics {
                contentDescription = expenseDescription
                progressBarRangeInfo = ProgressBarRangeInfo(expenseFraction, 0f..1f)
            },
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun TransactionRow(item: TransactionItem, modifier: Modifier = Modifier) {
    val stateLabel = stringResource(if (item.cleared) R.string.ui_cleared_lowercase else R.string.ui_uncleared)
    val description = stringResource(
        R.string.accessibility_transaction,
        item.payee,
        item.categoryName ?: item.kind.name,
        item.accountName,
        item.amount.displayValue(),
        stateLabel,
    )
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.payee, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.transaction_metadata,
                    item.categoryName ?: item.kind.name,
                    item.accountName,
                    stateLabel,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val shown = if (item.kind == TransactionKind.EXPENSE) item.amount else item.amount
        Text(
            shown.displayValue(),
            color = if (item.kind == TransactionKind.INCOME) Color(0xFF087F5B) else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun PlanScreen(state: FinanceUiState, padding: PaddingValues, onSetBudget: (String, String) -> Unit) {
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.ui_monthly_plan), style = MaterialTheme.typography.headlineMedium) }
        val totalPlanned = state.budgets.sumOf { it.planned.minor }
        val totalSpent = state.budgets.sumOf { it.spent.minor }
        item { Text(stringResource(R.string.budget_spent_planned, Money(totalSpent).displayValue(), Money(totalPlanned).displayValue())) }
        if (state.budgets.isEmpty()) {
            item { EmptyStateCard(stringResource(R.string.state_empty_budgets)) }
        }
        items(state.budgets, key = { it.categoryId }) { budget ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(budget.categoryName, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.money_pair, budget.spent.displayValue(), budget.planned.displayValue()))
                        BudgetProgressChart(budget)
                        if (budget.rollover.minor != 0L) {
                            Text(
                                stringResource(R.string.budget_allocated_rollover, budget.allocated.displayValue(), budget.rollover.displayValue()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(onClick = { editingCategoryId = budget.categoryId }) { Text(stringResource(R.string.ui_set)) }
                }
            }
        }
    }
    editingCategoryId?.let { id ->
        val budget = state.budgets.firstOrNull { it.categoryId == id }
        if (budget != null) {
            AmountDialog(
                title = stringResource(R.string.budget_for_named, budget.categoryName),
                initial = if (budget.allocated.minor == 0L) "" else budget.allocated.minor.toBigDecimal().movePointLeft(2).toPlainString(),
                onDismiss = { editingCategoryId = null },
                onSave = { onSetBudget(id, it); editingCategoryId = null },
            )
        }
    }
}

@Composable
@SuppressLint("LocalContextGetResourceValueCall")
internal fun MoreScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onCreateAccount: (String, AccountType, String, String) -> Unit,
    onEditAccount: (String) -> Unit,
    onArchiveAccount: (String) -> Unit,
    onRestoreAccount: (String) -> Unit,
    onReconcile: (String, String, String, Boolean) -> Unit,
    onCreateGoal: (String, String, String, String?) -> Unit,
    onEditGoal: (String) -> Unit,
    onAddGoalContribution: (String, String, String, String) -> Unit,
    onEditGoalContribution: (String) -> Unit,
    onDeleteGoalContribution: (String) -> Unit,
    onRestoreGoalContribution: (String) -> Unit,
    onCreateRecurring: (String, TransactionKind, String, String, String?, String, String) -> Unit,
    onEditRecurring: (String) -> Unit,
    onPauseRecurring: (String) -> Unit,
    onResumeRecurring: (String) -> Unit,
    onDeleteRecurring: (String) -> Unit,
    onRestoreRecurring: (String) -> Unit,
    onCreateDebt: (String, String, String, String) -> Unit,
    onEditDebt: (String) -> Unit,
    onImportCsv: (String) -> Unit,
    onSetAppLock: (Boolean) -> Unit,
    onSetReminders: (Boolean) -> Unit,
    onSetMoneyValuesHidden: (Boolean) -> Unit,
    onShowOnboarding: () -> Unit,
    onCreateCategory: (String, CategoryKind) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onArchiveCategory: (String) -> Unit,
    onRestoreCategory: (String) -> Unit,
    onMergeCategory: (String, String) -> Unit,
    onUndoCategoryMerge: (String) -> Unit,
    onCreateFullBackup: suspend () -> String,
    onRestoreFullBackup: suspend (String, CharArray) -> Unit,
    onUndoFullRestore: suspend (CharArray) -> Unit,
    onRecoverTransaction: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                writer.appendLine("date,type,payee,category,account,amount,currency")
                state.transactions.forEach { transaction ->
                    val values = listOf(
                        transaction.localDate, transaction.kind.name, transaction.payee,
                        transaction.categoryName.orEmpty(), transaction.accountName,
                        transaction.amount.minor.toString(), transaction.amount.currencyCode,
                    ).joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
                    writer.appendLine(values)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                onImportCsv(reader.readText())
            }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val document = android.graphics.pdf.PdfDocument()
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val paint = android.graphics.Paint().apply { textSize = 18f; isAntiAlias = true }
            page.canvas.drawText(context.getString(R.string.ui_northstar_monthly_summary), 48f, 64f, paint)
            paint.textSize = 14f
            page.canvas.drawText(context.getString(R.string.report_balance, state.summary.balance.formatted()), 48f, 100f, paint)
            page.canvas.drawText(context.getString(R.string.report_income, state.summary.incomeThisMonth.formatted()), 48f, 126f, paint)
            page.canvas.drawText(context.getString(R.string.report_expenses, state.summary.expensesThisMonth.formatted()), 48f, 152f, paint)
            page.canvas.drawText(context.getString(R.string.report_projection, state.forecast.projectedBalance.formatted()), 48f, 178f, paint)
            var y = 220f
            state.budgets.take(15).forEach { budget ->
                page.canvas.drawText(
                    context.getString(R.string.report_budget_line, budget.categoryName, budget.spent.formatted(), budget.planned.formatted()),
                    48f, y, paint,
                )
                y += 22f
            }
            document.finishPage(page)
            context.contentResolver.openOutputStream(it)?.use(document::writeTo)
            document.close()
        }
    }
    val backupCodec = remember { SecureBackupCodec() }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf<CharArray?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestoreDocument by remember { mutableStateOf<String?>(null) }
    var pendingRestorePassword by remember { mutableStateOf<CharArray?>(null) }
    var showUndoRestorePasswordDialog by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingBackupPassword
        if (uri != null && password != null) {
            scope.launch {
                runSuspendCatching {
                    withContext(Dispatchers.IO) {
                        val encrypted = backupCodec.encrypt(onCreateFullBackup(), password)
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                            output.write(encrypted)
                        }
                    }
                }.onSuccess {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.backup_created),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.backup_failed, error.message ?: context.getString(R.string.unknown_error)),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }.also {
                    password.fill('\u0000')
                    pendingBackupPassword = null
                }
            }
        } else {
            password?.fill('\u0000')
            pendingBackupPassword = null
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePasswordDialog = true
        }
    }
    fun decryptSelectedBackup(password: CharArray) {
        val uri = pendingRestoreUri ?: return
        scope.launch {
            val result = runSuspendCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        backupCodec.decrypt(input.readBytes(), password)
                    }
                }
            }
            result.onSuccess { decrypted ->
                if (decrypted.trimStart().startsWith("{")) {
                    pendingRestoreDocument = decrypted
                    pendingRestorePassword = password
                    showRestoreConfirmation = true
                } else {
                    onImportCsv(decrypted)
                    password.fill('\u0000')
                }
            }.onFailure { error ->
                password.fill('\u0000')
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.backup_read_failed, error.message ?: context.getString(R.string.unknown_error)),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            pendingRestoreUri = null
        }
    }
    fun performFullRestore() {
        val document = pendingRestoreDocument ?: return
        val password = pendingRestorePassword ?: return
        pendingRestoreDocument = null
        pendingRestorePassword = null
        showRestoreConfirmation = false
        scope.launch {
            runSuspendCatching {
                withContext(Dispatchers.IO) { onRestoreFullBackup(document, password) }
            }.onSuccess {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.backup_restored),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.restore_unchanged, error.message ?: context.getString(R.string.restore_failed)),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }.also {
                password.fill('\u0000')
            }
        }
    }
    fun undoFullRestore(password: CharArray) {
        scope.launch {
            runSuspendCatching {
                withContext(Dispatchers.IO) { onUndoFullRestore(password) }
            }.onSuccess {
                android.widget.Toast.makeText(context, context.getString(R.string.toast_previous_data_restored), android.widget.Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.restore_undo_failed, error.message ?: context.getString(R.string.unknown_error)),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }.also {
                password.fill('\u0000')
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onSetReminders(granted) }
    var showCreate by remember { mutableStateOf(false) }
    var reconcileAccountId by remember { mutableStateOf<String?>(null) }
    var showGoal by remember { mutableStateOf(false) }
    var showRecurring by remember { mutableStateOf(false) }
    var showDebt by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var renameCategoryId by remember { mutableStateOf<String?>(null) }
    var archiveCategoryId by remember { mutableStateOf<String?>(null) }
    var mergeCategoryId by remember { mutableStateOf<String?>(null) }
    var archiveAccountId by remember { mutableStateOf<String?>(null) }
    var deleteRecurringId by remember { mutableStateOf<String?>(null) }
    var contributionGoalId by remember { mutableStateOf<String?>(null) }
    var deleteContributionId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) { Text(stringResource(R.string.ui_add_account)) }
            }
        }
        if (state.accounts.isEmpty()) item { EmptyStateCard(stringResource(R.string.state_empty_accounts)) }
        item { Text(stringResource(R.string.ui_settings), style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.appLockEnabled,
                        onClick = { onSetAppLock(!state.settings.appLockEnabled) },
                        label = { Text(stringResource(R.string.ui_biometric_device_credential_lock)) },
                    )
                    FilterChip(
                        selected = state.settings.remindersEnabled,
                        onClick = {
                            val enabled = !state.settings.remindersEnabled
                            if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetReminders(enabled)
                            }
                        },
                        label = { Text(stringResource(R.string.ui_daily_financial_review_reminders)) },
                    )
                    FilterChip(
                        selected = state.settings.moneyValuesHidden,
                        onClick = { onSetMoneyValuesHidden(!state.settings.moneyValuesHidden) },
                        label = {
                            Text(
                                stringResource(
                                    if (state.settings.moneyValuesHidden) R.string.money_show_values
                                    else R.string.money_hide_values,
                                ),
                            )
                        },
                    )
                    Text(stringResource(R.string.money_privacy_explanation), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ui_app_lock_applies_the_next_time_northstar_starts), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onShowOnboarding) {
                        Text(stringResource(R.string.onboarding_reopen))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCategory = true }) { Text(stringResource(R.string.ui_add)) }
            }
        }
        if (state.categories.isEmpty()) item { EmptyStateCard(stringResource(R.string.ui_no_active_categories)) }
        items(state.categories, key = { "category-${it.id}" }) { category ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            Text(category.kind.name.lowercase(), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { renameCategoryId = category.id }) { Text(stringResource(R.string.ui_rename)) }
                    }
                    Row {
                        TextButton(onClick = { archiveCategoryId = category.id }) { Text(stringResource(R.string.ui_archive)) }
                        if (state.categories.any { it.id != category.id && it.kind == category.kind }) {
                            TextButton(onClick = { mergeCategoryId = category.id }) { Text(stringResource(R.string.ui_merge)) }
                        }
                    }
                }
            }
        }
        if (state.archivedCategories.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_archived_categories), style = MaterialTheme.typography.titleMedium) }
            items(state.archivedCategories, key = { "archived-category-${it.id}" }) { category ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            Text(
                                category.mergedIntoCategoryName?.let {
                                    stringResource(R.string.category_merged_into, it)
                                } ?: stringResource(R.string.category_archived),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = {
                                if (category.mergedIntoCategoryId == null) {
                                    onRestoreCategory(category.id)
                                } else {
                                    onUndoCategoryMerge(category.id)
                                }
                            },
                        ) {
                            Text(stringResource(if (category.mergedIntoCategoryId == null) R.string.ui_restore else R.string.action_undo_merge))
                        }
                    }
                }
            }
        }
        if (state.deletedTransactions.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_recently_deleted), style = MaterialTheme.typography.titleLarge) }
            items(state.deletedTransactions, key = { "deleted-${it.id}" }) { transaction ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(transaction.payee, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.transaction_date_amount, transaction.localDate, transaction.amount.displayValue()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onRecoverTransaction(transaction.id) }) { Text(stringResource(R.string.ui_restore)) }
                    }
                }
            }
        }
        item {
            Column {
                TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) }) {
                    Text(stringResource(R.string.ui_import_transactions_from_csv))
                }
                TextButton(onClick = { exportLauncher.launch("northstar-transactions.csv") }) {
                    Text(stringResource(R.string.ui_export_transactions_to_csv))
                }
                TextButton(onClick = { pdfLauncher.launch("northstar-monthly-summary.pdf") }) {
                    Text(stringResource(R.string.ui_export_monthly_report_to_pdf))
                }
                TextButton(onClick = { showBackupPasswordDialog = true }) {
                    Text(stringResource(R.string.ui_create_password_protected_full_backup))
                }
                TextButton(onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text(stringResource(R.string.ui_restore_password_protected_backup))
                }
                TextButton(onClick = { showUndoRestorePasswordDialog = true }) {
                    Text(stringResource(R.string.ui_undo_last_full_restore))
                }
                state.importSummary?.let {
                    Text(
                        stringResource(R.string.import_result, it.imported, it.skippedDuplicates, it.errors),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Text(stringResource(R.string.ui_reports), style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val net = state.summary.incomeThisMonth.minor - state.summary.expensesThisMonth.minor
                    Text(stringResource(R.string.ui_income_versus_expenses), fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.report_income_value, state.summary.incomeThisMonth.displayValue()))
                    Text(stringResource(R.string.report_expenses_value, state.summary.expensesThisMonth.displayValue()))
                    IncomeExpenseChart(state.summary.incomeThisMonth, state.summary.expensesThisMonth)
                    Text(stringResource(R.string.report_net_value, Money(net).displayValue()), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text(stringResource(R.string.ui_financial_calendar), style = MaterialTheme.typography.titleLarge) }
        if (state.recurring.isEmpty()) item { EmptyStateCard(stringResource(R.string.ui_no_upcoming_scheduled_events)) }
        items(state.recurring.sortedBy { it.nextLocalDate }, key = { "calendar-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(item.nextLocalDate, Modifier.weight(0.35f))
                Text(item.name, Modifier.weight(0.4f))
                Text(item.amount.displayValue(), Modifier.weight(0.25f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_recurring), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showRecurring = true }) { Text(stringResource(R.string.ui_add)) }
            }
        }
        if (state.recurring.isEmpty()) item { EmptyStateCard(stringResource(R.string.ui_no_recurring_schedules_yet)) }
        items(state.recurring, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(
                            R.string.recurrence_schedule_summary,
                            item.amount.displayValue(),
                            item.intervalCount,
                            item.frequency.lowercase(),
                            item.nextLocalDate,
                        ),
                    )
                    Row {
                        TextButton(onClick = { onEditRecurring(item.id) }) { Text(stringResource(R.string.ui_edit)) }
                        TextButton(onClick = { onPauseRecurring(item.id) }) { Text(stringResource(R.string.ui_pause)) }
                        TextButton(onClick = { deleteRecurringId = item.id }) { Text(stringResource(R.string.ui_delete)) }
                    }
                }
            }
        }
        if (state.pausedRecurring.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_paused_recurrences), style = MaterialTheme.typography.titleMedium) }
            items(state.pausedRecurring, key = { "paused-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.recurrence_next, item.amount.displayValue(), item.nextLocalDate))
                        Row {
                            TextButton(onClick = { onEditRecurring(item.id) }) { Text(stringResource(R.string.ui_edit)) }
                            TextButton(onClick = { onResumeRecurring(item.id) }) { Text(stringResource(R.string.ui_resume)) }
                            TextButton(onClick = { deleteRecurringId = item.id }) { Text(stringResource(R.string.ui_delete)) }
                        }
                    }
                }
            }
        }
        if (state.deletedRecurring.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_recently_deleted_recurrences), style = MaterialTheme.typography.titleMedium) }
            items(state.deletedRecurring, key = { "deleted-recurring-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.ui_restores_paused_for_review), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRestoreRecurring(item.id) }) { Text(stringResource(R.string.ui_restore)) }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_debts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showDebt = true }) { Text(stringResource(R.string.ui_add)) }
            }
        }
        if (state.debts.isEmpty()) item { EmptyStateCard(stringResource(R.string.ui_no_debt_profiles_yet)) }
        items(state.debts, key = { it.id }) { debt ->
            val accountName = state.accounts.firstOrNull { it.id == debt.accountId }?.name
                ?: stringResource(R.string.fallback_account)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(accountName, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.debt_summary, debt.annualRateBasisPoints / 100.0, debt.minimumPayment.displayValue(), debt.dueDay))
                    TextButton(onClick = { onEditDebt(debt.id) }) { Text(stringResource(R.string.ui_edit)) }
                }
            }
        }
        items(state.accounts) { account ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.account_type_currency, account.type.name.lowercase(), account.currencyCode))
                        }
                        Text(account.balance.displayValue(), fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        stringResource(R.string.account_cleared_balance, account.clearedBalance.displayValue()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { reconcileAccountId = account.id }) { Text(stringResource(R.string.ui_reconcile)) }
                        TextButton(onClick = { onEditAccount(account.id) }) { Text(stringResource(R.string.ui_edit)) }
                        TextButton(onClick = { archiveAccountId = account.id }) { Text(stringResource(R.string.ui_archive)) }
                    }
                }
            }
        }
        if (state.archivedAccounts.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_archived_accounts), style = MaterialTheme.typography.titleMedium) }
            items(state.archivedAccounts, key = { "archived-account-${it.id}" }) { account ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.account_type_balance, account.type.name.lowercase(), account.balance.displayValue()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onRestoreAccount(account.id) }) { Text(stringResource(R.string.ui_restore)) }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_savings_goals), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showGoal = true }) { Text(stringResource(R.string.ui_add_goal)) }
            }
        }
        if (state.goals.isEmpty()) item { EmptyStateCard(stringResource(R.string.ui_no_savings_goals_yet)) }
        items(state.goals, key = { it.id }) { goal ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(goal.name, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.goal_progress, goal.saved.displayValue(), goal.target.displayValue()))
                    Text(goal.status.lowercase(), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { onEditGoal(goal.id) }) { Text(stringResource(R.string.ui_edit)) }
                        TextButton(onClick = { contributionGoalId = goal.id }) { Text(stringResource(R.string.ui_add_contribution)) }
                    }
                }
            }
        }
        if (state.goalContributions.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_goal_contributions), style = MaterialTheme.typography.titleMedium) }
            items(state.goalContributions, key = { "contribution-${it.id}" }) { contribution ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(contribution.goalName, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.amount_date, contribution.amount.displayValue(), contribution.localDate))
                        if (contribution.note.isNotBlank()) Text(contribution.note, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { onEditGoalContribution(contribution.id) }) { Text(stringResource(R.string.ui_edit)) }
                            TextButton(onClick = { deleteContributionId = contribution.id }) { Text(stringResource(R.string.ui_delete)) }
                        }
                    }
                }
            }
        }
        if (state.deletedGoalContributions.isNotEmpty()) {
            item { Text(stringResource(R.string.ui_recently_deleted_contributions), style = MaterialTheme.typography.titleMedium) }
            items(state.deletedGoalContributions, key = { "deleted-contribution-${it.id}" }) { contribution ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contribution.goalName, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.amount_date, contribution.amount.displayValue(), contribution.localDate))
                        }
                        TextButton(onClick = { onRestoreGoalContribution(contribution.id) }) { Text(stringResource(R.string.ui_restore)) }
                    }
                }
            }
        }
    }
    if (showBackupPasswordDialog) {
        BackupPasswordDialog(
            title = stringResource(R.string.backup_protect_title),
            confirmLabel = stringResource(R.string.backup_choose_file),
            requireConfirmation = true,
            onDismiss = { showBackupPasswordDialog = false },
            onConfirm = { password ->
                pendingBackupPassword = password
                showBackupPasswordDialog = false
                backupLauncher.launch("northstar-full-backup.nsmb")
            },
        )
    }
    if (showRestorePasswordDialog) {
        BackupPasswordDialog(
            title = stringResource(R.string.backup_unlock_title),
            confirmLabel = stringResource(R.string.action_unlock),
            requireConfirmation = false,
            onDismiss = {
                showRestorePasswordDialog = false
                pendingRestoreUri = null
            },
            onConfirm = { password ->
                showRestorePasswordDialog = false
                decryptSelectedBackup(password)
            },
        )
    }
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = {
                pendingRestorePassword?.fill('\u0000')
                pendingRestorePassword = null
                pendingRestoreDocument = null
                showRestoreConfirmation = false
            },
            title = { Text(stringResource(R.string.ui_replace_all_financial_data)) },
            text = {
                Text(stringResource(R.string.backup_replace_warning))
            },
            confirmButton = {
                TextButton(onClick = { performFullRestore() }) { Text(stringResource(R.string.ui_replace_data)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRestorePassword?.fill('\u0000')
                        pendingRestorePassword = null
                        pendingRestoreDocument = null
                        showRestoreConfirmation = false
                    },
                ) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
    if (showUndoRestorePasswordDialog) {
        BackupPasswordDialog(
            title = stringResource(R.string.backup_undo_title),
            confirmLabel = stringResource(R.string.action_undo),
            requireConfirmation = false,
            onDismiss = { showUndoRestorePasswordDialog = false },
            onConfirm = { password ->
                showUndoRestorePasswordDialog = false
                undoFullRestore(password)
            },
        )
    }
    if (showGoal) {
        GoalDialog(
            onDismiss = { showGoal = false },
            onSave = { name, target, saved, date ->
                onCreateGoal(name, target, saved, date)
                showGoal = false
            },
        )
    }
    if (showRecurring) {
        RecurringDialog(
            state = state,
            onDismiss = { showRecurring = false },
            onSave = { name, kind, amount, account, category, frequency, date ->
                onCreateRecurring(name, kind, amount, account, category, frequency, date)
                showRecurring = false
            },
        )
    }
    if (showDebt) {
        DebtDialog(
            state = state,
            onDismiss = { showDebt = false },
            onSave = { account, rate, payment, day ->
                onCreateDebt(account, rate, payment, day)
                showDebt = false
            },
        )
    }
    if (showCategory) {
        CategoryDialog(
            onDismiss = { showCategory = false },
            onSave = { name, kind ->
                onCreateCategory(name, kind)
                showCategory = false
            },
        )
    }

    renameCategoryId?.let { id ->
        val category = state.categories.firstOrNull { it.id == id }
        if (category != null) {
            RenameCategoryDialog(
                currentName = category.name,
                onDismiss = { renameCategoryId = null },
                onSave = { name ->
                    onRenameCategory(id, name)
                    renameCategoryId = null
                },
            )
        }
    }
    archiveCategoryId?.let { id ->
        val category = state.categories.firstOrNull { it.id == id }
        if (category != null) {
            AlertDialog(
                onDismissRequest = { archiveCategoryId = null },
                title = { Text(stringResource(R.string.confirm_archive_named, category.name)) },
                text = { Text(stringResource(R.string.ui_existing_transactions_keep_their_category_you_can_re)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onArchiveCategory(id)
                            archiveCategoryId = null
                        },
                    ) { Text(stringResource(R.string.ui_archive)) }
                },
                dismissButton = { TextButton(onClick = { archiveCategoryId = null }) { Text(stringResource(R.string.ui_cancel)) } },
            )
        }
    }
    mergeCategoryId?.let { sourceId ->
        val source = state.categories.firstOrNull { it.id == sourceId }
        if (source != null) {
            MergeCategoryDialog(
                sourceName = source.name,
                targets = state.categories.filter { it.id != sourceId && it.kind == source.kind },
                onDismiss = { mergeCategoryId = null },
                onMerge = { targetId ->
                    onMergeCategory(sourceId, targetId)
                    mergeCategoryId = null
                },
            )
        }
    }
    archiveAccountId?.let { id ->
        val account = state.accounts.firstOrNull { it.id == id }
        if (account != null) {
            AlertDialog(
                onDismissRequest = { archiveAccountId = null },
                title = { Text(stringResource(R.string.confirm_archive_named, account.name)) },
                text = {
                    Text(stringResource(R.string.archive_account_explanation))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onArchiveAccount(id)
                            archiveAccountId = null
                        },
                    ) { Text(stringResource(R.string.ui_archive)) }
                },
                dismissButton = { TextButton(onClick = { archiveAccountId = null }) { Text(stringResource(R.string.ui_cancel)) } },
            )
        }
    }
    deleteRecurringId?.let { id ->
        val item = (state.recurring + state.pausedRecurring).firstOrNull { it.id == id }
        if (item != null) {
            AlertDialog(
                onDismissRequest = { deleteRecurringId = null },
                title = { Text(stringResource(R.string.confirm_delete_named, item.name)) },
                text = {
                    Text(stringResource(R.string.ui_it_will_stop_affecting_forecasts_you_can_restore_it))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteRecurring(id)
                            deleteRecurringId = null
                        },
                    ) { Text(stringResource(R.string.ui_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleteRecurringId = null }) { Text(stringResource(R.string.ui_cancel)) } },
            )
        }
    }
    contributionGoalId?.let { goalId ->
        val goal = state.goals.firstOrNull { it.id == goalId }
        if (goal != null) {
            AddGoalContributionDialog(
                goal = goal,
                onDismiss = { contributionGoalId = null },
                onSave = { amount, date, note ->
                    onAddGoalContribution(goalId, amount, date, note)
                    contributionGoalId = null
                },
            )
        }
    }
    deleteContributionId?.let { id ->
        val contribution = state.goalContributions.firstOrNull { it.id == id }
        if (contribution != null) {
            AlertDialog(
                onDismissRequest = { deleteContributionId = null },
                title = { Text(stringResource(R.string.ui_delete_contribution)) },
                text = {
                    Text(stringResource(R.string.confirm_delete_contribution, contribution.amount.displayValue(), contribution.goalName))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteGoalContribution(id)
                            deleteContributionId = null
                        },
                    ) { Text(stringResource(R.string.ui_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleteContributionId = null }) { Text(stringResource(R.string.ui_cancel)) } },
            )
        }
    }

    if (showCreate) {
        AccountDialog(
            onDismiss = { showCreate = false },
            onSave = { name, type, opening, currency ->
                onCreateAccount(name, type, opening, currency)
                showCreate = false
            },
        )
    }
    reconcileAccountId?.let { id ->
        val account = state.accounts.firstOrNull { it.id == id }
        if (account != null) {
            ReconcileDialog(
                accountName = account.name,
                currentBalance = account.balance,
                clearedBalance = account.clearedBalance,
                onDismiss = { reconcileAccountId = null },
                onSave = { date, amount, adjustment ->
                    onReconcile(id, date, amount, adjustment)
                    reconcileAccountId = null
                },
            )
        }
    }
}
