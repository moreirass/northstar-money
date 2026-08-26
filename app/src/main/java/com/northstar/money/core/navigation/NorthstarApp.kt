package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Plan("Plan", Icons.Default.Assessment),
    Activity("Activity", Icons.AutoMirrored.Filled.ReceiptLong),
    More("More", Icons.Default.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NorthstarApp() {
    val application = LocalContext.current.applicationContext as NorthstarApplication
    val financeViewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModelFactory(application.financeRepository, application.userPreferences),
    )
    val state by financeViewModel.uiState.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.Home) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDeleteTransactionId by remember { mutableStateOf<String?>(null) }
    var editingTransaction by remember { mutableStateOf<EditableTransaction?>(null) }
    var editingAccount by remember { mutableStateOf<EditableAccount?>(null) }
    var editingRecurring by remember { mutableStateOf<EditableRecurring?>(null) }
    var editingGoal by remember { mutableStateOf<EditableGoal?>(null) }
    var editingGoalContribution by remember { mutableStateOf<GoalContribution?>(null) }
    var editingDebt by remember { mutableStateOf<DebtProfile?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(financeViewModel, snackbarHostState) {
        financeViewModel.events.collect { event ->
            snackbarHostState.showSnackbar(event.message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(destination.label) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            Destination.Home -> HomeScreen(state, padding)
            Destination.Plan -> PlanScreen(state, padding, financeViewModel::setBudget)
            Destination.Activity -> ActivityScreen(
                state = state,
                padding = padding,
                onEdit = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getTransactionForEdit(id) }
                            .onSuccess { editingTransaction = it }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    it.message ?: "Could not open the transaction for editing.",
                                )
                            }
                    }
                },
                onDelete = { id -> pendingDeleteTransactionId = id },
            )
            Destination.More -> MoreScreen(
                state = state,
                padding = padding,
                onCreateAccount = financeViewModel::createAccount,
                onEditAccount = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getAccountForEdit(id) }
                            .onSuccess { editingAccount = it }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    it.message ?: "Could not open the account for editing.",
                                )
                            }
                    }
                },
                onArchiveAccount = financeViewModel::archiveAccount,
                onRestoreAccount = financeViewModel::restoreAccount,
                onReconcile = financeViewModel::reconcile,
                onCreateGoal = financeViewModel::createGoal,
                onEditGoal = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getGoalForEdit(id) }
                            .onSuccess { editingGoal = it }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not edit the savings goal.") }
                    }
                },
                onAddGoalContribution = financeViewModel::addGoalContribution,
                onEditGoalContribution = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getGoalContributionForEdit(id) }
                            .onSuccess { editingGoalContribution = it }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not edit the contribution.") }
                    }
                },
                onDeleteGoalContribution = financeViewModel::deleteGoalContribution,
                onRestoreGoalContribution = financeViewModel::restoreGoalContribution,
                onCreateRecurring = financeViewModel::createRecurring,
                onEditRecurring = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getRecurringForEdit(id) }
                            .onSuccess { editingRecurring = it }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    it.message ?: "Could not open the recurring item for editing.",
                                )
                            }
                    }
                },
                onPauseRecurring = financeViewModel::pauseRecurring,
                onResumeRecurring = financeViewModel::resumeRecurring,
                onDeleteRecurring = financeViewModel::deleteRecurring,
                onRestoreRecurring = financeViewModel::restoreRecurring,
                onCreateDebt = financeViewModel::createDebt,
                onEditDebt = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getDebtForEdit(id) }
                            .onSuccess { editingDebt = it }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not edit the debt profile.") }
                    }
                },
                onImportCsv = financeViewModel::importCsv,
                onSetAppLock = financeViewModel::setAppLock,
                onSetReminders = financeViewModel::setReminders,
                onCreateCategory = financeViewModel::createCategory,
                onRenameCategory = financeViewModel::renameCategory,
                onArchiveCategory = financeViewModel::archiveCategory,
                onRestoreCategory = financeViewModel::restoreCategory,
                onMergeCategory = financeViewModel::mergeCategory,
                onUndoCategoryMerge = financeViewModel::undoCategoryMerge,
                onCreateFullBackup = financeViewModel::createFullBackup,
                onRestoreFullBackup = financeViewModel::restoreFullBackup,
                onUndoFullRestore = financeViewModel::undoLastFullRestore,
                onRecoverTransaction = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.restoreTransaction(id) }
                            .onSuccess { snackbarHostState.showSnackbar("Transaction restored") }
                            .onFailure { snackbarHostState.showSnackbar("Could not restore the transaction. Please try again.") }
                    }
                },
            )
        }
    }

    pendingDeleteTransactionId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTransactionId = null },
            title = { Text("Move transaction to Recently deleted?") },
            text = { Text("It will stop affecting balances and reports. You can restore it now or later from More.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteTransactionId = null
                        scope.launch {
                            runSuspendCatching { financeViewModel.deleteTransaction(id) }
                                .onSuccess {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Transaction moved to Recently deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        runSuspendCatching { financeViewModel.restoreTransaction(id) }
                                            .onFailure {
                                                snackbarHostState.showSnackbar(
                                                    "Could not restore the transaction. It remains in Recently deleted.",
                                                )
                                            }
                                    }
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar("Could not delete the transaction. Nothing was changed.")
                                }
                        }
                    },
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTransactionId = null }) { Text("Cancel") }
            },
        )
    }

    editingTransaction?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            state = state,
            onDismiss = { editingTransaction = null },
            onSave = {
                financeViewModel.updateTransaction(it)
                editingTransaction = null
            },
        )
    }

    editingAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onSave = {
                financeViewModel.updateAccount(it)
                editingAccount = null
            },
        )
    }

    editingRecurring?.let { recurring ->
        EditRecurringDialog(
            recurring = recurring,
            state = state,
            onDismiss = { editingRecurring = null },
            onSave = {
                financeViewModel.updateRecurring(it)
                editingRecurring = null
            },
        )
    }

    editingGoal?.let { goal ->
        EditGoalDialog(
            goal = goal,
            onDismiss = { editingGoal = null },
            onSave = {
                financeViewModel.updateGoal(it)
                editingGoal = null
            },
        )
    }

    editingGoalContribution?.let { contribution ->
        GoalContributionDialog(
            title = "Edit contribution",
            contribution = contribution,
            goals = state.goals,
            onDismiss = { editingGoalContribution = null },
            onSave = {
                financeViewModel.updateGoalContribution(it)
                editingGoalContribution = null
            },
        )
    }

    editingDebt?.let { debt ->
        EditDebtDialog(
            debt = debt,
            accountName = state.accounts.firstOrNull { it.id == debt.accountId }?.name ?: "Account",
            onDismiss = { editingDebt = null },
            onSave = {
                financeViewModel.updateDebt(it)
                editingDebt = null
            },
        )
    }

    if (showAdd) {
        AddTransactionSheet(
            state = state,
            onDismiss = { showAdd = false },
            onSave = { kind, amount, account, destinationAccount, category, payee ->
                if (kind == TransactionKind.TRANSFER) {
                    financeViewModel.transfer(amount, account, destinationAccount, payee)
                } else {
                    financeViewModel.addTransaction(kind, amount, account, category, payee)
                }
                showAdd = false
            },
        )
    }
}

@Composable
private fun HomeScreen(state: FinanceUiState, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Available now", style = MaterialTheme.typography.labelLarge)
            Text(state.summary.balance.formatted(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("30-day forecast", style = MaterialTheme.typography.titleMedium)
                    Text("Projected: ${state.forecast.projectedBalance.formatted()}")
                    Text("Lowest: ${state.forecast.lowestBalance.formatted()} on ${state.forecast.lowestDate}")
                    Text("${state.forecast.scheduledEvents} scheduled events included", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Income", state.summary.incomeThisMonth, Modifier.weight(1f))
                SummaryCard("Spent", state.summary.expensesThisMonth, Modifier.weight(1f))
            }
        }
        item { Text("Recent activity", style = MaterialTheme.typography.titleLarge) }
        if (state.transactions.isEmpty()) {
            item { Text("Add your first transaction to see your financial picture.") }
        } else {
            items(state.transactions.take(5), key = { it.id }) { TransactionRow(it) }
        }
    }
}

@Composable
private fun SummaryCard(label: String, money: Money, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(money.formatted(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ActivityScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visibleTransactions = state.transactions.filter {
        query.isBlank() || it.payee.contains(query, ignoreCase = true) ||
            it.categoryName.orEmpty().contains(query, ignoreCase = true) ||
            it.accountName.contains(query, ignoreCase = true)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search transactions") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        if (visibleTransactions.isEmpty()) item { Text(if (query.isBlank()) "No transactions yet." else "No matching transactions.") }
        items(visibleTransactions, key = { it.id }) { transaction ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransactionRow(transaction, Modifier.weight(1f))
                IconButton(onClick = { onEdit(transaction.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${transaction.payee}")
                }
                IconButton(onClick = { onDelete(transaction.id) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${transaction.payee}")
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TransactionRow(item: TransactionItem, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.payee, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("${item.categoryName ?: item.kind.name} • ${item.accountName}", style = MaterialTheme.typography.bodySmall)
        }
        val shown = if (item.kind == TransactionKind.EXPENSE) item.amount else item.amount
        Text(
            shown.formatted(),
            color = if (item.kind == TransactionKind.INCOME) Color(0xFF087F5B) else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlanScreen(state: FinanceUiState, padding: PaddingValues, onSetBudget: (String, String) -> Unit) {
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Monthly plan", style = MaterialTheme.typography.headlineMedium) }
        val totalPlanned = state.budgets.sumOf { it.planned.minor }
        val totalSpent = state.budgets.sumOf { it.spent.minor }
        item { Text("${Money(totalSpent).formatted()} spent of ${Money(totalPlanned).formatted()} planned") }
        items(state.budgets, key = { it.categoryId }) { budget ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(budget.categoryName, fontWeight = FontWeight.Medium)
                        Text("${budget.spent.formatted()} / ${budget.planned.formatted()}")
                        if (budget.rollover.minor != 0L) {
                            Text(
                                "Allocated ${budget.allocated.formatted()} • rollover ${budget.rollover.formatted()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(onClick = { editingCategoryId = budget.categoryId }) { Text("Set") }
                }
            }
        }
    }
    editingCategoryId?.let { id ->
        val budget = state.budgets.firstOrNull { it.categoryId == id }
        if (budget != null) {
            AmountDialog(
                title = "Budget for ${budget.categoryName}",
                initial = if (budget.allocated.minor == 0L) "" else budget.allocated.minor.toBigDecimal().movePointLeft(2).toPlainString(),
                onDismiss = { editingCategoryId = null },
                onSave = { onSetBudget(id, it); editingCategoryId = null },
            )
        }
    }
}

@Composable
private fun MoreScreen(
    state: FinanceUiState,
    padding: PaddingValues,
    onCreateAccount: (String, AccountType, String) -> Unit,
    onEditAccount: (String) -> Unit,
    onArchiveAccount: (String) -> Unit,
    onRestoreAccount: (String) -> Unit,
    onReconcile: (String, String, Boolean) -> Unit,
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
            page.canvas.drawText("Northstar monthly summary", 48f, 64f, paint)
            paint.textSize = 14f
            page.canvas.drawText("Balance: ${state.summary.balance.formatted()}", 48f, 100f, paint)
            page.canvas.drawText("Income: ${state.summary.incomeThisMonth.formatted()}", 48f, 126f, paint)
            page.canvas.drawText("Expenses: ${state.summary.expensesThisMonth.formatted()}", 48f, 152f, paint)
            page.canvas.drawText("30-day projection: ${state.forecast.projectedBalance.formatted()}", 48f, 178f, paint)
            var y = 220f
            state.budgets.take(15).forEach { budget ->
                page.canvas.drawText(
                    "${budget.categoryName}: ${budget.spent.formatted()} / ${budget.planned.formatted()}",
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
                        "Complete database backup created",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    android.widget.Toast.makeText(
                        context,
                        "Backup failed: ${error.message ?: "unknown error"}",
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
                    "Backup could not be read: ${error.message ?: "unknown error"}",
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
                    "Full backup restored. Undo is available on this screen.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                android.widget.Toast.makeText(
                    context,
                    "Nothing was changed: ${error.message ?: "restore failed"}",
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
                android.widget.Toast.makeText(context, "Previous data restored", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                android.widget.Toast.makeText(
                    context,
                    "Undo failed: ${error.message ?: "unknown error"}",
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
                Text("Accounts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) { Text("Add account") }
            }
        }
        item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.appLockEnabled,
                        onClick = { onSetAppLock(!state.settings.appLockEnabled) },
                        label = { Text("Biometric/device-credential lock") },
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
                        label = { Text("Daily financial review reminders") },
                    )
                    Text("App lock applies the next time Northstar starts.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCategory = true }) { Text("Add") }
            }
        }
        if (state.categories.isEmpty()) item { Text("No active categories.") }
        items(state.categories, key = { "category-${it.id}" }) { category ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            Text(category.kind.name.lowercase(), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { renameCategoryId = category.id }) { Text("Rename") }
                    }
                    Row {
                        TextButton(onClick = { archiveCategoryId = category.id }) { Text("Archive") }
                        if (state.categories.any { it.id != category.id && it.kind == category.kind }) {
                            TextButton(onClick = { mergeCategoryId = category.id }) { Text("Merge") }
                        }
                    }
                }
            }
        }
        if (state.archivedCategories.isNotEmpty()) {
            item { Text("Archived categories", style = MaterialTheme.typography.titleMedium) }
            items(state.archivedCategories, key = { "archived-category-${it.id}" }) { category ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            Text(
                                category.mergedIntoCategoryName?.let { "Merged into $it" } ?: "Archived",
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
                        ) { Text(if (category.mergedIntoCategoryId == null) "Restore" else "Undo merge") }
                    }
                }
            }
        }
        if (state.deletedTransactions.isNotEmpty()) {
            item { Text("Recently deleted", style = MaterialTheme.typography.titleLarge) }
            items(state.deletedTransactions, key = { "deleted-${it.id}" }) { transaction ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(transaction.payee, fontWeight = FontWeight.Medium)
                            Text(
                                "${transaction.localDate} • ${transaction.amount.formatted()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onRecoverTransaction(transaction.id) }) { Text("Restore") }
                    }
                }
            }
        }
        item {
            Column {
                TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv")) }) {
                    Text("Import transactions from CSV")
                }
                TextButton(onClick = { exportLauncher.launch("northstar-transactions.csv") }) {
                    Text("Export transactions to CSV")
                }
                TextButton(onClick = { pdfLauncher.launch("northstar-monthly-summary.pdf") }) {
                    Text("Export monthly report to PDF")
                }
                TextButton(onClick = { showBackupPasswordDialog = true }) {
                    Text("Create password-protected full backup")
                }
                TextButton(onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("Restore password-protected backup")
                }
                TextButton(onClick = { showUndoRestorePasswordDialog = true }) {
                    Text("Undo last full restore")
                }
                state.importMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { Text("Reports", style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val net = state.summary.incomeThisMonth.minor - state.summary.expensesThisMonth.minor
                    Text("Income versus expenses", fontWeight = FontWeight.Medium)
                    Text("Income ${state.summary.incomeThisMonth.formatted()}")
                    Text("Expenses ${state.summary.expensesThisMonth.formatted()}")
                    Text("Net ${Money(net).formatted()}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("Financial calendar", style = MaterialTheme.typography.titleLarge) }
        if (state.recurring.isEmpty()) item { Text("No upcoming scheduled events.") }
        items(state.recurring.sortedBy { it.nextLocalDate }, key = { "calendar-${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(item.nextLocalDate, Modifier.weight(0.35f))
                Text(item.name, Modifier.weight(0.4f))
                Text(item.amount.formatted(), Modifier.weight(0.25f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recurring", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showRecurring = true }) { Text("Add") }
            }
        }
        if (state.recurring.isEmpty()) item { Text("No recurring schedules yet.") }
        items(state.recurring, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${item.amount.formatted()} • every ${item.intervalCount} ${item.frequency.lowercase()} period(s) • next ${item.nextLocalDate}",
                    )
                    Row {
                        TextButton(onClick = { onEditRecurring(item.id) }) { Text("Edit") }
                        TextButton(onClick = { onPauseRecurring(item.id) }) { Text("Pause") }
                        TextButton(onClick = { deleteRecurringId = item.id }) { Text("Delete") }
                    }
                }
            }
        }
        if (state.pausedRecurring.isNotEmpty()) {
            item { Text("Paused recurrences", style = MaterialTheme.typography.titleMedium) }
            items(state.pausedRecurring, key = { "paused-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, fontWeight = FontWeight.Medium)
                        Text("${item.amount.formatted()} • next ${item.nextLocalDate}")
                        Row {
                            TextButton(onClick = { onEditRecurring(item.id) }) { Text("Edit") }
                            TextButton(onClick = { onResumeRecurring(item.id) }) { Text("Resume") }
                            TextButton(onClick = { deleteRecurringId = item.id }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        if (state.deletedRecurring.isNotEmpty()) {
            item { Text("Recently deleted recurrences", style = MaterialTheme.typography.titleMedium) }
            items(state.deletedRecurring, key = { "deleted-recurring-${it.id}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text("Restores paused for review", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRestoreRecurring(item.id) }) { Text("Restore") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Debts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showDebt = true }) { Text("Add") }
            }
        }
        if (state.debts.isEmpty()) item { Text("No debt profiles yet.") }
        items(state.debts, key = { it.id }) { debt ->
            val accountName = state.accounts.firstOrNull { it.id == debt.accountId }?.name ?: "Account"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(accountName, fontWeight = FontWeight.Medium)
                    Text("${debt.annualRateBasisPoints / 100.0}% APR • minimum ${debt.minimumPayment.formatted()} • due day ${debt.dueDay}")
                    TextButton(onClick = { onEditDebt(debt.id) }) { Text("Edit") }
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
                            Text("${account.type.name.lowercase()} • ${account.currencyCode}")
                        }
                        Text(account.balance.formatted(), fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        TextButton(onClick = { reconcileAccountId = account.id }) { Text("Reconcile") }
                        TextButton(onClick = { onEditAccount(account.id) }) { Text("Edit") }
                        TextButton(onClick = { archiveAccountId = account.id }) { Text("Archive") }
                    }
                }
            }
        }
        if (state.archivedAccounts.isNotEmpty()) {
            item { Text("Archived accounts", style = MaterialTheme.typography.titleMedium) }
            items(state.archivedAccounts, key = { "archived-account-${it.id}" }) { account ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${account.type.name.lowercase()} • ${account.balance.formatted()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onRestoreAccount(account.id) }) { Text("Restore") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Savings goals", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showGoal = true }) { Text("Add goal") }
            }
        }
        if (state.goals.isEmpty()) item { Text("No savings goals yet.") }
        items(state.goals, key = { it.id }) { goal ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(goal.name, fontWeight = FontWeight.Medium)
                    Text("${goal.saved.formatted()} of ${goal.target.formatted()}")
                    Text(goal.status.lowercase(), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { onEditGoal(goal.id) }) { Text("Edit") }
                        TextButton(onClick = { contributionGoalId = goal.id }) { Text("Add contribution") }
                    }
                }
            }
        }
        if (state.goalContributions.isNotEmpty()) {
            item { Text("Goal contributions", style = MaterialTheme.typography.titleMedium) }
            items(state.goalContributions, key = { "contribution-${it.id}" }) { contribution ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(contribution.goalName, fontWeight = FontWeight.Medium)
                        Text("${contribution.amount.formatted()} • ${contribution.localDate}")
                        if (contribution.note.isNotBlank()) Text(contribution.note, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { onEditGoalContribution(contribution.id) }) { Text("Edit") }
                            TextButton(onClick = { deleteContributionId = contribution.id }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        if (state.deletedGoalContributions.isNotEmpty()) {
            item { Text("Recently deleted contributions", style = MaterialTheme.typography.titleMedium) }
            items(state.deletedGoalContributions, key = { "deleted-contribution-${it.id}" }) { contribution ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(contribution.goalName, fontWeight = FontWeight.Medium)
                            Text("${contribution.amount.formatted()} • ${contribution.localDate}")
                        }
                        TextButton(onClick = { onRestoreGoalContribution(contribution.id) }) { Text("Restore") }
                    }
                }
            }
        }
    }
    if (showBackupPasswordDialog) {
        BackupPasswordDialog(
            title = "Protect backup",
            confirmLabel = "Choose file",
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
            title = "Unlock backup",
            confirmLabel = "Unlock",
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
            title = { Text("Replace all financial data?") },
            text = {
                Text(
                    "All current accounts, transactions, budgets, goals and schedules will be replaced. " +
                        "An encrypted recovery copy will be saved on this device before anything changes.",
                )
            },
            confirmButton = {
                TextButton(onClick = { performFullRestore() }) { Text("Replace data") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRestorePassword?.fill('\u0000')
                        pendingRestorePassword = null
                        pendingRestoreDocument = null
                        showRestoreConfirmation = false
                    },
                ) { Text("Cancel") }
            },
        )
    }
    if (showUndoRestorePasswordDialog) {
        BackupPasswordDialog(
            title = "Undo last restore",
            confirmLabel = "Undo",
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
                title = { Text("Archive ${category.name}?") },
                text = { Text("Existing transactions keep their category. You can restore it from Archived categories.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onArchiveCategory(id)
                            archiveCategoryId = null
                        },
                    ) { Text("Archive") }
                },
                dismissButton = { TextButton(onClick = { archiveCategoryId = null }) { Text("Cancel") } },
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
                title = { Text("Archive ${account.name}?") },
                text = {
                    Text(
                        "The account and its history remain stored. It will stop contributing to active totals, forecasts, recurrences and debts until restored.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onArchiveAccount(id)
                            archiveAccountId = null
                        },
                    ) { Text("Archive") }
                },
                dismissButton = { TextButton(onClick = { archiveAccountId = null }) { Text("Cancel") } },
            )
        }
    }
    deleteRecurringId?.let { id ->
        val item = (state.recurring + state.pausedRecurring).firstOrNull { it.id == id }
        if (item != null) {
            AlertDialog(
                onDismissRequest = { deleteRecurringId = null },
                title = { Text("Delete ${item.name}?") },
                text = {
                    Text("It will stop affecting forecasts. You can restore it later from Recently deleted recurrences.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteRecurring(id)
                            deleteRecurringId = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteRecurringId = null }) { Text("Cancel") } },
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
                title = { Text("Delete contribution?") },
                text = {
                    Text("${contribution.amount.formatted()} will stop counting toward ${contribution.goalName}. You can restore it later.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteGoalContribution(id)
                            deleteContributionId = null
                        },
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteContributionId = null }) { Text("Cancel") } },
            )
        }
    }

    if (showCreate) {
        AccountDialog(
            onDismiss = { showCreate = false },
            onSave = { name, type, opening ->
                onCreateAccount(name, type, opening)
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
                onDismiss = { reconcileAccountId = null },
                onSave = { amount, adjustment ->
                    onReconcile(id, amount, adjustment)
                    reconcileAccountId = null
                },
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val lengthIsValid = password.length in
        com.northstar.money.data.backup.PortableBackupCodec.MIN_PASSWORD_LENGTH..
            com.northstar.money.data.backup.PortableBackupCodec.MAX_PASSWORD_LENGTH
    val passwordsMatch = !requireConfirmation || password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use 12–128 characters. Losing this password makes the backup unrecoverable.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmation.isNotEmpty() && !passwordsMatch,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password.toCharArray()) },
                enabled = lengthIsValid && passwordsMatch,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryDialog(onDismiss: () -> Unit, onSave: (String, CategoryKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryKind.entries.forEach { option ->
                        FilterChip(kind == option, { kind = option }, { Text(option.name.lowercase()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameCategoryDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergeCategoryDialog(
    sourceName: String,
    targets: List<Category>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    var targetId by remember(targets) { mutableStateOf(targets.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge $sourceName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Past activity will appear under the selected category. $sourceName will be archived and the merge can be undone.",
                )
                targets.forEach { target ->
                    FilterChip(
                        selected = target.id == targetId,
                        onClick = { targetId = target.id },
                        label = { Text(target.name) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { targetId?.let(onMerge) },
                enabled = targetId != null,
            ) { Text("Merge") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditTransactionDialog(
    transaction: EditableTransaction,
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (EditableTransaction) -> Unit,
) {
    var amount by remember { mutableStateOf(transaction.amount.minor.toBigDecimal().movePointLeft(2).toPlainString()) }
    var localDate by remember { mutableStateOf(transaction.localDate) }
    var payee by remember { mutableStateOf(transaction.payee) }
    var note by remember { mutableStateOf(transaction.note) }
    var accountId by remember { mutableStateOf(transaction.accountId) }
    var categoryId by remember { mutableStateOf(transaction.categoryId) }
    var destinationAccountId by remember { mutableStateOf(transaction.destinationAccountId) }
    val accounts = state.accounts.filter { it.currencyCode == transaction.amount.currencyCode }
    val categories = state.categories.filter { it.kind.name == transaction.kind.name }
    val parsedAmount = runCatching { Money.parseMajor(amount, transaction.amount.currencyCode) }.getOrNull()
    val valid = parsedAmount?.minor?.let { it > 0 } == true &&
        runCatching { java.time.LocalDate.parse(localDate) }.isSuccess &&
        accounts.any { it.id == accountId } &&
        if (transaction.kind == TransactionKind.TRANSFER) {
            destinationAccountId != null && destinationAccountId != accountId &&
                accounts.any { it.id == destinationAccountId }
        } else {
            categoryId != null && categories.any { it.id == categoryId }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${transaction.kind.name.lowercase()}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount (${transaction.amount.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(localDate, { localDate = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(payee, { payee = it }, label = { Text("Payee") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
                Text(if (transaction.kind == TransactionKind.TRANSFER) "From account" else "Account")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == accountId,
                            onClick = { accountId = account.id },
                            label = { Text(account.name) },
                        )
                    }
                }
                if (transaction.kind == TransactionKind.TRANSFER) {
                    Text("To account")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.filter { it.id != accountId }.forEach { account ->
                            FilterChip(
                                selected = account.id == destinationAccountId,
                                onClick = { destinationAccountId = account.id },
                                label = { Text(account.name) },
                            )
                        }
                    }
                } else {
                    Text("Category")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = category.id == categoryId,
                                onClick = { categoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        transaction.copy(
                            localDate = localDate,
                            payee = payee,
                            note = note,
                            amount = requireNotNull(parsedAmount),
                            accountId = accountId,
                            categoryId = categoryId,
                            destinationAccountId = destinationAccountId,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditRecurringDialog(
    recurring: EditableRecurring,
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (EditableRecurring) -> Unit,
) {
    val fractionDigits = java.util.Currency.getInstance(recurring.amount.currencyCode).defaultFractionDigits
    var name by remember(recurring.id) { mutableStateOf(recurring.name) }
    var kind by remember(recurring.id) { mutableStateOf(recurring.kind) }
    var amount by remember(recurring.id) {
        mutableStateOf(recurring.amount.minor.toBigDecimal().movePointLeft(fractionDigits).toPlainString())
    }
    var accountId by remember(recurring.id) { mutableStateOf(recurring.accountId) }
    var categoryId by remember(recurring.id) { mutableStateOf(recurring.categoryId) }
    var frequency by remember(recurring.id) { mutableStateOf(recurring.frequency) }
    var intervalCount by remember(recurring.id) { mutableStateOf(recurring.intervalCount.toString()) }
    var nextDate by remember(recurring.id) { mutableStateOf(recurring.nextLocalDate) }
    val accounts = state.accounts.filter { it.currencyCode == recurring.amount.currencyCode }
    val requiredCategoryKind = if (kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == requiredCategoryKind }
    val parsedAmount = runCatching { Money.parseMajor(amount, recurring.amount.currencyCode) }.getOrNull()
    val parsedInterval = intervalCount.toIntOrNull()
    val valid = name.isNotBlank() && parsedAmount?.minor?.let { it > 0 } == true &&
        parsedInterval?.let { it > 0 } == true &&
        runCatching { java.time.LocalDate.parse(nextDate) }.isSuccess &&
        accounts.any { it.id == accountId } && categories.any { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit recurrence") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TransactionKind.EXPENSE, TransactionKind.INCOME).forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                val targetKind = if (option == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                                categoryId = state.categories.firstOrNull { it.kind == targetKind }?.id
                            },
                            label = { Text(option.name.lowercase()) },
                        )
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount (${recurring.amount.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("Account")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChip(account.id == accountId, { accountId = account.id }, { Text(account.name) })
                    }
                }
                Text("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(category.id == categoryId, { categoryId = category.id }, { Text(category.name) })
                    }
                }
                Text("Frequency")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEEKLY", "MONTHLY", "YEARLY").forEach { option ->
                        FilterChip(frequency == option, { frequency = option }, { Text(option.lowercase()) })
                    }
                }
                OutlinedTextField(
                    intervalCount,
                    { intervalCount = it },
                    label = { Text("Every N periods") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    nextDate,
                    { nextDate = it },
                    label = { Text("Next date (YYYY-MM-DD)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        recurring.copy(
                            name = name,
                            kind = kind,
                            amount = requireNotNull(parsedAmount),
                            accountId = accountId,
                            categoryId = categoryId,
                            frequency = frequency,
                            intervalCount = requireNotNull(parsedInterval),
                            nextLocalDate = nextDate,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecurringDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (String, TransactionKind, String, String, String?, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().plusMonths(1).toString()) }
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    val account = state.accounts.firstOrNull()
    val category = state.categories.firstOrNull {
        it.kind == if (kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recurring transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Next date (YYYY-MM-DD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == TransactionKind.EXPENSE, { kind = TransactionKind.EXPENSE }, { Text("Expense") })
                    FilterChip(kind == TransactionKind.INCOME, { kind = TransactionKind.INCOME }, { Text("Income") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEEKLY", "MONTHLY", "YEARLY").forEach {
                        FilterChip(frequency == it, { frequency = it }, { Text(it.lowercase()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, kind, amount, account!!.id, category?.id, frequency, date) },
                enabled = name.isNotBlank() && account != null && category != null &&
                    runCatching { Money.parseMajor(amount).minor > 0 }.getOrDefault(false) &&
                    runCatching { java.time.LocalDate.parse(date) }.isSuccess,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DebtDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var rate by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("1") }
    val account = state.accounts.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debt profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Account: ${account?.name ?: "Create an account first"}")
                OutlinedTextField(rate, { rate = it }, label = { Text("Annual rate (%)") }, singleLine = true)
                OutlinedTextField(payment, { payment = it }, label = { Text("Minimum payment") }, singleLine = true)
                OutlinedTextField(dueDay, { dueDay = it }, label = { Text("Due day") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(account!!.id, rate, payment, dueDay) },
                enabled = account != null && rate.toBigDecimalOrNull() != null &&
                    runCatching { Money.parseMajor(payment).minor >= 0 }.getOrDefault(false) &&
                    dueDay.toIntOrNull() in 1..31,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditDebtDialog(
    debt: DebtProfile,
    accountName: String,
    onDismiss: () -> Unit,
    onSave: (DebtProfile) -> Unit,
) {
    val fractionDigits = java.util.Currency.getInstance(debt.minimumPayment.currencyCode).defaultFractionDigits
    var rate by remember(debt.id) {
        mutableStateOf(debt.annualRateBasisPoints.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString())
    }
    var payment by remember(debt.id) {
        mutableStateOf(
            debt.minimumPayment.minor.toBigDecimal().movePointLeft(fractionDigits).toPlainString(),
        )
    }
    var dueDay by remember(debt.id) { mutableStateOf(debt.dueDay.toString()) }
    val basisPoints = runCatching { rate.toBigDecimal().movePointRight(2).intValueExact() }.getOrNull()
    val parsedPayment = runCatching {
        Money.parseMajor(payment, debt.minimumPayment.currencyCode)
    }.getOrNull()
    val parsedDueDay = dueDay.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit debt profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Account: $accountName")
                OutlinedTextField(
                    rate,
                    { rate = it },
                    label = { Text("Annual rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    payment,
                    { payment = it },
                    label = { Text("Minimum payment (${debt.minimumPayment.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    dueDay,
                    { dueDay = it },
                    label = { Text("Due day") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        debt.copy(
                            annualRateBasisPoints = requireNotNull(basisPoints),
                            minimumPayment = requireNotNull(parsedPayment),
                            dueDay = requireNotNull(parsedDueDay),
                        ),
                    )
                },
                enabled = basisPoints?.let { it >= 0 } == true &&
                    parsedPayment?.minor?.let { it >= 0 } == true && parsedDueDay in 1..31,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AmountDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var amount by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                amount, { amount = it }, label = { Text("Amount (EUR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount) },
                enabled = runCatching { Money.parseMajor(amount).minor >= 0 }.getOrDefault(false),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalDialog(onDismiss: () -> Unit, onSave: (String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New savings goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Target amount") }, singleLine = true)
                OutlinedTextField(saved, { saved = it }, label = { Text("Already saved") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Target date (YYYY-MM-DD, optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, target, saved, date.ifBlank { null }) },
                enabled = name.isNotBlank() &&
                    runCatching { Money.parseMajor(target).minor > 0 }.getOrDefault(false) &&
                    runCatching { Money.parseMajor(saved).minor >= 0 }.getOrDefault(false) &&
                    (date.isBlank() || runCatching { java.time.LocalDate.parse(date) }.isSuccess),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditGoalDialog(
    goal: EditableGoal,
    onDismiss: () -> Unit,
    onSave: (EditableGoal) -> Unit,
) {
    val fractionDigits = java.util.Currency.getInstance(goal.target.currencyCode).defaultFractionDigits
    var name by remember(goal.id) { mutableStateOf(goal.name) }
    var target by remember(goal.id) {
        mutableStateOf(goal.target.minor.toBigDecimal().movePointLeft(fractionDigits).toPlainString())
    }
    var date by remember(goal.id) { mutableStateOf(goal.targetLocalDate.orEmpty()) }
    var status by remember(goal.id) { mutableStateOf(goal.status) }
    val parsedTarget = runCatching { Money.parseMajor(target, goal.target.currencyCode) }.getOrNull()
    val validDate = date.isBlank() || runCatching { java.time.LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit savings goal") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, singleLine = true)
                OutlinedTextField(
                    target,
                    { target = it },
                    label = { Text("Target amount (${goal.target.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text("Target date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                )
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ACTIVE" to "Active", "PAUSED" to "Paused", "COMPLETED" to "Completed")
                        .forEach { (value, label) ->
                            FilterChip(status == value, { status = value }, { Text(label) })
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        goal.copy(
                            name = name.trim(),
                            target = requireNotNull(parsedTarget),
                            targetLocalDate = date.trim().ifBlank { null },
                            status = status,
                        ),
                    )
                },
                enabled = name.isNotBlank() && parsedTarget?.minor?.let { it > 0 } == true && validDate,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddGoalContributionDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var amount by remember(goal.id) { mutableStateOf("") }
    var date by remember(goal.id) { mutableStateOf(java.time.LocalDate.now().toString()) }
    var note by remember(goal.id) { mutableStateOf("") }
    val parsedAmount = runCatching { Money.parseMajor(amount, goal.target.currencyCode) }.getOrNull()
    val validDate = runCatching { java.time.LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contribution") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goal.name, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount (${goal.target.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount, date.trim(), note.trim()) },
                enabled = parsedAmount?.minor?.let { it > 0 } == true && validDate && note.length <= 500,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalContributionDialog(
    title: String,
    contribution: GoalContribution,
    goals: List<SavingsGoal>,
    onDismiss: () -> Unit,
    onSave: (GoalContribution) -> Unit,
) {
    val fractionDigits = java.util.Currency.getInstance(contribution.amount.currencyCode).defaultFractionDigits
    val eligibleGoals = goals.filter { it.target.currencyCode == contribution.amount.currencyCode }
    var goalId by remember(contribution.id) { mutableStateOf(contribution.goalId) }
    var amount by remember(contribution.id) {
        mutableStateOf(contribution.amount.minor.toBigDecimal().movePointLeft(fractionDigits).toPlainString())
    }
    var date by remember(contribution.id) { mutableStateOf(contribution.localDate) }
    var note by remember(contribution.id) { mutableStateOf(contribution.note) }
    val selectedGoal = eligibleGoals.firstOrNull { it.id == goalId }
    val parsedAmount = runCatching { Money.parseMajor(amount, contribution.amount.currencyCode) }.getOrNull()
    val validDate = runCatching { java.time.LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Goal", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eligibleGoals.forEach { goal ->
                        FilterChip(goalId == goal.id, { goalId = goal.id }, { Text(goal.name) })
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount (${contribution.amount.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        contribution.copy(
                            goalId = requireNotNull(selectedGoal).id,
                            goalName = selectedGoal.name,
                            amount = requireNotNull(parsedAmount),
                            localDate = date.trim(),
                            note = note.trim(),
                        ),
                    )
                },
                enabled = selectedGoal != null && parsedAmount?.minor?.let { it > 0 } == true &&
                    validDate && note.length <= 500,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionSheet(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (TransactionKind, String, String, String, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    val account = state.accounts.firstOrNull()
    var sourceAccountId by remember(state.accounts) { mutableStateOf(account?.id.orEmpty()) }
    val possibleDestinations = state.accounts.filter { it.id != sourceAccountId }
    var destinationAccountId by remember(sourceAccountId, possibleDestinations) {
        mutableStateOf(possibleDestinations.firstOrNull()?.id.orEmpty())
    }
    val requiredKind = if (kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == requiredKind }
    var categoryId by remember(kind, categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    val validAmount = runCatching { Money.parseMajor(amount).minor > 0 }.getOrDefault(false)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add transaction", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == TransactionKind.EXPENSE, { kind = TransactionKind.EXPENSE }, { Text("Expense") })
                FilterChip(kind == TransactionKind.INCOME, { kind = TransactionKind.INCOME }, { Text("Income") })
                FilterChip(kind == TransactionKind.TRANSFER, { kind = TransactionKind.TRANSFER }, { Text("Transfer") })
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (EUR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                label = { Text(if (kind == TransactionKind.INCOME) "Source" else if (kind == TransactionKind.TRANSFER) "Note" else "Payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == TransactionKind.TRANSFER) {
                Text("From account", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.forEach { item ->
                        FilterChip(sourceAccountId == item.id, { sourceAccountId = item.id }, { Text(item.name) })
                    }
                }
                Text("To account", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    possibleDestinations.forEach { item ->
                        FilterChip(destinationAccountId == item.id, { destinationAccountId = item.id }, { Text(item.name) })
                    }
                }
            } else {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(4).forEach { category ->
                        FilterChip(categoryId == category.id, { categoryId = category.id }, { Text(category.name) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onSave(kind, amount, sourceAccountId, destinationAccountId, categoryId, payee) },
                enabled = validAmount && sourceAccountId.isNotBlank() &&
                    if (kind == TransactionKind.TRANSFER) destinationAccountId.isNotBlank()
                    else categoryId.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Save transaction") }
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: EditableAccount,
    onDismiss: () -> Unit,
    onSave: (EditableAccount) -> Unit,
) {
    val fractionDigits = java.util.Currency.getInstance(account.openingBalance.currencyCode).defaultFractionDigits
    var name by remember(account.id) { mutableStateOf(account.name) }
    var type by remember(account.id) { mutableStateOf(account.type) }
    var openingBalance by remember(account.id) {
        mutableStateOf(
            account.openingBalance.minor.toBigDecimal().movePointLeft(fractionDigits).toPlainString(),
        )
    }
    val parsedBalance = runCatching {
        Money.parseMajor(openingBalance, account.openingBalance.currencyCode)
    }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true)
                Text("Type")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AccountType.entries.forEach { option ->
                        FilterChip(type == option, { type = option }, { Text(option.name.lowercase()) })
                    }
                }
                OutlinedTextField(
                    openingBalance,
                    { openingBalance = it },
                    label = { Text("Opening balance (${account.openingBalance.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("Currency is fixed after account creation.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        account.copy(
                            name = name,
                            type = type,
                            openingBalance = requireNotNull(parsedBalance),
                        ),
                    )
                },
                enabled = name.isNotBlank() && parsedBalance != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, AccountType, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }
    var type by remember { mutableStateOf(AccountType.CHECKING) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true)
                OutlinedTextField(
                    opening, { opening = it }, label = { Text("Opening balance (EUR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH).forEach { option ->
                        FilterChip(type == option, { type = option }, { Text(option.name.lowercase()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, type, opening) },
                enabled = name.isNotBlank() && runCatching { Money.parseMajor(opening) }.isSuccess,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReconcileDialog(
    accountName: String,
    currentBalance: Money,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var statement by remember { mutableStateOf("") }
    var adjustment by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reconcile $accountName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current app balance: ${currentBalance.formatted()}")
                OutlinedTextField(
                    statement, { statement = it }, label = { Text("Statement balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                FilterChip(
                    selected = adjustment,
                    onClick = { adjustment = !adjustment },
                    label = { Text("Create adjustment if needed") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(statement, adjustment) },
                enabled = runCatching { Money.parseMajor(statement) }.isSuccess,
            ) { Text("Reconcile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}
