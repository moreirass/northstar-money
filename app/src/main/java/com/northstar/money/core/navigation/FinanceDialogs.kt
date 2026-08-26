package com.northstar.money.core.navigation

import com.northstar.money.R
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
import androidx.compose.ui.res.stringResource
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

@Composable
internal fun BackupPasswordDialog(
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
                Text(stringResource(R.string.ui_use_12_128_characters_losing_this_password_makes_the))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.ui_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.ui_confirm_password)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun CategoryDialog(onDismiss: () -> Unit, onSave: (String, CategoryKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_new_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_category_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryKind.entries.forEach { option ->
                        FilterChip(kind == option, { kind = option }, { Text(option.name.lowercase()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ui_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun RenameCategoryDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_rename_category)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.ui_category_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ui_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun MergeCategoryDialog(
    sourceName: String,
    targets: List<Category>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    var targetId by remember(targets) { mutableStateOf(targets.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_merge_named, sourceName)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.merge_category_explanation, sourceName))
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
            ) { Text(stringResource(R.string.ui_merge)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun EditTransactionDialog(
    transaction: EditableTransaction,
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (EditableTransaction) -> Unit,
) {
    val sourceDigits = java.util.Currency.getInstance(transaction.amount.currencyCode).defaultFractionDigits
    val initialDestinationAmount = transaction.destinationAmount ?: transaction.amount
    val destinationDigits = java.util.Currency.getInstance(initialDestinationAmount.currencyCode).defaultFractionDigits
    var amount by remember {
        mutableStateOf(transaction.amount.minor.toBigDecimal().movePointLeft(sourceDigits).toPlainString())
    }
    var destinationAmount by remember {
        mutableStateOf(initialDestinationAmount.minor.toBigDecimal().movePointLeft(destinationDigits).toPlainString())
    }
    var localDate by remember { mutableStateOf(transaction.localDate) }
    var payee by remember { mutableStateOf(transaction.payee) }
    var note by remember { mutableStateOf(transaction.note) }
    var accountId by remember { mutableStateOf(transaction.accountId) }
    var categoryId by remember { mutableStateOf(transaction.categoryId) }
    var destinationAccountId by remember { mutableStateOf(transaction.destinationAccountId) }
    val accounts = state.accounts.filter { it.currencyCode == transaction.amount.currencyCode }
    val destinationAccounts = state.accounts.filter { it.currencyCode == initialDestinationAmount.currencyCode }
    val categories = state.categories.filter { it.kind.name == transaction.kind.name }
    val parsedAmount = runCatching { Money.parseMajor(amount, transaction.amount.currencyCode) }.getOrNull()
    val parsedDestinationAmount = runCatching {
        Money.parseMajor(destinationAmount, initialDestinationAmount.currencyCode)
    }.getOrNull()
    val valid = parsedAmount?.minor?.let { it > 0 } == true &&
        runCatching { java.time.LocalDate.parse(localDate) }.isSuccess &&
        accounts.any { it.id == accountId } &&
        if (transaction.kind == TransactionKind.TRANSFER) {
            destinationAccountId != null && destinationAccountId != accountId &&
                destinationAccounts.any { it.id == destinationAccountId } &&
                parsedDestinationAmount?.minor?.let { it > 0 } == true
        } else {
            categoryId != null && categories.any { it.id == categoryId }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_kind, transaction.kind.name.lowercase())) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.field_amount_currency, transaction.amount.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (transaction.kind == TransactionKind.TRANSFER &&
                    transaction.amount.currencyCode != initialDestinationAmount.currencyCode
                ) {
                    OutlinedTextField(
                        destinationAmount,
                        { destinationAmount = it },
                        label = { Text(stringResource(R.string.field_received_amount_currency, initialDestinationAmount.currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                OutlinedTextField(localDate, { localDate = it }, label = { Text(stringResource(R.string.ui_date_yyyy_mm_dd)) }, singleLine = true)
                OutlinedTextField(payee, { payee = it }, label = { Text(stringResource(R.string.ui_payee)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.ui_note)) })
                Text(stringResource(if (transaction.kind == TransactionKind.TRANSFER) R.string.field_from_account else R.string.ui_account))
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
                    Text(stringResource(R.string.ui_to_account))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        destinationAccounts.filter { it.id != accountId }.forEach { account ->
                            FilterChip(
                                selected = account.id == destinationAccountId,
                                onClick = { destinationAccountId = account.id },
                                label = { Text(account.name) },
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.ui_category))
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
                            destinationAmount = if (
                                transaction.amount.currencyCode == initialDestinationAmount.currencyCode
                            ) requireNotNull(parsedAmount) else requireNotNull(parsedDestinationAmount),
                        ),
                    )
                },
                enabled = valid,
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun EditRecurringDialog(
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
        title = { Text(stringResource(R.string.ui_edit_recurrence)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TransactionKind.EXPENSE, TransactionKind.INCOME).forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                val targetKind = if (option == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                                categoryId = state.categories.firstOrNull { it.kind == targetKind }?.id
                            },
                            label = {
                                Text(stringResource(if (option == TransactionKind.INCOME) R.string.ui_income else R.string.ui_expense))
                            },
                        )
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.field_amount_currency, recurring.amount.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(stringResource(R.string.ui_account))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChip(account.id == accountId, { accountId = account.id }, { Text(account.name) })
                    }
                }
                Text(stringResource(R.string.ui_category))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(category.id == categoryId, { categoryId = category.id }, { Text(category.name) })
                    }
                }
                Text(stringResource(R.string.ui_frequency))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEEKLY", "MONTHLY", "YEARLY").forEach { option ->
                        val labelRes = when (option) {
                            "WEEKLY" -> R.string.frequency_weekly
                            "YEARLY" -> R.string.frequency_yearly
                            else -> R.string.frequency_monthly
                        }
                        FilterChip(frequency == option, { frequency = option }, { Text(stringResource(labelRes)) })
                    }
                }
                OutlinedTextField(
                    intervalCount,
                    { intervalCount = it },
                    label = { Text(stringResource(R.string.ui_every_n_periods)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    nextDate,
                    { nextDate = it },
                    label = { Text(stringResource(R.string.ui_next_date_yyyy_mm_dd)) },
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
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun RecurringDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (String, TransactionKind, String, String, String?, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().plusMonths(1).toString()) }
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    val accounts = state.accounts
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    val account = accounts.firstOrNull { it.id == accountId }
    val requiredCategoryKind = if (kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == requiredCategoryKind }
    var categoryId by remember(kind, categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    val parsedAmount = runCatching {
        Money.parseMajor(amount, account?.currencyCode ?: "EUR")
    }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_recurring_transaction)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true)
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.field_amount_currency, account?.currencyCode ?: "—")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.ui_next_date_yyyy_mm_dd)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == TransactionKind.EXPENSE, { kind = TransactionKind.EXPENSE }, { Text(stringResource(R.string.ui_expense)) })
                    FilterChip(kind == TransactionKind.INCOME, { kind = TransactionKind.INCOME }, { Text(stringResource(R.string.ui_income)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEEKLY", "MONTHLY", "YEARLY").forEach {
                        val labelRes = when (it) {
                            "WEEKLY" -> R.string.frequency_weekly
                            "YEARLY" -> R.string.frequency_yearly
                            else -> R.string.frequency_monthly
                        }
                        FilterChip(frequency == it, { frequency = it }, { Text(stringResource(labelRes)) })
                    }
                }
                Text(stringResource(R.string.ui_account), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    accounts.forEach { option ->
                        FilterChip(accountId == option.id, { accountId = option.id }, { Text(option.name) })
                    }
                }
                Text(stringResource(R.string.ui_category), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { option ->
                        FilterChip(categoryId == option.id, { categoryId = option.id }, { Text(option.name) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, kind, amount, account!!.id, categoryId, frequency, date) },
                enabled = name.isNotBlank() && account != null && categories.any { it.id == categoryId } &&
                    parsedAmount?.minor?.let { it > 0 } == true &&
                    runCatching { java.time.LocalDate.parse(date) }.isSuccess,
            ) { Text(stringResource(R.string.ui_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun DebtDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var rate by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("1") }
    val accounts = state.accounts.filter { candidate -> state.debts.none { it.accountId == candidate.id } }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    val account = accounts.firstOrNull { it.id == accountId }
    val parsedPayment = runCatching {
        Money.parseMajor(payment, account?.currencyCode ?: "EUR")
    }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_debt_profile)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ui_account), style = MaterialTheme.typography.labelLarge)
                if (accounts.isEmpty()) Text(stringResource(R.string.ui_create_an_account_without_a_debt_profile_first))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    accounts.forEach { option ->
                        FilterChip(accountId == option.id, { accountId = option.id }, { Text(option.name) })
                    }
                }
                OutlinedTextField(rate, { rate = it }, label = { Text(stringResource(R.string.ui_annual_rate)) }, singleLine = true)
                OutlinedTextField(
                    payment,
                    { payment = it },
                    label = { Text(stringResource(R.string.field_minimum_payment_currency, account?.currencyCode ?: "—")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(dueDay, { dueDay = it }, label = { Text(stringResource(R.string.ui_due_day)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(account!!.id, rate, payment, dueDay) },
                enabled = account != null && rate.toBigDecimalOrNull() != null &&
                    parsedPayment?.minor?.let { it >= 0 } == true &&
                    dueDay.toIntOrNull() in 1..31,
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun EditDebtDialog(
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
        title = { Text(stringResource(R.string.ui_edit_debt_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_named, accountName))
                OutlinedTextField(
                    rate,
                    { rate = it },
                    label = { Text(stringResource(R.string.ui_annual_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    payment,
                    { payment = it },
                    label = { Text(stringResource(R.string.field_minimum_payment_currency, debt.minimumPayment.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    dueDay,
                    { dueDay = it },
                    label = { Text(stringResource(R.string.ui_due_day)) },
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
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun AmountDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var amount by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                amount, { amount = it }, label = { Text(stringResource(R.string.ui_amount_eur)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount) },
                enabled = runCatching { Money.parseMajor(amount).minor >= 0 }.getOrDefault(false),
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun GoalDialog(onDismiss: () -> Unit, onSave: (String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_new_savings_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_goal_name)) }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.ui_target_amount)) }, singleLine = true)
                OutlinedTextField(saved, { saved = it }, label = { Text(stringResource(R.string.ui_already_saved)) }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.ui_target_date_yyyy_mm_dd_optional)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, target, saved, date.ifBlank { null }) },
                enabled = name.isNotBlank() &&
                    runCatching { Money.parseMajor(target).minor > 0 }.getOrDefault(false) &&
                    runCatching { Money.parseMajor(saved).minor >= 0 }.getOrDefault(false) &&
                    (date.isBlank() || runCatching { java.time.LocalDate.parse(date) }.isSuccess),
            ) { Text(stringResource(R.string.ui_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun EditGoalDialog(
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
        title = { Text(stringResource(R.string.ui_edit_savings_goal)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_goal_name)) }, singleLine = true)
                OutlinedTextField(
                    target,
                    { target = it },
                    label = { Text(stringResource(R.string.field_target_amount_currency, goal.target.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text(stringResource(R.string.ui_target_date_yyyy_mm_dd_optional)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.ui_status), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "ACTIVE" to R.string.status_active,
                        "PAUSED" to R.string.status_paused,
                        "COMPLETED" to R.string.status_completed,
                    ).forEach { (value, labelRes) ->
                            FilterChip(status == value, { status = value }, { Text(stringResource(labelRes)) })
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
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun AddGoalContributionDialog(
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
        title = { Text(stringResource(R.string.ui_add_contribution)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goal.name, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text(stringResource(R.string.field_amount_currency, goal.target.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.ui_date_yyyy_mm_dd)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.ui_note_optional)) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount, date.trim(), note.trim()) },
                enabled = parsedAmount?.minor?.let { it > 0 } == true && validDate && note.length <= 500,
            ) { Text(stringResource(R.string.ui_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun GoalContributionDialog(
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
                Text(stringResource(R.string.ui_goal), style = MaterialTheme.typography.labelLarge)
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
                    label = { Text(stringResource(R.string.field_amount_currency, contribution.amount.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.ui_date_yyyy_mm_dd)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.ui_note_optional)) })
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
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTransactionSheet(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onSave: (TransactionKind, String, String, String, String, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var destinationAmount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var sourceAccountId by remember(state.accounts) { mutableStateOf(state.accounts.firstOrNull()?.id.orEmpty()) }
    val sourceAccount = state.accounts.firstOrNull { it.id == sourceAccountId }
    val possibleDestinations = state.accounts.filter { it.id != sourceAccountId }
    var destinationAccountId by remember(sourceAccountId, possibleDestinations) {
        mutableStateOf(possibleDestinations.firstOrNull()?.id.orEmpty())
    }
    val destinationAccount = possibleDestinations.firstOrNull { it.id == destinationAccountId }
    val isCrossCurrency = sourceAccount != null && destinationAccount != null &&
        sourceAccount.currencyCode != destinationAccount.currencyCode
    val requiredKind = if (kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == requiredKind }
    var categoryId by remember(kind, categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    val validAmount = runCatching {
        Money.parseMajor(amount, sourceAccount?.currencyCode ?: "EUR").minor > 0
    }.getOrDefault(false)
    val effectiveDestinationAmount = if (isCrossCurrency) destinationAmount else amount
    val validDestinationAmount = runCatching {
        Money.parseMajor(effectiveDestinationAmount, destinationAccount?.currencyCode ?: "EUR").minor > 0
    }.getOrDefault(false)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ui_add_transaction), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == TransactionKind.EXPENSE, { kind = TransactionKind.EXPENSE }, { Text(stringResource(R.string.ui_expense)) })
                FilterChip(kind == TransactionKind.INCOME, { kind = TransactionKind.INCOME }, { Text(stringResource(R.string.ui_income)) })
                FilterChip(kind == TransactionKind.TRANSFER, { kind = TransactionKind.TRANSFER }, { Text(stringResource(R.string.ui_transfer)) })
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.field_amount_currency, sourceAccount?.currencyCode ?: "—")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == TransactionKind.TRANSFER && isCrossCurrency) {
                OutlinedTextField(
                    value = destinationAmount,
                    onValueChange = { destinationAmount = it },
                    label = { Text(stringResource(R.string.field_received_amount_currency, destinationAccount.currencyCode)) },
                    supportingText = { Text(stringResource(R.string.ui_enter_the_amount_credited_by_the_destination_account)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                label = {
                    Text(
                        stringResource(
                            if (kind == TransactionKind.INCOME) R.string.field_source
                            else if (kind == TransactionKind.TRANSFER) R.string.field_note
                            else R.string.field_payee,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(if (kind == TransactionKind.TRANSFER) R.string.field_from_account else R.string.ui_account),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.accounts.forEach { item ->
                    FilterChip(sourceAccountId == item.id, { sourceAccountId = item.id }, { Text(item.name) })
                }
            }
            if (kind == TransactionKind.TRANSFER) {
                Text(stringResource(R.string.ui_to_account), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    possibleDestinations.forEach { item ->
                        FilterChip(destinationAccountId == item.id, { destinationAccountId = item.id }, { Text(item.name) })
                    }
                }
            } else {
                Text(stringResource(R.string.ui_category), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        FilterChip(categoryId == category.id, { categoryId = category.id }, { Text(category.name) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = {
                    onSave(
                        kind,
                        amount,
                        effectiveDestinationAmount,
                        sourceAccountId,
                        destinationAccountId,
                        categoryId,
                        payee,
                    )
                },
                enabled = validAmount && sourceAccountId.isNotBlank() &&
                    if (kind == TransactionKind.TRANSFER) {
                        destinationAccountId.isNotBlank() && validDestinationAmount
                    }
                    else categoryId.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.ui_save_transaction)) }
        }
    }
}

@Composable
internal fun EditAccountDialog(
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
        title = { Text(stringResource(R.string.ui_edit_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_account_name)) }, singleLine = true)
                Text(stringResource(R.string.ui_type))
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
                    label = { Text(stringResource(R.string.field_opening_balance_currency, account.openingBalance.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(stringResource(R.string.ui_currency_is_fixed_after_account_creation), style = MaterialTheme.typography.bodySmall)
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
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun AccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, AccountType, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }
    var type by remember { mutableStateOf(AccountType.CHECKING) }
    var currencyCode by remember { mutableStateOf("EUR") }
    val normalizedCurrency = currencyCode.trim().uppercase()
    val validCurrency = runCatching { java.util.Currency.getInstance(normalizedCurrency) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_new_account)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_account_name)) }, singleLine = true)
                OutlinedTextField(
                    opening, { opening = it }, label = { Text(stringResource(R.string.field_opening_balance_currency, normalizedCurrency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                OutlinedTextField(
                    currencyCode,
                    { currencyCode = it.take(3).uppercase() },
                    label = { Text(stringResource(R.string.ui_currency_iso_4217)) },
                    supportingText = { if (!validCurrency) Text(stringResource(R.string.ui_enter_a_valid_3_letter_currency_code)) },
                    singleLine = true,
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
                onClick = { onSave(name, type, opening, normalizedCurrency) },
                enabled = name.isNotBlank() && validCurrency &&
                    runCatching { Money.parseMajor(opening, normalizedCurrency) }.isSuccess,
            ) { Text(stringResource(R.string.ui_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
internal fun ReconcileDialog(
    accountName: String,
    currentBalance: Money,
    clearedBalance: Money,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var statement by remember { mutableStateOf("") }
    var statementDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var adjustment by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_reconcile_named, accountName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.current_total_balance, currentBalance.displayValue()))
                Text(stringResource(R.string.current_cleared_balance, clearedBalance.displayValue()))
                Text(stringResource(R.string.ui_only_cleared_transactions_on_or_before_the_statement))
                OutlinedTextField(
                    statementDate,
                    { statementDate = it },
                    label = { Text(stringResource(R.string.ui_statement_date_yyyy_mm_dd)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    statement,
                    { statement = it },
                    label = { Text(stringResource(R.string.field_statement_balance_currency, currentBalance.currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                FilterChip(
                    selected = adjustment,
                    onClick = { adjustment = !adjustment },
                    label = { Text(stringResource(R.string.ui_create_adjustment_if_needed)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(statementDate, statement, adjustment) },
                enabled = runCatching { Money.parseMajor(statement, currentBalance.currencyCode) }.isSuccess &&
                    runCatching { java.time.LocalDate.parse(statementDate) }.getOrNull()
                        ?.let { !it.isAfter(java.time.LocalDate.now()) } == true,
            ) { Text(stringResource(R.string.ui_reconcile)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}
