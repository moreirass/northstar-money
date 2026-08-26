package com.northstar.money.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

internal enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Plan("plan", "Plan", Icons.Default.Assessment),
    Activity("activity", "Activity", Icons.AutoMirrored.Filled.ReceiptLong),
    More("more", "More", Icons.Default.MoreHoriz),
}

internal enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

internal fun classifyWindowWidth(widthDp: Int): WindowWidthClass = when {
    widthDp < 600 -> WindowWidthClass.COMPACT
    widthDp < 840 -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NorthstarApp() {
    val application = LocalContext.current.applicationContext as NorthstarApplication
    val financeViewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModelFactory(application.financeRepository, application.userPreferences),
    )
    val state by financeViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = Destination.entries.firstOrNull { it.route == backStackEntry?.destination?.route }
        ?: Destination.Home
    var showAdd by remember { mutableStateOf(false) }
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

    BoxWithConstraints {
    val widthClass = classifyWindowWidth(maxWidth.value.toInt())
    val useNavigationRail = widthClass != WindowWidthClass.COMPACT
    Scaffold(
        topBar = { TopAppBar(title = { Text(destination.label) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
        bottomBar = {
            if (!useNavigationRail) NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Destination.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            NavigationRail {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Destination.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.weight(1f),
        ) {
            composable(Destination.Home.route) {
                AdaptiveContentPane(widthClass) { HomeScreen(state, padding) }
            }
            composable(Destination.Plan.route) {
                AdaptiveContentPane(widthClass) { PlanScreen(state, padding, financeViewModel::setBudget) }
            }
            composable(Destination.Activity.route) { AdaptiveContentPane(widthClass) { ActivityScreen(
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
                onSetCleared = financeViewModel::setTransactionCleared,
                onDelete = { id ->
                    scope.launch {
                        runSuspendCatching {
                            performTransactionDeleteWithUndo(
                                delete = { financeViewModel.deleteTransaction(id) },
                                offerUndo = {
                                    snackbarHostState.showSnackbar(
                                        message = "Transaction moved to Recently deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long,
                                    ) == SnackbarResult.ActionPerformed
                                },
                                restore = { financeViewModel.restoreTransaction(id) },
                            )
                        }.onSuccess { outcome ->
                            if (outcome == DeleteUndoOutcome.RESTORE_FAILED) {
                                snackbarHostState.showSnackbar(
                                    "Could not restore the transaction. It remains in Recently deleted.",
                                )
                            }
                        }.onFailure {
                            snackbarHostState.showSnackbar("Could not delete the transaction. Nothing was changed.")
                        }
                    }
                },
            ) } }
            composable(Destination.More.route) { AdaptiveContentPane(widthClass) { MoreScreen(
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
            ) } }
        }
        }
    }
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
            onSave = { kind, amount, destinationAmount, account, destinationAccount, category, payee ->
                if (kind == TransactionKind.TRANSFER) {
                    financeViewModel.transfer(amount, destinationAmount, account, destinationAccount, payee)
                } else {
                    financeViewModel.addTransaction(kind, amount, account, category, payee)
                }
                showAdd = false
            },
        )
    }
}

@Composable
internal fun AdaptiveContentPane(
    widthClass: WindowWidthClass,
    content: @Composable () -> Unit,
) {
    val maximumWidth = when (widthClass) {
        WindowWidthClass.COMPACT -> 600.dp
        WindowWidthClass.MEDIUM -> 760.dp
        WindowWidthClass.EXPANDED -> 1040.dp
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().widthIn(max = maximumWidth)) { content() }
    }
}
