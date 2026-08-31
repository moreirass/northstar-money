package com.northstar.money.core.navigation

import android.annotation.SuppressLint
import com.northstar.money.R
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.PieChartOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.stringResource
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

internal enum class Destination(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Default.Home),
    Activity("activity", R.string.nav_activity, Icons.Default.SwapHoriz),
    Plan("plan", R.string.nav_plan, Icons.Default.PieChartOutline),
    More("more", R.string.nav_more, Icons.Default.MoreHoriz),
}

internal enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

private const val BudgetDetailRoute = "budget/{categoryId}"

internal fun classifyWindowWidth(widthDp: Int): WindowWidthClass = when {
    widthDp < 600 -> WindowWidthClass.COMPACT
    widthDp < 840 -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun NorthstarApp() {
    val context = LocalContext.current
    val application = context.applicationContext as NorthstarApplication
    val financeViewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModelFactory(application.financeRepository, application.userPreferences),
    )
    val state by financeViewModel.uiState.collectAsStateWithLifecycle()
    if (!state.isLoading && !state.loadFailed && !state.settings.onboardingCompleted) {
        OnboardingScreen(
            initialCurrencyCode = state.settings.baseCurrencyCode,
            onCurrencySelected = financeViewModel::setBaseCurrencyCode,
            onInitialAccountSubmitted = financeViewModel::configureInitialAccount,
            onBudgetSubmitted = financeViewModel::completeOnboarding,
            onComplete = {},
        )
        return
    }
    CompositionLocalProvider(LocalMoneyValuesHidden provides state.settings.moneyValuesHidden) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isBudgetDetail = backStackEntry?.destination?.route == BudgetDetailRoute
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
            snackbarHostState.showSnackbar(context.getString(event.messageRes))
        }
    }

    BoxWithConstraints {
    val widthClass = classifyWindowWidth(maxWidth.value.toInt())
    val useNavigationRail = widthClass != WindowWidthClass.COMPACT
    Scaffold(
        containerColor = if (
            destination == Destination.Home ||
            destination == Destination.Activity ||
            destination == Destination.Plan ||
            destination == Destination.More
        ) {
            Color(0xFF08080A)
        } else {
            MaterialTheme.colorScheme.background
        },
        topBar = {
            if (
                destination != Destination.Home &&
                destination != Destination.Activity &&
                destination != Destination.Plan &&
                destination != Destination.More
            ) {
                TopAppBar(
                    title = { Text(stringResource(destination.labelRes)) },
                    actions = {
                        val moneyVisibilityDescription = stringResource(
                            if (state.settings.moneyValuesHidden) R.string.money_show_values else R.string.money_hide_values,
                        )
                        IconButton(
                            onClick = { financeViewModel.setMoneyValuesHidden(!state.settings.moneyValuesHidden) },
                        ) {
                            Icon(
                                imageVector = if (state.settings.moneyValuesHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = moneyVisibilityDescription,
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (destination == Destination.Home && !isBudgetDetail) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = Color(0xFF10B981),
                    contentColor = Color(0xFF08080A),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_transaction))
                }
            }
        },
        floatingActionButtonPosition = if (destination == Destination.Home) FabPosition.Start else FabPosition.End,
        bottomBar = {
            if (!useNavigationRail && !isBudgetDetail) NavigationBar(
                containerColor = Color(0xFF121215),
                tonalElevation = 0.dp,
            ) {
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
                        label = { Text(stringResource(item.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF8E8E9F),
                            unselectedTextColor = Color(0xFF8E8E9F),
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
        if (useNavigationRail && !isBudgetDetail) {
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
                        label = { Text(stringResource(item.labelRes)) },
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
                FinanceScreenState(state.isLoading, state.loadFailed, financeViewModel::retryLoading) {
                    AdaptiveContentPane(widthClass) {
                        HomeScreen(
                            state = state,
                            padding = padding,
                            onOpenTransactions = { navController.navigate(Destination.Activity.route) },
                            onOpenBudgets = { navController.navigate(Destination.Plan.route) },
                        )
                    }
                }
            }
            composable(Destination.Plan.route) {
                FinanceScreenState(state.isLoading, state.loadFailed, financeViewModel::retryLoading) {
                    AdaptiveContentPane(widthClass) {
                        PlanScreen(
                            state = state,
                            padding = padding,
                            onSetBudget = financeViewModel::setBudget,
                            onOpenBudget = { categoryId -> navController.navigate("budget/$categoryId") },
                        )
                    }
                }
            }
            composable(BudgetDetailRoute) { entry ->
                FinanceScreenState(state.isLoading, state.loadFailed, financeViewModel::retryLoading) {
                    AdaptiveContentPane(widthClass) {
                        BudgetDetailScreen(
                            budget = state.budgets.firstOrNull {
                                it.categoryId == entry.arguments?.getString("categoryId")
                            },
                            transactions = state.transactions,
                            padding = padding,
                            onBack = navController::popBackStack,
                            onEditTransaction = { id ->
                                scope.launch {
                                    runSuspendCatching { financeViewModel.getTransactionForEdit(id) }
                                        .onSuccess { editingTransaction = it }
                                }
                            },
                        )
                    }
                }
            }
            composable(Destination.Activity.route) { FinanceScreenState(
                state.isLoading,
                state.loadFailed,
                financeViewModel::retryLoading,
            ) { AdaptiveContentPane(widthClass) { ActivityScreen(
                state = state,
                padding = padding,
                onEdit = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getTransactionForEdit(id) }
                            .onSuccess { editingTransaction = it }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    it.message ?: context.getString(R.string.snackbar_could_not_open_transaction),
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
                                        message = context.getString(R.string.snackbar_transaction_deleted),
                                        actionLabel = context.getString(R.string.action_undo),
                                        duration = SnackbarDuration.Long,
                                    ) == SnackbarResult.ActionPerformed
                                },
                                restore = { financeViewModel.restoreTransaction(id) },
                            )
                        }.onSuccess { outcome ->
                            if (outcome == DeleteUndoOutcome.RESTORE_FAILED) {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_restore_failed_deleted),
                                )
                            }
                        }.onFailure {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_delete_failed))
                        }
                    }
                },
            ) } } }
            composable(Destination.More.route) { FinanceScreenState(
                state.isLoading,
                state.loadFailed,
                financeViewModel::retryLoading,
            ) { AdaptiveContentPane(widthClass) { MoreScreen(
                state = state,
                padding = padding,
                onCreateAccount = financeViewModel::createAccount,
                onEditAccount = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getAccountForEdit(id) }
                            .onSuccess { editingAccount = it }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    it.message ?: context.getString(R.string.snackbar_could_not_open_account),
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
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: context.getString(R.string.snackbar_could_not_edit_goal)) }
                    }
                },
                onAddGoalContribution = financeViewModel::addGoalContribution,
                onEditGoalContribution = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.getGoalContributionForEdit(id) }
                            .onSuccess { editingGoalContribution = it }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: context.getString(R.string.snackbar_could_not_edit_contribution)) }
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
                                    it.message ?: context.getString(R.string.snackbar_could_not_open_recurrence),
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
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: context.getString(R.string.snackbar_could_not_edit_debt)) }
                    }
                },
                onImportCsv = financeViewModel::importCsv,
                onSetAppLock = financeViewModel::setAppLock,
                onSetReminders = financeViewModel::setReminders,
                onSetMoneyValuesHidden = financeViewModel::setMoneyValuesHidden,
                onShowOnboarding = { financeViewModel.setOnboardingCompleted(false) },
                onCreateCategory = financeViewModel::createCategory,
                onRenameCategory = financeViewModel::renameCategory,
                onArchiveCategory = financeViewModel::archiveCategory,
                onRestoreCategory = financeViewModel::restoreCategory,
                onMergeCategory = financeViewModel::mergeCategory,
                onUndoCategoryMerge = financeViewModel::undoCategoryMerge,
                onCreateFullBackup = financeViewModel::createFullBackup,
                onRestoreFullBackup = financeViewModel::restoreFullBackup,
                onUndoFullRestore = financeViewModel::undoLastFullRestore,
                onRefreshExchangeRates = financeViewModel::refreshExchangeRates,
                onRecoverTransaction = { id ->
                    scope.launch {
                        runSuspendCatching { financeViewModel.restoreTransaction(id) }
                            .onSuccess { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transaction_restored)) }
                            .onFailure { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_restore_transaction_failed)) }
                    }
                },
            ) } } }
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
            onAttachReceipt = { name, mimeType, content ->
                financeViewModel.addReceiptAttachment(transaction.id, name, mimeType, content)
            },
            onDeleteReceipt = financeViewModel::deleteReceiptAttachment,
            onRetryReceiptOcr = financeViewModel::retryReceiptOcr,
            onApplyReceiptOcr = financeViewModel::applyReceiptOcr,
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
            title = stringResource(R.string.dialog_edit_contribution),
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
            accountName = state.accounts.firstOrNull { it.id == debt.accountId }?.name
                ?: stringResource(R.string.fallback_account),
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
