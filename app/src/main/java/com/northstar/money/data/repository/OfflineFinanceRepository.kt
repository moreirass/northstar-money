package com.northstar.money.data.repository

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.FinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineFinanceRepository(
    private val dao: FinanceDao,
    private val backupCodec: FullBackupJsonCodec = FullBackupJsonCodec(),
) : FinanceRepository {
    override fun observeAccounts(): Flow<List<Account>> = dao.observeAccounts().map { rows ->
        rows.map { Account(it.id, it.name, AccountType.valueOf(it.type), it.currencyCode, Money(it.balanceMinor, it.currencyCode)) }
    }

    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { rows ->
        rows.map { Category(it.id, it.name, CategoryKind.valueOf(it.kind)) }
    }

    override fun observeTransactions(): Flow<List<TransactionItem>> =
        dao.observeTransactions().map { rows ->
            rows.map {
                TransactionItem(
                    id = it.id,
                    payee = it.payee,
                    categoryName = it.categoryName,
                    accountName = it.accountName,
                    kind = TransactionKind.valueOf(it.kind),
                    amount = Money(it.amountMinor, it.currencyCode),
                    localDate = it.localDate,
                )
            }
        }

    override fun observeSummary(): Flow<FinanceSummary> {
        val monthStart = LocalDate.now().withDayOfMonth(1).toString()
        return dao.observeSummary(monthStart).map {
            FinanceSummary(Money(it.balanceMinor), Money(it.incomeMinor), Money(it.expenseMinor))
        }
    }

    override fun observeBudgets() = dao.observeBudgets(LocalDate.now().withDayOfMonth(1).toString()).map { rows ->
        rows.map {
            com.northstar.money.domain.model.BudgetProgress(
                it.categoryId, it.categoryName, Money(it.plannedMinor), Money(it.spentMinor)
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
                it.id, it.accountId, it.annualRateBasisPoints, Money(it.minimumPaymentMinor), it.dueDay
            )
        }
    }

    override suspend fun seedIfEmpty() {
        if (dao.observeAccounts().first().isEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertAccounts(
                listOf(AccountEntity("main-account", "Main account", "CHECKING", "EUR", 0, createdAt = now, updatedAt = now))
            )
        }
        if (dao.observeCategories().first().isEmpty()) {
            val expenseNames = listOf("Housing", "Groceries", "Transport", "Dining", "Health", "Shopping", "Other")
            val categories = expenseNames.mapIndexed { index, name ->
                CategoryEntity("expense-${name.lowercase()}", name, "EXPENSE", index)
            } + CategoryEntity("income-salary", "Salary", "INCOME", 0)
            dao.insertCategories(categories)
        }
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

    override suspend fun deleteTransaction(id: String) = dao.deleteTransaction(id)

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
        var imported = 0
        var duplicates = 0
        var errors = 0
        csv.lineSequence().drop(1).filter { it.isNotBlank() }.forEach { line ->
            runCatching {
                val columns = parseCsvLine(line)
                require(columns.size >= 7)
                val date = LocalDate.parse(columns[0]).toString()
                val kind = TransactionKind.valueOf(columns[1])
                require(kind != TransactionKind.TRANSFER)
                val payee = columns[2]
                val category = categories.firstOrNull { it.name == columns[3] }
                    ?: categories.first { it.kind == if (kind == TransactionKind.INCOME) "INCOME" else "EXPENSE" }
                val account = accounts.firstOrNull { it.name == columns[4] } ?: accounts.first()
                val signedMinor = columns[5].toLong()
                val amountMinor = if (kind == TransactionKind.EXPENSE) -kotlin.math.abs(signedMinor) else kotlin.math.abs(signedMinor)
                if (dao.countMatchingTransaction(date, payee, account.id, amountMinor) > 0) {
                    duplicates++
                } else {
                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    dao.insertTransaction(
                        TransactionEntity(id, kind.name, date, payee, "", now, now),
                        TransactionEntryEntity(
                            UUID.randomUUID().toString(), id, account.id, category.id,
                            amountMinor, columns[6], true,
                        ),
                    )
                    imported++
                }
            }.onFailure { errors++ }
        }
        return com.northstar.money.domain.model.ImportResult(imported, duplicates, errors)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    result += value.toString()
                    value.clear()
                }
                else -> value.append(char)
            }
            index++
        }
        result += value.toString()
        return result
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
}
