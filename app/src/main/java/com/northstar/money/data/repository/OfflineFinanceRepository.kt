package com.northstar.money.data.repository

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.FinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionImportItem
import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.AccountType
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.CategoryKind
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.repository.FinanceRepository
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.backup.FullBackupJsonCodec
import com.northstar.money.data.backup.DatabaseSnapshotValidator
import com.northstar.money.data.backup.PortableBackupCodec
import com.northstar.money.data.backup.RestoreRecoveryStore
import com.northstar.money.data.importing.TransactionCsvValidator
import java.time.LocalDate
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

    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { rows ->
        rows.map { Category(it.id, it.name, CategoryKind.valueOf(it.kind)) }
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
                Money(it.savedMinor, it.currencyCode), it.targetLocalDate,
            )
        }
    }

    override fun observeRecurring() = dao.observeRecurring().map { rows ->
        rows.map {
            com.northstar.money.domain.model.RecurringItem(
                it.id, it.name, TransactionKind.valueOf(it.kind),
                Money(it.amountMinor, it.currencyCode), it.nextLocalDate, it.frequency,
            )
        }
    }

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
        dao.insertGoal(
            com.northstar.money.core.database.GoalEntity(
                UUID.randomUUID().toString(), name.trim(), target.minor, saved.minor,
                target.currencyCode, targetDate?.takeIf { it.isNotBlank() }, "ACTIVE", System.currentTimeMillis(),
            )
        )
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
        requireAccountCurrency(accountId, amount.currencyCode)
        LocalDate.parse(nextDate)
        dao.insertRecurring(
            com.northstar.money.core.database.RecurringScheduleEntity(
                UUID.randomUUID().toString(), name.trim(), kind.name, amount.minor,
                amount.currencyCode, accountId, categoryId, frequency, 1, nextDate, true,
                System.currentTimeMillis(),
            )
        )
    }

    override suspend fun createDebt(
        accountId: String,
        annualRateBasisPoints: Int,
        minimumPayment: Money,
        dueDay: Int,
    ) {
        require(annualRateBasisPoints >= 0 && minimumPayment.minor >= 0 && dueDay in 1..31)
        requireAccountCurrency(accountId, minimumPayment.currencyCode)
        dao.upsertDebt(
            com.northstar.money.core.database.DebtProfileEntity(
                UUID.randomUUID().toString(), accountId, annualRateBasisPoints,
                minimumPayment.minor, dueDay, System.currentTimeMillis(),
            )
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
        val snapshot = document.toSnapshot()
        snapshotValidator.validate(snapshot)

        val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
        val recoveryDocument = backupCodec.encode(
            snapshot = dao.exportSnapshot(),
            databaseVersion = NorthstarDatabase.VERSION,
        )
        recoveryStore.save(portableBackupCodec.encrypt(recoveryDocument, recoveryPassword))
        dao.replaceWithSnapshot(snapshot)
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
    }
}
