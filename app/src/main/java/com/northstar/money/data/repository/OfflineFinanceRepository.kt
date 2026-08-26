package com.northstar.money.data.repository

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.FinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionImportItem
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.ArchivedCategory
import com.northstar.money.domain.model.CategoryKind
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import com.northstar.money.domain.model.EditableAccount
import com.northstar.money.domain.model.EditableRecurring
import com.northstar.money.domain.model.EditableGoal
import com.northstar.money.domain.model.GoalContribution
import com.northstar.money.domain.repository.FinanceRepository
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.backup.FullBackupJsonCodec
import com.northstar.money.data.backup.DatabaseSnapshotValidator
import com.northstar.money.data.backup.PortableBackupCodec
import com.northstar.money.data.backup.RestoreRecoveryStore
import com.northstar.money.data.importing.TransactionCsvValidator
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineFinanceRepository(
    private val dao: FinanceDao,
    private val backupCodec: FullBackupJsonCodec = FullBackupJsonCodec(),
    private val restoreRecoveryStore: RestoreRecoveryStore? = null,
    private val portableBackupCodec: PortableBackupCodec = PortableBackupCodec(),
    private val snapshotValidator: DatabaseSnapshotValidator = DatabaseSnapshotValidator(),
    private val csvValidator: TransactionCsvValidator = TransactionCsvValidator(),
) : FinanceRepository {
    private val restoreMutex = Mutex()

    override fun observeAccounts(): Flow<List<Account>> = dao.observeAccounts().map { rows ->
        rows.map { Account(it.id, it.name, AccountType.valueOf(it.type), it.currencyCode, Money(it.balanceMinor, it.currencyCode)) }
    }

    override fun observeArchivedAccounts(): Flow<List<Account>> = dao.observeArchivedAccounts().map { rows ->
        rows.map { Account(it.id, it.name, AccountType.valueOf(it.type), it.currencyCode, Money(it.balanceMinor, it.currencyCode)) }
    }

    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { rows ->
        rows.map { Category(it.id, it.name, CategoryKind.valueOf(it.kind)) }
    }

    override fun observeArchivedCategories(): Flow<List<ArchivedCategory>> =
        dao.observeArchivedCategories().map { rows ->
            rows.map {
                ArchivedCategory(
                    id = it.id,
                    name = it.name,
                    kind = CategoryKind.valueOf(it.kind),
                    mergedIntoCategoryId = it.mergedIntoCategoryId,
                    mergedIntoCategoryName = it.mergedIntoCategoryName,
                )
            }
        }

    override fun observeTransactions(): Flow<List<TransactionItem>> =
        dao.observeTransactions().map { rows ->
            rows.map(::transactionRowToDomain)
        }

    override fun observeDeletedTransactions(): Flow<List<TransactionItem>> =
        dao.observeDeletedTransactions().map { rows -> rows.map(::transactionRowToDomain) }

    override fun observeSummary(): Flow<FinanceSummary> {
        val monthStart = LocalDate.now().withDayOfMonth(1).toString()
        return dao.observeSummary(monthStart, BASE_CURRENCY_CODE).map {
            FinanceSummary(
                Money(it.balanceMinor, BASE_CURRENCY_CODE),
                Money(it.incomeMinor, BASE_CURRENCY_CODE),
                Money(it.expenseMinor, BASE_CURRENCY_CODE),
            )
        }
    }

    override fun observeBudgets() = dao.observeBudgets(
        LocalDate.now().withDayOfMonth(1).toString(),
        BASE_CURRENCY_CODE,
    ).map { rows ->
        rows.map {
            com.northstar.money.domain.model.BudgetProgress(
                it.categoryId,
                it.categoryName,
                Money(it.plannedMinor, BASE_CURRENCY_CODE),
                Money(it.spentMinor, BASE_CURRENCY_CODE),
            )
        }
    }

    override fun observeGoals() = dao.observeGoals().map { rows ->
        rows.map {
            com.northstar.money.domain.model.SavingsGoal(
                it.id, it.name, Money(it.targetMinor, it.currencyCode),
                Money(it.savedMinor, it.currencyCode), it.targetLocalDate, it.status,
            )
        }
    }

    override fun observeGoalContributions() = dao.observeGoalContributions().map { rows ->
        rows.map(::goalContributionToDomain)
    }

    override fun observeDeletedGoalContributions() = dao.observeDeletedGoalContributions().map { rows ->
        rows.map(::goalContributionToDomain)
    }

    override fun observeRecurring() = dao.observeRecurring().map { rows ->
        rows.map(::recurringToDomain)
    }

    override fun observePausedRecurring() = dao.observePausedRecurring().map { rows -> rows.map(::recurringToDomain) }

    override fun observeDeletedRecurring() = dao.observeDeletedRecurring().map { rows -> rows.map(::recurringToDomain) }

    override fun observeDebts() = dao.observeDebts().map { rows ->
        rows.map {
            com.northstar.money.domain.model.DebtProfile(
                it.id,
                it.accountId,
                it.annualRateBasisPoints,
                Money(it.minimumPaymentMinor, it.currencyCode),
                it.dueDay,
            )
        }
    }

    override suspend fun seedIfEmpty() {
        val now = System.currentTimeMillis()
        val expenseNames = listOf("Housing", "Groceries", "Transport", "Dining", "Health", "Shopping", "Other")
        val categories = expenseNames.mapIndexed { index, name ->
            CategoryEntity("expense-${name.lowercase()}", name, "EXPENSE", index)
        } + CategoryEntity("income-salary", "Salary", "INCOME", 0)
        dao.seedIfEmpty(
            defaultAccounts = listOf(
                AccountEntity("main-account", "Main account", "CHECKING", "EUR", 0, createdAt = now, updatedAt = now),
            ),
            defaultCategories = categories,
        )
    }

    override suspend fun addTransaction(
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String,
        payee: String,
    ) {
        require(kind != TransactionKind.TRANSFER) { "Transfers require two entries" }
        require(amount.minor > 0) { "Amount must be positive" }
        requireAccountCurrency(accountId, amount.currencyCode)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertTransaction(
            TransactionEntity(id, kind.name, LocalDate.now().toString(), payee.trim(), "", now, now),
            TransactionEntryEntity(
                UUID.randomUUID().toString(), id, accountId, categoryId,
                if (kind == TransactionKind.EXPENSE) -amount.minor else amount.minor,
                amount.currencyCode, true,
            ),
        )
    }

    override suspend fun deleteTransaction(id: String) {
        require(dao.deleteTransaction(id, System.currentTimeMillis()) == 1) { "Transaction is already deleted or missing" }
    }

    override suspend fun restoreTransaction(id: String) {
        require(dao.restoreTransaction(id, System.currentTimeMillis()) == 1) { "Transaction is not available for recovery" }
    }

    override suspend fun getTransactionForEdit(id: String): EditableTransaction {
        val stored = dao.getTransactionForEdit(id)
        require(!stored.isReconciliationAdjustment) { "Reconciliation adjustments cannot be edited directly" }
        val kind = TransactionKind.valueOf(stored.transaction.kind)
        return if (kind == TransactionKind.TRANSFER) {
            require(stored.entries.size == 2) { "Transfer must contain exactly two entries" }
            val source = stored.entries.singleOrNull { it.amountMinor < 0 }
            val destination = stored.entries.singleOrNull { it.amountMinor > 0 }
            requireNotNull(source) { "Transfer source is invalid" }
            requireNotNull(destination) { "Transfer destination is invalid" }
            require(
                source.currencyCode == destination.currencyCode &&
                    Math.addExact(source.amountMinor, destination.amountMinor) == 0L,
            ) { "Cross-currency or unbalanced transfer cannot be edited yet" }
            EditableTransaction(
                id = id,
                kind = kind,
                localDate = stored.transaction.localDate,
                payee = stored.transaction.payee,
                note = stored.transaction.note,
                amount = Money(Math.negateExact(source.amountMinor), source.currencyCode),
                accountId = source.accountId,
                categoryId = null,
                destinationAccountId = destination.accountId,
            )
        } else {
            require(stored.entries.size == 1) { "Income and expense transactions must contain one entry" }
            val entry = stored.entries.single()
            val positiveAmount = if (entry.amountMinor < 0) Math.negateExact(entry.amountMinor) else entry.amountMinor
            require(positiveAmount > 0) { "Transaction amount is invalid" }
            EditableTransaction(
                id = id,
                kind = kind,
                localDate = stored.transaction.localDate,
                payee = stored.transaction.payee,
                note = stored.transaction.note,
                amount = Money(positiveAmount, entry.currencyCode),
                accountId = entry.accountId,
                categoryId = entry.categoryId,
            )
        }
    }

    override suspend fun updateTransaction(transaction: EditableTransaction) {
        val stored = dao.getTransactionForEdit(transaction.id)
        require(!stored.isReconciliationAdjustment) { "Reconciliation adjustments cannot be edited directly" }
        require(stored.transaction.kind == transaction.kind.name) { "Transaction type cannot be changed" }
        require(transaction.amount.minor > 0) { "Amount must be positive" }
        val localDate = LocalDate.parse(transaction.localDate).toString()
        val updatedAt = System.currentTimeMillis()
        val updatedTransaction = stored.transaction.copy(
            localDate = localDate,
            payee = transaction.payee.trim().ifBlank {
                if (transaction.kind == TransactionKind.TRANSFER) "Transfer" else transaction.kind.name.lowercase().replaceFirstChar(Char::uppercase)
            },
            note = transaction.note.trim(),
            updatedAt = updatedAt,
        )
        val updatedEntries = if (transaction.kind == TransactionKind.TRANSFER) {
            require(stored.entries.size == 2) { "Transfer must contain exactly two entries" }
            val destinationAccountId = requireNotNull(transaction.destinationAccountId) { "Destination account is required" }
            require(transaction.accountId != destinationAccountId) { "Transfer accounts must be different" }
            requireAccountCurrency(transaction.accountId, transaction.amount.currencyCode)
            requireAccountCurrency(destinationAccountId, transaction.amount.currencyCode)
            val source = requireNotNull(stored.entries.singleOrNull { it.amountMinor < 0 }) { "Transfer source is invalid" }
            val destination = requireNotNull(stored.entries.singleOrNull { it.amountMinor > 0 }) { "Transfer destination is invalid" }
            listOf(
                source.copy(
                    accountId = transaction.accountId,
                    categoryId = null,
                    amountMinor = Math.negateExact(transaction.amount.minor),
                    currencyCode = transaction.amount.currencyCode,
                ),
                destination.copy(
                    accountId = destinationAccountId,
                    categoryId = null,
                    amountMinor = transaction.amount.minor,
                    currencyCode = transaction.amount.currencyCode,
                ),
            )
        } else {
            require(stored.entries.size == 1) { "Income and expense transactions must contain one entry" }
            val categoryId = requireNotNull(transaction.categoryId) { "Category is required" }
            requireAccountCurrency(transaction.accountId, transaction.amount.currencyCode)
            require(dao.getActiveCategoryKind(categoryId) == transaction.kind.name) {
                "Category does not match the transaction type"
            }
            listOf(
                stored.entries.single().copy(
                    accountId = transaction.accountId,
                    categoryId = categoryId,
                    amountMinor = if (transaction.kind == TransactionKind.EXPENSE) {
                        Math.negateExact(transaction.amount.minor)
                    } else {
                        transaction.amount.minor
                    },
                    currencyCode = transaction.amount.currencyCode,
                ),
            )
        }
        dao.updateTransaction(updatedTransaction, updatedEntries)
    }

    override suspend fun createAccount(name: String, type: AccountType, openingBalance: Money) {
        require(name.isNotBlank())
        val now = System.currentTimeMillis()
        dao.insertAccount(
            AccountEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                type = type.name,
                currencyCode = openingBalance.currencyCode,
                openingBalanceMinor = openingBalance.minor,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun getAccountForEdit(id: String): EditableAccount {
        val account = requireNotNull(dao.getActiveAccount(id)) { "Account is missing or archived" }
        return EditableAccount(
            id = account.id,
            name = account.name,
            type = AccountType.valueOf(account.type),
            openingBalance = Money(account.openingBalanceMinor, account.currencyCode),
        )
    }

    override suspend fun updateAccount(account: EditableAccount) {
        require(account.name.isNotBlank()) { "Account name is required" }
        val stored = requireNotNull(dao.getActiveAccount(account.id)) { "Account is missing or archived" }
        require(account.openingBalance.currencyCode == stored.currencyCode) { "Account currency cannot be changed" }
        dao.updateAccount(
            stored.copy(
                name = account.name.trim(),
                type = account.type.name,
                openingBalanceMinor = account.openingBalance.minor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun archiveAccount(id: String) {
        dao.archiveAccount(id, System.currentTimeMillis())
    }

    override suspend fun restoreAccount(id: String) {
        dao.restoreAccount(id, System.currentTimeMillis())
    }

    override suspend fun transfer(
        amount: Money,
        sourceAccountId: String,
        destinationAccountId: String,
        note: String,
    ) {
        require(amount.minor > 0)
        require(sourceAccountId != destinationAccountId)
        requireAccountCurrency(sourceAccountId, amount.currencyCode)
        requireAccountCurrency(destinationAccountId, amount.currencyCode)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertTransaction(
            TransactionEntity(id, TransactionKind.TRANSFER.name, LocalDate.now().toString(), "Transfer", note.trim(), now, now),
            listOf(
                TransactionEntryEntity(UUID.randomUUID().toString(), id, sourceAccountId, null, -amount.minor, amount.currencyCode, true),
                TransactionEntryEntity(UUID.randomUUID().toString(), id, destinationAccountId, null, amount.minor, amount.currencyCode, true),
            ),
        )
    }

    override suspend fun reconcile(accountId: String, statementBalance: Money, createAdjustment: Boolean) {
        requireAccountCurrency(accountId, statementBalance.currencyCode)
        val calculated = dao.getAccountBalance(accountId)
        val difference = statementBalance.minor - calculated
        val now = System.currentTimeMillis()
        val adjustmentId = if (difference != 0L && createAdjustment) UUID.randomUUID().toString() else null
        val adjustment = adjustmentId?.let {
            TransactionEntity(it, if (difference > 0) "INCOME" else "EXPENSE", LocalDate.now().toString(), "Reconciliation adjustment", "", now, now)
        }
        val entry = adjustmentId?.let {
            TransactionEntryEntity(UUID.randomUUID().toString(), it, accountId, null, difference, statementBalance.currencyCode, true)
        }
        dao.insertReconciliationWithAdjustment(
            com.northstar.money.core.database.ReconciliationEntity(
                UUID.randomUUID().toString(), accountId, LocalDate.now().toString(),
                statementBalance.minor, calculated, difference, adjustmentId, now,
            ),
            adjustment,
            entry,
        )
    }

    override suspend fun setBudget(categoryId: String, planned: Money) {
        require(planned.currencyCode == BASE_CURRENCY_CODE) { "Budgets use $BASE_CURRENCY_CODE" }
        val month = LocalDate.now().withDayOfMonth(1).toString()
        dao.upsertBudget(
            com.northstar.money.core.database.BudgetAllocationEntity(
                "$month|$categoryId", month, categoryId, planned.minor
            )
        )
    }

    override suspend fun createGoal(name: String, target: Money, saved: Money, targetDate: String?) {
        require(name.isNotBlank() && target.minor > 0 && saved.minor >= 0)
        require(target.currencyCode == saved.currencyCode) { "Goal and saved amount currencies must match" }
        val normalizedTargetDate = targetDate?.takeIf { it.isNotBlank() }?.also { LocalDate.parse(it) }
        val now = System.currentTimeMillis()
        val goalId = UUID.randomUUID().toString()
        dao.insertGoalWithInitialContribution(
            goal = com.northstar.money.core.database.GoalEntity(
                goalId, name.trim(), target.minor, 0,
                target.currencyCode, normalizedTargetDate, "ACTIVE", now,
            ),
            initialContribution = saved.takeIf { it.minor > 0 }?.let {
                GoalContributionEntity(
                    UUID.randomUUID().toString(), goalId, it.minor, LocalDate.now().toString(),
                    "Opening saved amount", now, now,
                )
            },
        )
    }

    override suspend fun getGoalForEdit(id: String): EditableGoal {
        val goal = requireNotNull(dao.getGoal(id)) { "Savings goal is missing" }
        return EditableGoal(
            id = goal.id,
            name = goal.name,
            target = Money(goal.targetMinor, goal.currencyCode),
            targetLocalDate = goal.targetLocalDate,
            status = goal.status,
        )
    }

    override suspend fun updateGoal(goal: EditableGoal) {
        require(goal.name.isNotBlank() && goal.target.minor > 0) { "Name and positive target are required" }
        require(goal.status in GOAL_STATUSES) { "Invalid goal status" }
        val targetDate = goal.targetLocalDate?.takeIf { it.isNotBlank() }?.also { LocalDate.parse(it) }
        val stored = requireNotNull(dao.getGoal(goal.id)) { "Savings goal is missing" }
        require(goal.target.currencyCode == stored.currencyCode) { "Goal currency cannot be changed" }
        dao.updateGoal(
            stored.copy(
                name = goal.name.trim(),
                targetMinor = goal.target.minor,
                targetLocalDate = targetDate,
                status = goal.status,
            ),
        )
    }

    override suspend fun addGoalContribution(goalId: String, amount: Money, localDate: String, note: String) {
        require(amount.minor > 0) { "Contribution must be positive" }
        val goal = requireNotNull(dao.getGoal(goalId)) { "Savings goal is missing" }
        require(amount.currencyCode == goal.currencyCode) { "Contribution currency must match the goal" }
        val date = LocalDate.parse(localDate).toString()
        require(note.length <= 500) { "Contribution note is too long" }
        val now = System.currentTimeMillis()
        dao.insertGoalContribution(
            GoalContributionEntity(
                UUID.randomUUID().toString(), goalId, amount.minor, date, note.trim(), now, now,
            ),
        )
    }

    override suspend fun getGoalContributionForEdit(id: String): GoalContribution {
        val contribution = requireNotNull(dao.getGoalContributionForEdit(id)) { "Contribution is missing or deleted" }
        val goal = requireNotNull(dao.getGoal(contribution.goalId)) { "Savings goal is missing" }
        return GoalContribution(
            contribution.id,
            contribution.goalId,
            goal.name,
            Money(contribution.amountMinor, goal.currencyCode),
            contribution.localDate,
            contribution.note,
        )
    }

    override suspend fun updateGoalContribution(contribution: GoalContribution) {
        require(contribution.amount.minor > 0) { "Contribution must be positive" }
        require(contribution.note.length <= 500) { "Contribution note is too long" }
        val date = LocalDate.parse(contribution.localDate).toString()
        val stored = requireNotNull(dao.getGoalContributionForEdit(contribution.id)) {
            "Contribution is missing or deleted"
        }
        val goal = requireNotNull(dao.getGoal(contribution.goalId)) { "Savings goal is missing" }
        require(contribution.amount.currencyCode == goal.currencyCode) { "Contribution currency must match the goal" }
        dao.updateGoalContribution(
            stored.copy(
                goalId = contribution.goalId,
                amountMinor = contribution.amount.minor,
                localDate = date,
                note = contribution.note.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteGoalContribution(id: String) {
        dao.deleteGoalContribution(id, System.currentTimeMillis())
    }

    override suspend fun restoreGoalContribution(id: String) {
        dao.restoreGoalContribution(id, System.currentTimeMillis())
    }

    override suspend fun createRecurring(
        name: String,
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String?,
        frequency: String,
        nextDate: String,
    ) {
        require(name.isNotBlank() && amount.minor > 0)
        require(kind == TransactionKind.INCOME || kind == TransactionKind.EXPENSE) { "Recurring transfers are not supported" }
        requireAccountCurrency(accountId, amount.currencyCode)
        val requiredCategoryId = requireNotNull(categoryId) { "Category is required" }
        require(dao.getActiveCategoryKind(requiredCategoryId) == kind.name) {
            "Category does not match the recurring type"
        }
        require(frequency in FREQUENCIES) { "Unsupported recurring frequency" }
        LocalDate.parse(nextDate)
        dao.insertRecurring(
            com.northstar.money.core.database.RecurringScheduleEntity(
                UUID.randomUUID().toString(), name.trim(), kind.name, amount.minor,
                amount.currencyCode, accountId, requiredCategoryId, frequency, 1, nextDate, true,
                System.currentTimeMillis(),
            )
        )
    }

    override suspend fun getRecurringForEdit(id: String): EditableRecurring {
        val recurring = requireNotNull(dao.getRecurringForEdit(id)) { "Recurring schedule is missing or deleted" }
        return EditableRecurring(
            id = recurring.id,
            name = recurring.name,
            kind = TransactionKind.valueOf(recurring.kind),
            amount = Money(recurring.amountMinor, recurring.currencyCode),
            accountId = recurring.accountId,
            categoryId = recurring.categoryId?.let { dao.getCanonicalActiveCategoryId(it) },
            frequency = recurring.frequency,
            intervalCount = recurring.intervalCount,
            nextLocalDate = recurring.nextLocalDate,
        )
    }

    override suspend fun updateRecurring(recurring: EditableRecurring) {
        require(recurring.name.isNotBlank() && recurring.amount.minor > 0) {
            "Name and positive amount are required"
        }
        require(recurring.kind == TransactionKind.INCOME || recurring.kind == TransactionKind.EXPENSE) {
            "Recurring transfers are not supported"
        }
        require(recurring.frequency in FREQUENCIES && recurring.intervalCount > 0) {
            "Unsupported recurring frequency"
        }
        LocalDate.parse(recurring.nextLocalDate)
        val stored = requireNotNull(dao.getRecurringForEdit(recurring.id)) { "Recurring schedule is missing or deleted" }
        require(recurring.amount.currencyCode == stored.currencyCode) { "Recurring currency cannot be changed" }
        requireAccountCurrency(recurring.accountId, recurring.amount.currencyCode)
        val categoryId = requireNotNull(recurring.categoryId) { "Category is required" }
        require(dao.getActiveCategoryKind(categoryId) == recurring.kind.name) {
            "Category does not match the recurring type"
        }
        dao.updateRecurring(
            stored.copy(
                name = recurring.name.trim(),
                kind = recurring.kind.name,
                amountMinor = recurring.amount.minor,
                accountId = recurring.accountId,
                categoryId = categoryId,
                frequency = recurring.frequency,
                intervalCount = recurring.intervalCount,
                nextLocalDate = recurring.nextLocalDate,
            ),
        )
    }

    override suspend fun pauseRecurring(id: String) {
        dao.pauseRecurring(id)
    }

    override suspend fun resumeRecurring(id: String) {
        val recurring = requireNotNull(dao.getRecurringForEdit(id)) { "Recurring schedule is missing or deleted" }
        requireAccountCurrency(recurring.accountId, recurring.currencyCode)
        val categoryId = requireNotNull(recurring.categoryId?.let { dao.getCanonicalActiveCategoryId(it) }) {
            "Choose an active category before resuming"
        }
        require(dao.getActiveCategoryKind(categoryId) == recurring.kind) { "Category does not match the recurring type" }
        if (categoryId != recurring.categoryId) dao.updateRecurring(recurring.copy(categoryId = categoryId))
        dao.resumeRecurring(id)
    }

    override suspend fun deleteRecurring(id: String) {
        dao.deleteRecurring(id, System.currentTimeMillis())
    }

    override suspend fun restoreRecurring(id: String) {
        dao.restoreRecurring(id)
    }

    override suspend fun createDebt(
        accountId: String,
        annualRateBasisPoints: Int,
        minimumPayment: Money,
        dueDay: Int,
    ) {
        require(annualRateBasisPoints >= 0 && minimumPayment.minor >= 0 && dueDay in 1..31)
        requireAccountCurrency(accountId, minimumPayment.currencyCode)
        dao.insertDebtProfile(
            com.northstar.money.core.database.DebtProfileEntity(
                UUID.randomUUID().toString(), accountId, annualRateBasisPoints,
                minimumPayment.minor, dueDay, System.currentTimeMillis(),
            )
        )
    }

    override suspend fun getDebtForEdit(id: String): com.northstar.money.domain.model.DebtProfile {
        val debt = requireNotNull(dao.getDebtProfile(id)) { "Debt profile is missing" }
        val currency = requireNotNull(dao.getActiveAccountCurrency(debt.accountId)) {
            "Debt account is missing or archived"
        }
        return com.northstar.money.domain.model.DebtProfile(
            debt.id,
            debt.accountId,
            debt.annualRateBasisPoints,
            Money(debt.minimumPaymentMinor, currency),
            debt.dueDay,
        )
    }

    override suspend fun updateDebt(debt: com.northstar.money.domain.model.DebtProfile) {
        require(debt.annualRateBasisPoints >= 0 && debt.minimumPayment.minor >= 0 && debt.dueDay in 1..31) {
            "Rate, minimum payment or due day is invalid"
        }
        val stored = requireNotNull(dao.getDebtProfile(debt.id)) { "Debt profile is missing" }
        require(debt.accountId == stored.accountId) { "Debt account cannot be changed" }
        requireAccountCurrency(stored.accountId, debt.minimumPayment.currencyCode)
        dao.updateDebtProfile(
            stored.copy(
                annualRateBasisPoints = debt.annualRateBasisPoints,
                minimumPaymentMinor = debt.minimumPayment.minor,
                dueDay = debt.dueDay,
            ),
        )
    }

    override suspend fun importCsv(csv: String): com.northstar.money.domain.model.ImportResult {
        val accounts = dao.observeAccounts().first()
        val categories = dao.observeCategories().first()
        val validation = csvValidator.validate(csv, accounts, categories)
        if (validation.errors > 0) {
            return com.northstar.money.domain.model.ImportResult(0, validation.skippedDuplicates, validation.errors)
        }
        val now = System.currentTimeMillis()
        val items = validation.transactions.map { row ->
            val transactionId = UUID.randomUUID().toString()
            TransactionImportItem(
                transaction = TransactionEntity(
                    transactionId,
                    row.kind.name,
                    row.localDate,
                    row.payee,
                    "",
                    now,
                    now,
                ),
                entry = TransactionEntryEntity(
                    UUID.randomUUID().toString(),
                    transactionId,
                    row.accountId,
                    row.categoryId,
                    row.amountMinor,
                    row.currencyCode,
                    true,
                ),
            )
        }
        val writeResult = dao.importTransactions(items)
        return com.northstar.money.domain.model.ImportResult(
            imported = writeResult.imported,
            skippedDuplicates = validation.skippedDuplicates + writeResult.skippedDuplicates,
            errors = 0,
        )
    }

    override suspend fun createCategory(name: String, kind: CategoryKind) {
        require(name.isNotBlank())
        dao.insertCategory(
            CategoryEntity(
                UUID.randomUUID().toString(), name.trim(), kind.name,
                dao.observeCategories().first().count { it.kind == kind.name },
            )
        )
    }

    override suspend fun renameCategory(id: String, name: String) {
        require(name.isNotBlank()) { "Category name is required" }
        dao.renameCategory(id, name.trim())
    }

    override suspend fun archiveCategory(id: String) {
        dao.archiveCategory(id, System.currentTimeMillis())
    }

    override suspend fun restoreCategory(id: String) {
        dao.restoreCategory(id)
    }

    override suspend fun mergeCategory(sourceId: String, targetId: String) {
        dao.mergeCategory(sourceId, targetId, System.currentTimeMillis())
    }

    override suspend fun undoCategoryMerge(id: String) {
        dao.undoCategoryMerge(id)
    }

    override suspend fun createFullBackup(): String = backupCodec.encode(
        snapshot = dao.exportSnapshot(),
        databaseVersion = NorthstarDatabase.VERSION,
    )

    override suspend fun restoreFullBackup(backup: String, recoveryPassword: CharArray) = restoreMutex.withLock {
        restoreFullBackupLocked(backup, recoveryPassword)
    }

    override suspend fun undoLastFullRestore(recoveryPassword: CharArray) = restoreMutex.withLock {
        val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
        val encryptedRecovery = requireNotNull(recoveryStore.load()) { "No restore recovery is available" }
        val recoveryDocument = portableBackupCodec.decrypt(encryptedRecovery, recoveryPassword)
        restoreFullBackupLocked(recoveryDocument, recoveryPassword)
    }

    private suspend fun restoreFullBackupLocked(backup: String, recoveryPassword: CharArray) {
        val document = backupCodec.decode(backup)
        require(document.databaseVersion <= NorthstarDatabase.VERSION) {
            "Backup requires a newer Northstar Money database version"
        }
        val snapshot = normalizeLegacyGoalSavings(document.toSnapshot())
        snapshotValidator.validate(snapshot)

        val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
        val recoveryDocument = backupCodec.encode(
            snapshot = dao.exportSnapshot(),
            databaseVersion = NorthstarDatabase.VERSION,
        )
        recoveryStore.save(portableBackupCodec.encrypt(recoveryDocument, recoveryPassword))
        dao.replaceWithSnapshot(snapshot)
    }

    private fun normalizeLegacyGoalSavings(snapshot: DatabaseSnapshot): DatabaseSnapshot {
        val goalsWithSavedAmount = snapshot.goals.filter { it.savedMinor > 0 }
        if (goalsWithSavedAmount.isEmpty()) return snapshot

        val contributionIds = snapshot.goalContributions.mapTo(mutableSetOf()) { it.id }
        val migratedContributions = goalsWithSavedAmount.map { goal ->
            val contributionId = "legacy-${goal.id}"
            require(contributionIds.add(contributionId)) {
                "Backup contains a conflicting legacy goal contribution"
            }
            GoalContributionEntity(
                id = contributionId,
                goalId = goal.id,
                amountMinor = goal.savedMinor,
                localDate = Instant.ofEpochMilli(goal.createdAt).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                note = "Opening saved amount",
                createdAt = goal.createdAt,
                updatedAt = goal.createdAt,
            )
        }
        return snapshot.copy(
            goals = snapshot.goals.map { it.copy(savedMinor = 0) },
            goalContributions = snapshot.goalContributions + migratedContributions,
        )
    }

    private fun transactionRowToDomain(row: com.northstar.money.core.database.TransactionRow) = TransactionItem(
        id = row.id,
        payee = row.payee,
        categoryName = row.categoryName,
        accountName = row.accountName,
        kind = TransactionKind.valueOf(row.kind),
        amount = Money(row.amountMinor, row.currencyCode),
        localDate = row.localDate,
    )

    private fun recurringToDomain(row: com.northstar.money.core.database.RecurringScheduleEntity) =
        com.northstar.money.domain.model.RecurringItem(
            row.id,
            row.name,
            TransactionKind.valueOf(row.kind),
            Money(row.amountMinor, row.currencyCode),
            row.nextLocalDate,
            row.frequency,
            row.intervalCount,
        )

    private fun goalContributionToDomain(row: com.northstar.money.core.database.GoalContributionRow) =
        GoalContribution(
            row.id,
            row.goalId,
            row.goalName,
            Money(row.amountMinor, row.currencyCode),
            row.localDate,
            row.note,
        )

    private suspend fun requireAccountCurrency(accountId: String, currencyCode: String) {
        val accountCurrency = requireNotNull(dao.getActiveAccountCurrency(accountId)) {
            "Account is missing or archived"
        }
        require(accountCurrency == currencyCode) {
            "Account uses $accountCurrency; $currencyCode operations are not supported for it"
        }
    }

    companion object {
        private const val BASE_CURRENCY_CODE = "EUR"
        private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")
        private val GOAL_STATUSES = setOf("ACTIVE", "PAUSED", "COMPLETED")
    }
}
