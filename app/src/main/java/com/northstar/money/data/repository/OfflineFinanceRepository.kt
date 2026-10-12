package com.northstar.money.data.repository

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.FinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionImportItem
import com.northstar.money.core.database.ReceiptAttachmentEntity
import com.northstar.money.core.database.TransactionExchangeRateEntity
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
import com.northstar.money.domain.model.ReceiptAttachment
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.domain.repository.FinanceRepository
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.backup.FullBackupJsonCodec
import com.northstar.money.data.backup.DatabaseSnapshotValidator
import com.northstar.money.data.backup.PortableBackupCodec
import com.northstar.money.data.backup.RestoreRecoveryStore
import com.northstar.money.data.importing.TransactionCsvValidator
import com.northstar.money.data.receipt.ReceiptOcrEngine
import com.northstar.money.data.receipt.ReceiptImageNormalizer
import com.northstar.money.data.exchange.HistoricalRateClient
import com.northstar.money.data.exchange.HistoricalRateProvider
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.northstar.money.core.database.AccountExchangeRateEntity
import com.northstar.money.core.database.ReceiptMetadataRow
import com.northstar.money.domain.model.nextOccurrence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineFinanceRepository(
    private val dao: FinanceDao,
    private val backupCodec: FullBackupJsonCodec = FullBackupJsonCodec(),
    private val restoreRecoveryStore: RestoreRecoveryStore? = null,
    private val portableBackupCodec: PortableBackupCodec = PortableBackupCodec(),
    private val snapshotValidator: DatabaseSnapshotValidator = DatabaseSnapshotValidator(),
    private val csvValidator: TransactionCsvValidator = TransactionCsvValidator(),
    private val receiptOcrEngine: ReceiptOcrEngine = ReceiptOcrEngine(),
    private val historicalRateProvider: HistoricalRateProvider = HistoricalRateClient(),
    private val baseCurrencyCode: Flow<String> = flowOf(DEFAULT_BASE_CURRENCY_CODE),
    private val scheduleRateRefresh: (() -> Unit)? = null,
) : FinanceRepository {
    private val restoreMutex = Mutex()
    private val rateRefreshMutex = Mutex()

    override fun observeAccounts(): Flow<List<Account>> = dao.observeAccounts().map { rows ->
        rows.map {
            Account(
                it.id, it.name, AccountType.valueOf(it.type), it.currencyCode,
                Money(it.balanceMinor, it.currencyCode),
                Money(it.clearedBalanceMinor, it.currencyCode),
            )
        }
    }

    override fun observeArchivedAccounts(): Flow<List<Account>> = dao.observeArchivedAccounts().map { rows ->
        rows.map {
            Account(
                it.id, it.name, AccountType.valueOf(it.type), it.currencyCode,
                Money(it.balanceMinor, it.currencyCode),
                Money(it.clearedBalanceMinor, it.currencyCode),
            )
        }
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

    override fun observeTransactionWindow(window: com.northstar.money.domain.model.TransactionWindow): Flow<com.northstar.money.domain.model.TransactionPage> =
        dao.observeTransactionWindow(window.startDate, window.endDate, window.categoryId, window.limit + 1).map { rows ->
            com.northstar.money.domain.model.TransactionPage(rows.take(window.limit).map(::transactionRowToDomain), rows.size > window.limit)
        }

    override fun observeMonthSummary(monthStart: String, currency: String): Flow<FinanceSummary> = dao.observeSummary(monthStart, currency).map {
        FinanceSummary(Money(it.balanceMinor, currency), Money(it.incomeMinor, currency), Money(it.expenseMinor, currency), it.missingConversions)
    }

    override fun observeCategorySpending(accountId: String?, currency: String): Flow<List<com.northstar.money.domain.model.CategorySpending>> =
        dao.observeCategorySpending(accountId, currency).map { rows -> rows.map { com.northstar.money.domain.model.CategorySpending(it.categoryName, Money(it.amountMinor, currency)) } }

    override fun observeMonthlyExpenses(categoryId: String, currency: String, start: String, end: String): Flow<List<com.northstar.money.domain.model.MonthlyExpense>> =
        dao.observeMonthlyExpenses(categoryId, currency, start, end).map { rows -> rows.map { com.northstar.money.domain.model.MonthlyExpense(it.monthStart, Money(it.amountMinor, currency)) } }

    override fun observeRecentTransactions(): Flow<List<TransactionItem>> = dao.observeRecentTransactions().map { rows -> rows.map(::transactionRowToDomain) }

    override suspend fun requestExchangeRateRefresh() {
        if (scheduleRateRefresh != null) scheduleRateRefresh.invoke() else refreshPendingExchangeRates()
    }

    override fun observeDeletedTransactions(): Flow<List<TransactionItem>> =
        dao.observeDeletedTransactions().map { rows -> rows.map(::transactionRowToDomain) }

    override fun observeReceiptAttachments(): Flow<List<ReceiptAttachment>> =
        dao.observeReceiptAttachments().map { rows -> rows.map(::receiptAttachmentToDomain) }

    override fun observeHistoricalExchangeRates(): Flow<List<HistoricalExchangeRate>> =
        dao.observeTransactionExchangeRates().map { rows -> rows.map(::exchangeRateToDomain) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSummary(): Flow<FinanceSummary> {
        return currentDates().combine(baseCurrencyCode.distinctUntilChanged()) { date, currency -> date to currency }
            .flatMapLatest { (date, currencyCode) ->
                dao.observeSummary(date.withDayOfMonth(1).toString(), currencyCode).map {
                    FinanceSummary(Money(it.balanceMinor, currencyCode), Money(it.incomeMinor, currencyCode),
                        Money(it.expenseMinor, currencyCode), it.missingConversions)
                }
            }
    }

    private fun currentDates() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LocalDate.now())
            val now = java.time.ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            kotlinx.coroutines.delay(java.time.Duration.between(now, next).toMillis().coerceIn(1_000L, 60_000L))
        }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeBudgets() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LocalDate.now().withDayOfMonth(1))
            val now = java.time.ZonedDateTime.now()
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            kotlinx.coroutines.delay(
                java.time.Duration.between(now, nextDay).toMillis().coerceIn(1_000L, 60_000L),
            )
        }
    }.distinctUntilChanged().combine(baseCurrencyCode.distinctUntilChanged()) { month, currencyCode ->
        month to currencyCode
    }.flatMapLatest { (month, currencyCode) ->
        dao.observeBudgets(
            monthStart = month.toString(),
            nextMonthStart = month.plusMonths(1).toString(),
            baseCurrencyCode = currencyCode,
        ).map { rows -> currencyCode to rows }
    }.map { (currencyCode, rows) ->
        rows.map {
            com.northstar.money.domain.model.BudgetProgress(
                categoryId = it.categoryId,
                categoryName = it.categoryName,
                planned = Money(it.plannedMinor, currencyCode),
                spent = Money(it.spentMinor, currencyCode),
                allocated = Money(it.allocatedMinor, currencyCode),
                rollover = Money(it.rolloverMinor, currencyCode),
                missingConversions = it.missingConversions,
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
        val currencyCode = baseCurrencyCode.first()
        val expenseNames = listOf("Housing", "Groceries", "Transport", "Dining", "Health", "Shopping", "Other")
        val categories = expenseNames.mapIndexed { index, name ->
            CategoryEntity("expense-${name.lowercase()}", name, "EXPENSE", index)
        } + CategoryEntity("income-salary", "Salary", "INCOME", 0)
        dao.seedIfEmpty(
            defaultAccounts = listOf(
                AccountEntity(INITIAL_ACCOUNT_ID, "Conta Principal", "CHECKING", currencyCode, 0, createdAt = now, updatedAt = now),
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
        val localDate = LocalDate.now().toString()
        val entry = TransactionEntryEntity(
            UUID.randomUUID().toString(), id, accountId, categoryId,
            if (kind == TransactionKind.EXPENSE) -amount.minor else amount.minor,
            amount.currencyCode, false,
        )
        dao.insertTransaction(
            TransactionEntity(id, kind.name, localDate, payee.trim(), "", now, now),
            entry,
        )
        requestHistoricalRate(id, entry, localDate)
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
            if (source.currencyCode == destination.currencyCode) {
                require(Math.addExact(source.amountMinor, destination.amountMinor) == 0L) {
                    "Same-currency transfer is unbalanced"
                }
            }
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
                destinationAmount = Money(destination.amountMinor, destination.currencyCode),
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
            val destinationAmount = requireNotNull(transaction.destinationAmount) { "Destination amount is required" }
            require(destinationAmount.minor > 0) { "Destination amount must be positive" }
            require(transaction.accountId != destinationAccountId) { "Transfer accounts must be different" }
            requireAccountCurrency(transaction.accountId, transaction.amount.currencyCode)
            requireAccountCurrency(destinationAccountId, destinationAmount.currencyCode)
            val effectiveDestinationAmount = if (transaction.amount.currencyCode == destinationAmount.currencyCode) {
                transaction.amount
            } else {
                destinationAmount
            }
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
                    amountMinor = effectiveDestinationAmount.minor,
                    currencyCode = effectiveDestinationAmount.currencyCode,
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
        updatedEntries.forEach { requestHistoricalRate(transaction.id, it, localDate) }
    }

    override suspend fun addReceiptAttachment(
        transactionId: String,
        originalName: String,
        mimeType: String,
        content: ByteArray,
    ) {
        require(content.isNotEmpty() && content.size <= MAX_RECEIPT_BYTES) { "Receipt images must be 8 MB or smaller" }
        require(mimeType.startsWith("image/")) { "Only receipt images are supported" }
        val stored = dao.getTransactionForEdit(transactionId)
        val normalizedContent = withContext(Dispatchers.Default) { ReceiptImageNormalizer.normalize(content) }
        val attachment = ReceiptAttachmentEntity(
            id = UUID.randomUUID().toString(),
            transactionId = transactionId,
            content = normalizedContent,
            originalName = originalName.trim().ifBlank { "receipt" },
            mimeType = "image/jpeg",
            byteSize = normalizedContent.size.toLong(),
            createdAt = System.currentTimeMillis(),
        )
        dao.insertReceiptAttachment(attachment)
        processReceiptOcr(attachment, stored.entries.first().currencyCode)
    }

    override suspend fun deleteReceiptAttachment(id: String) {
        require(dao.deleteReceiptAttachment(id) == 1) { "Receipt attachment is missing" }
    }

    override suspend fun retryReceiptOcr(id: String) {
        val attachment = requireNotNull(dao.getReceiptAttachment(id)) { "Receipt attachment is missing" }
        val stored = dao.getTransactionForEdit(attachment.transactionId)
        processReceiptOcr(attachment, stored.entries.first().currencyCode)
    }

    override suspend fun applyReceiptOcr(id: String) {
        val attachment = requireNotNull(dao.getReceiptAttachment(id)) { "Receipt attachment is missing" }
        val transaction = getTransactionForEdit(attachment.transactionId)
        require(transaction.kind != TransactionKind.TRANSFER) { "Receipt values cannot be applied to a transfer" }
        val detectedAmount = attachment.detectedAmountMinor?.let { minor ->
            val currency = attachment.detectedCurrencyCode ?: transaction.amount.currencyCode
            require(currency == transaction.amount.currencyCode) { "Receipt currency does not match the account" }
            Money(minor, currency)
        }
        updateTransaction(
            transaction.copy(
                amount = detectedAmount ?: transaction.amount,
                localDate = attachment.detectedLocalDate ?: transaction.localDate,
                payee = attachment.detectedMerchant ?: transaction.payee,
            ),
        )
    }

    override suspend fun refreshPendingExchangeRates(): Int = rateRefreshMutex.withLock {
        val currency = baseCurrencyCode.first()
        var refreshed = 0
        val slots = Semaphore(4)
        val candidates = dao.getEntriesMissingExchangeRates(currency)
        // Limit queued coroutine objects as well as concurrent HTTP requests.
        candidates.chunked(64).forEach { batch ->
            val results = coroutineScope {
                batch.map { candidate -> async {
                    slots.withPermit {
                        try {
                            recordHistoricalRate(candidate.transactionId, TransactionEntryEntity(
                                candidate.entryId, candidate.transactionId, candidate.accountId, candidate.categoryId,
                                candidate.amountMinor, candidate.currencyCode, candidate.cleared,
                            ), candidate.localDate)
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { false }
                    }
                } }.awaitAll()
            }
            refreshed += results.count { it }
        }
        val storedRates = dao.getAllAccountExchangeRates().associateBy { it.accountId }
        dao.getActiveAccountsForRates().filter { it.currencyCode != currency && it.openingBalanceMinor != 0L }.forEach { account ->
            val existing = storedRates[account.id]
            if (existing?.quoteCurrencyCode == currency && existing.baseCurrencyCode == account.currencyCode &&
                existing.openingBalanceMinor == account.openingBalanceMinor) return@forEach
            try {
                val date = Instant.ofEpochMilli(account.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                val quote = historicalRateProvider.getRate(account.currencyCode, currency, date)
                if (baseCurrencyCode.first() == currency) {
                    dao.storeOpeningRateIfUnchanged(AccountExchangeRateEntity(account.id, account.currencyCode, currency,
                        account.openingBalanceMinor, HistoricalRateClient.convertMinor(account.openingBalanceMinor,
                            account.currencyCode, currency, quote.rateMicros), quote.rateMicros, date))
                    refreshed++
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Summary exposes missing conversions; retry when online. */ }
        }
        refreshed
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
        scheduleRateRefresh?.invoke()
    }

    override suspend fun configureInitialAccount(name: String, openingBalance: Money) {
        require(name.isNotBlank()) { "Account name is required" }
        require(openingBalance.minor >= 0) { "Initial balance cannot be negative" }
        val seededAccount = dao.getActiveAccount(INITIAL_ACCOUNT_ID)
        if (seededAccount != null && dao.countEntriesForAccount(INITIAL_ACCOUNT_ID) == 0) {
            dao.updateAccount(
                seededAccount.copy(
                    name = name.trim(),
                    currencyCode = openingBalance.currencyCode,
                    openingBalanceMinor = openingBalance.minor,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            createAccount(name, AccountType.CHECKING, openingBalance)
        }
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
        scheduleRateRefresh?.invoke()
    }

    override suspend fun archiveAccount(id: String) {
        dao.archiveAccount(id, System.currentTimeMillis())
    }

    override suspend fun restoreAccount(id: String) {
        dao.restoreAccount(id, System.currentTimeMillis())
    }

    override suspend fun transfer(
        sourceAmount: Money,
        destinationAmount: Money,
        sourceAccountId: String,
        destinationAccountId: String,
        note: String,
    ) {
        require(sourceAmount.minor > 0 && destinationAmount.minor > 0)
        require(sourceAccountId != destinationAccountId)
        requireAccountCurrency(sourceAccountId, sourceAmount.currencyCode)
        requireAccountCurrency(destinationAccountId, destinationAmount.currencyCode)
        require(
            sourceAmount.currencyCode != destinationAmount.currencyCode ||
                sourceAmount.minor == destinationAmount.minor,
        ) { "Same-currency transfer amounts must match" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val localDate = LocalDate.now().toString()
        val entries = listOf(
            TransactionEntryEntity(
                UUID.randomUUID().toString(), id, sourceAccountId, null,
                Math.negateExact(sourceAmount.minor), sourceAmount.currencyCode, false,
            ),
            TransactionEntryEntity(
                UUID.randomUUID().toString(), id, destinationAccountId, null,
                destinationAmount.minor, destinationAmount.currencyCode, false,
            ),
        )
        dao.insertTransaction(
            TransactionEntity(id, TransactionKind.TRANSFER.name, localDate, "Transfer", note.trim(), now, now),
            entries,
        )
        entries.forEach { requestHistoricalRate(id, it, localDate) }
    }

    override suspend fun setTransactionCleared(id: String, cleared: Boolean) {
        dao.setTransactionCleared(id, cleared)
    }

    override suspend fun reconcile(
        accountId: String,
        statementLocalDate: String,
        statementBalance: Money,
        createAdjustment: Boolean,
    ) {
        val date = LocalDate.parse(statementLocalDate)
        require(!date.isAfter(LocalDate.now())) { "Statement date cannot be in the future" }
        requireAccountCurrency(accountId, statementBalance.currencyCode)
        val now = System.currentTimeMillis()
        val adjustmentEntry = dao.reconcileAccount(
            accountId = accountId,
            currencyCode = statementBalance.currencyCode,
            statementLocalDate = date.toString(),
            statementBalanceMinor = statementBalance.minor,
            createAdjustment = createAdjustment,
            reconciliationId = UUID.randomUUID().toString(),
            adjustmentTransactionId = UUID.randomUUID().toString(),
            adjustmentEntryId = UUID.randomUUID().toString(),
            completedAt = now,
        )
        adjustmentEntry?.let { requestHistoricalRate(it.transactionId, it, date.toString()) }
    }

    override suspend fun setBudget(categoryId: String, planned: Money) {
        val currencyCode = baseCurrencyCode.first()
        require(planned.currencyCode == currencyCode) { "Budgets use $currencyCode" }
        require(planned.minor >= 0) { "Budget must not be negative" }
        require(dao.getActiveCategoryKind(categoryId) == "EXPENSE") { "Choose an active expense category" }
        val month = LocalDate.now().withDayOfMonth(1).toString()
        dao.upsertBudget(
            com.northstar.money.core.database.BudgetAllocationEntity(
                "$month|$categoryId|$currencyCode", month, categoryId, planned.minor, currencyCode
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
        require(recurring.frequency in FREQUENCIES && recurring.intervalCount in 1..1_000) {
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
                anchorDay = if (stored.nextLocalDate != recurring.nextLocalDate) LocalDate.parse(recurring.nextLocalDate).dayOfMonth else stored.anchorDay,
                anchorMonth = if (stored.nextLocalDate != recurring.nextLocalDate) LocalDate.parse(recurring.nextLocalDate).monthValue else stored.anchorMonth,
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

    override suspend fun postDueRecurringOccurrences(throughLocalDate: String): Int {
        val throughDate = LocalDate.parse(throughLocalDate)
        var posted = 0
        dao.getDueRecurringSchedules(throughDate.toString()).forEach { initial ->
            var occurrenceDate = LocalDate.parse(initial.nextLocalDate)
            while (occurrenceDate <= throughDate && posted < MAX_RECURRING_POSTS_PER_RUN) {
                val nextDate = nextOccurrence(occurrenceDate, initial.frequency, initial.intervalCount, initial.anchorDay, initial.anchorMonth)
                val occurrenceKey = "${initial.id}:${occurrenceDate}"
                val transactionId = UUID.nameUUIDFromBytes("transaction:$occurrenceKey".toByteArray()).toString()
                val entryId = UUID.nameUUIDFromBytes("entry:$occurrenceKey".toByteArray()).toString()
                val inserted = dao.postRecurringOccurrence(
                    recurringId = initial.id,
                    occurrenceLocalDate = occurrenceDate.toString(),
                    nextLocalDate = nextDate.toString(),
                    transactionId = transactionId,
                    entryId = entryId,
                    createdAt = System.currentTimeMillis(),
                )
                if (!inserted) return@forEach
                val stored = dao.getTransactionForEdit(transactionId)
                requestHistoricalRate(transactionId, stored.entries.single(), occurrenceDate.toString())
                posted += 1
                occurrenceDate = nextDate
            }
        }
        return posted
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
                    false,
                ),
            )
        }
        val writeResult = dao.importTransactions(items)
        items.forEach { item ->
            suspendResult { dao.getTransactionForEdit(item.transaction.id) }
                .getOrNull()
                ?.let { stored -> requestHistoricalRate(stored.transaction.id, stored.entries.single(), stored.transaction.localDate) }
        }
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
        baseCurrencyCode = baseCurrencyCode.first(),
    )

    override suspend fun writeFullBackup(output: java.io.OutputStream, password: CharArray) {
        val snapshot = dao.exportSnapshot()
        val currency = baseCurrencyCode.first()
        withContext(Dispatchers.IO) {
            portableBackupCodec.encryptTo(output, password) { plaintext ->
                backupCodec.encodeTo(plaintext, snapshot, NorthstarDatabase.VERSION, currency)
            }
        }
    }

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
        val rawSnapshot = if (document.databaseVersion < 10) normalizeLegacySnapshot(document.toSnapshot()) else document.toSnapshot()
        val snapshot = normalizeLegacyGoalSavings(if (document.databaseVersion < 10) rawSnapshot.copy(
            budgetAllocations = rawSnapshot.budgetAllocations.map { it.copy(currencyCode = document.baseCurrencyCode ?: baseCurrencyCode.first()) },
        ) else rawSnapshot)
        snapshotValidator.validate(snapshot)

        val recoveryStore = requireNotNull(restoreRecoveryStore) { "Restore recovery storage is unavailable" }
        dao.replaceWithRecovery(snapshot) { previous ->
            val currency = baseCurrencyCode.first()
            withContext(Dispatchers.IO) {
                recoveryStore.write { output -> portableBackupCodec.encryptTo(output, recoveryPassword) { plaintext ->
                    backupCodec.encodeTo(plaintext, previous, NorthstarDatabase.VERSION, currency)
                } }
            }
        }
        scheduleRateRefresh?.invoke()
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

    private fun normalizeLegacySnapshot(snapshot: DatabaseSnapshot): DatabaseSnapshot {
        require(snapshot.categories.size <= 10_000) { "Backup contains too many categories" }
        val categories = snapshot.categories.associateBy { it.id }
        val entries = snapshot.transactionEntries.associateBy { it.id }
        val transactions = snapshot.transactions.associateBy { it.id }
        return snapshot.copy(
            categories = snapshot.categories.map { category ->
                var target = category.mergedIntoCategoryId
                val visited = mutableSetOf(category.id)
                while (target != null) {
                    require(visited.size < 100) { "Backup category merge chain is too long" }
                    require(visited.add(target)) { "Backup contains a category merge cycle" }
                    val next = requireNotNull(categories[target]) { "Backup category merge target is missing" }
                    require(next.kind == category.kind) { "Backup category merge types differ" }
                    if (next.mergedIntoCategoryId == null) {
                        if (next.archivedAt != null) target = null
                        break
                    }
                    target = next.mergedIntoCategoryId
                }
                category.copy(mergedIntoCategoryId = target)
            },
            transactionExchangeRates = snapshot.transactionExchangeRates.map { rate ->
                val entry = entries[rate.entryId]
                val transaction = entry?.let { transactions[it.transactionId] }
                if (entry != null && transaction != null && (rate.transactionId != transaction.id ||
                    rate.rateLocalDate != transaction.localDate || rate.baseCurrencyCode != entry.currencyCode)) {
                    rate.copy(transactionId = transaction.id, rateLocalDate = transaction.localDate,
                        baseCurrencyCode = entry.currencyCode, rateMicros = null, convertedAmountMinor = null,
                        status = "PENDING", fetchedAt = null)
                } else rate
            },
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
        cleared = row.cleared,
        createdAt = row.createdAt,
        accountId = row.accountId, destinationAccountId = row.destinationAccountId, categoryId = row.categoryId,
    )

    private fun receiptAttachmentToDomain(row: ReceiptMetadataRow) = ReceiptAttachment(
        id = row.id,
        transactionId = row.transactionId,
        originalName = row.originalName,
        mimeType = row.mimeType,
        byteSize = row.byteSize,
        createdAt = row.createdAt,
        ocrStatus = row.ocrStatus,
        detectedAmount = row.detectedAmountMinor?.let { minor ->
            Money(minor, row.detectedCurrencyCode ?: BASE_CURRENCY_CODE)
        },
        detectedLocalDate = row.detectedLocalDate,
        detectedMerchant = row.detectedMerchant,
    )

    private fun exchangeRateToDomain(row: TransactionExchangeRateEntity) = HistoricalExchangeRate(
        id = row.id,
        transactionId = row.transactionId,
        entryId = row.entryId,
        baseCurrencyCode = row.baseCurrencyCode,
        quoteCurrencyCode = row.quoteCurrencyCode,
        rateMicros = row.rateMicros,
        convertedAmountMinor = row.convertedAmountMinor,
        rateLocalDate = row.rateLocalDate,
        source = row.source,
        status = row.status,
    )

    private suspend fun processReceiptOcr(attachment: ReceiptAttachmentEntity, currencyCode: String) {
        dao.updateReceiptAttachment(attachment.copy(ocrStatus = "PROCESSING"))
        suspendResult { receiptOcrEngine.recognize(attachment.content, currencyCode) }
            .onSuccess { parsed ->
                dao.updateReceiptAttachment(
                    attachment.copy(
                        ocrStatus = "COMPLETE",
                        ocrText = parsed.rawText,
                        detectedAmountMinor = parsed.amount?.minor,
                        detectedCurrencyCode = parsed.amount?.currencyCode,
                        detectedLocalDate = parsed.localDate,
                        detectedMerchant = parsed.merchant,
                    ),
                )
            }
            .onFailure {
                dao.updateReceiptAttachment(attachment.copy(ocrStatus = "FAILED"))
            }
    }

    private suspend fun requestHistoricalRate(transactionId: String, entry: TransactionEntryEntity, localDate: String) {
        if (scheduleRateRefresh != null) scheduleRateRefresh.invoke()
        else recordHistoricalRate(transactionId, entry, localDate)
    }

    private suspend fun recordHistoricalRate(transactionId: String, entry: TransactionEntryEntity, localDate: String): Boolean {
        val currency = baseCurrencyCode.first()
        val existing = dao.getTransactionExchangeRate(entry.id)
        val pending = TransactionExchangeRateEntity("rate-${entry.id}", transactionId, entry.id,
            entry.currencyCode, currency, null, null, localDate, HistoricalRateClient.SOURCE, "PENDING", null)
        val result = suspendResult {
            val quote = if (existing?.status == "AVAILABLE" && existing.rateMicros != null &&
                existing.baseCurrencyCode == entry.currencyCode && existing.quoteCurrencyCode == currency &&
                existing.rateLocalDate == localDate) {
                com.northstar.money.data.exchange.RateQuote(localDate, existing.rateMicros, existing.source)
            } else historicalRateProvider.getRate(entry.currencyCode, currency, localDate)
            pending.copy(rateMicros = quote.rateMicros, convertedAmountMinor = HistoricalRateClient.convertMinor(
                entry.amountMinor, entry.currencyCode, currency, quote.rateMicros),
                source = if (quote.date == localDate) quote.source else "${quote.source} (${quote.date})",
                status = "AVAILABLE", fetchedAt = System.currentTimeMillis())
        }
        if (baseCurrencyCode.first() != currency) return false
        val rate = result.getOrElse {
            // A rate for a different date is an estimate, not the transaction's historical rate.
            pending
        }
        val stored = dao.storeRateIfUnchanged(rate, entry, localDate)
        return stored && rate.status == "AVAILABLE"
    }

    private suspend fun <T> suspendResult(operation: suspend () -> T): Result<T> = try {
        Result.success(operation())
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }

    private fun recurringToDomain(row: com.northstar.money.core.database.RecurringScheduleEntity) =
        com.northstar.money.domain.model.RecurringItem(
            row.id,
            row.name,
            TransactionKind.valueOf(row.kind),
            Money(row.amountMinor, row.currencyCode),
            row.nextLocalDate,
            row.frequency,
            row.intervalCount, row.anchorDay, row.anchorMonth,
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

    private fun nextRecurringDate(date: LocalDate, frequency: String, intervalCount: Int): LocalDate =
        when (frequency) {
            "WEEKLY" -> date.plusWeeks(intervalCount.toLong())
            "MONTHLY" -> date.plusMonths(intervalCount.toLong())
            "YEARLY" -> date.plusYears(intervalCount.toLong())
            else -> error("Unsupported recurring frequency")
        }

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
        private const val DEFAULT_BASE_CURRENCY_CODE = "EUR"
        private const val INITIAL_ACCOUNT_ID = "main-account"
        private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")
        private val GOAL_STATUSES = setOf("ACTIVE", "PAUSED", "COMPLETED")
        private const val MAX_RECURRING_POSTS_PER_RUN = 10_000
        private const val MAX_RECEIPT_BYTES = 8 * 1024 * 1024
    }
}
