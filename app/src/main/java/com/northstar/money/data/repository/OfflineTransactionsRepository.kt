package com.northstar.money.data.repository

import kotlinx.coroutines.flow.first
import com.northstar.money.domain.model.TransactionPage
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map
import com.northstar.money.domain.repository.TransactionsRepository

internal class OfflineTransactionsRepository(
    private val dao: SuspendingFinanceDao,
    private val rates: OfflineExchangeRatesRepository,
) : TransactionsRepository {
    override fun observeTransactions(): LiveData<List<TransactionItem>> = RepositoryTasks.observe {
        dao.observeTransactions().map { rows ->
                rows.map(::transactionRowToDomain)
            }
    }

    override fun observeTransactionWindow(window: com.northstar.money.domain.model.TransactionWindow): LiveData<TransactionPage> = RepositoryTasks.observe {
        dao.observeTransactionWindow(window.startDate, window.endDate, window.categoryId, window.limit + 1).map { rows ->
                com.northstar.money.domain.model.TransactionPage(rows.take(window.limit).map(::transactionRowToDomain), rows.size > window.limit)
            }
    }

    override fun observeRecentTransactions(): LiveData<List<TransactionItem>> = RepositoryTasks.observe {
        dao.observeRecentTransactions().map { rows -> rows.map(::transactionRowToDomain) }
    }

    override fun observeDeletedTransactions(): LiveData<List<TransactionItem>> = RepositoryTasks.observe {
        dao.observeDeletedTransactions().map { rows -> rows.map(::transactionRowToDomain) }
    }

    override fun addTransaction(
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String,
        payee: String,
    ): ListenableFuture<Void?> = RepositoryTasks.write {
        require(kind != TransactionKind.TRANSFER) { "Transfers require two entries" }
        require(amount.minor > 0) { "Amount must be positive" }
        dao.requireAccountCurrency(accountId, amount.currencyCode)
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
        rates.requestHistoricalRate(id, entry, localDate)
    }

    override fun deleteTransaction(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        require(dao.deleteTransaction(id, System.currentTimeMillis()) == 1) { "Transaction is already deleted or missing" }
    }

    override fun restoreTransaction(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        require(dao.restoreTransaction(id, System.currentTimeMillis()) == 1) { "Transaction is not available for recovery" }
    }

    override fun getTransactionForEdit(id: String): ListenableFuture<EditableTransaction> = RepositoryTasks.read {
        val stored = dao.getTransactionForEdit(id)
        require(!stored.isReconciliationAdjustment) { "Reconciliation adjustments cannot be edited directly" }
        val kind = TransactionKind.valueOf(stored.transaction.kind)
        return@read if (kind == TransactionKind.TRANSFER) {
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

    override fun updateTransaction(transaction: EditableTransaction): ListenableFuture<Void?> = RepositoryTasks.write {
        val stored = dao.getTransactionForEdit(transaction.id)
        require(!stored.isReconciliationAdjustment) { "Reconciliation adjustments cannot be edited directly" }
        require(stored.transaction.kind == transaction.kind.name) { "Transaction type cannot be changed" }
        require(transaction.amount.minor > 0) { "Amount must be positive" }
        val localDate = LocalDate.parse(transaction.localDate).toString()
        val updatedAt = System.currentTimeMillis()
        val updatedTransaction = stored.transaction.withDetails(
            localDate,
            transaction.payee.trim().ifBlank {
                if (transaction.kind == TransactionKind.TRANSFER) "Transfer" else transaction.kind.name.lowercase().replaceFirstChar(Char::uppercase)
            },
            transaction.note.trim(),
            updatedAt,
        )
        val updatedEntries = if (transaction.kind == TransactionKind.TRANSFER) {
            require(stored.entries.size == 2) { "Transfer must contain exactly two entries" }
            val destinationAccountId = requireNotNull(transaction.destinationAccountId) { "Destination account is required" }
            val destinationAmount = requireNotNull(transaction.destinationAmount) { "Destination amount is required" }
            require(destinationAmount.minor > 0) { "Destination amount must be positive" }
            require(transaction.accountId != destinationAccountId) { "Transfer accounts must be different" }
            dao.requireAccountCurrency(transaction.accountId, transaction.amount.currencyCode)
            dao.requireAccountCurrency(destinationAccountId, destinationAmount.currencyCode)
            val effectiveDestinationAmount = if (transaction.amount.currencyCode == destinationAmount.currencyCode) {
                transaction.amount
            } else {
                destinationAmount
            }
            val source = requireNotNull(stored.entries.singleOrNull { it.amountMinor < 0 }) { "Transfer source is invalid" }
            val destination = requireNotNull(stored.entries.singleOrNull { it.amountMinor > 0 }) { "Transfer destination is invalid" }
            listOf(
                source.withDetails(
                    transaction.accountId,
                    null,
                    Math.negateExact(transaction.amount.minor),
                    transaction.amount.currencyCode,
                ),
                destination.withDetails(
                    destinationAccountId,
                    null,
                    effectiveDestinationAmount.minor,
                    effectiveDestinationAmount.currencyCode,
                ),
            )
        } else {
            require(stored.entries.size == 1) { "Income and expense transactions must contain one entry" }
            val categoryId = requireNotNull(transaction.categoryId) { "Category is required" }
            dao.requireAccountCurrency(transaction.accountId, transaction.amount.currencyCode)
            require(dao.getActiveCategoryKind(categoryId) == transaction.kind.name) {
                "Category does not match the transaction type"
            }
            listOf(
                stored.entries.single().withDetails(
                    transaction.accountId,
                    categoryId,
                    if (transaction.kind == TransactionKind.EXPENSE) {
                        Math.negateExact(transaction.amount.minor)
                    } else {
                        transaction.amount.minor
                    },
                    transaction.amount.currencyCode,
                ),
            )
        }
        dao.updateTransaction(updatedTransaction, updatedEntries)
        updatedEntries.forEach { rates.requestHistoricalRate(transaction.id, it, localDate) }
    }

    override fun transfer(
        sourceAmount: Money,
        destinationAmount: Money,
        sourceAccountId: String,
        destinationAccountId: String,
        note: String,
    ): ListenableFuture<Void?> = RepositoryTasks.write {
        require(sourceAmount.minor > 0 && destinationAmount.minor > 0)
        require(sourceAccountId != destinationAccountId)
        dao.requireAccountCurrency(sourceAccountId, sourceAmount.currencyCode)
        dao.requireAccountCurrency(destinationAccountId, destinationAmount.currencyCode)
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
        entries.forEach { rates.requestHistoricalRate(id, it, localDate) }
    }

    override fun setTransactionCleared(id: String, cleared: Boolean): ListenableFuture<Void?> = RepositoryTasks.write {
        dao.setTransactionCleared(id, cleared)
    }

    override fun reconcile(
        accountId: String,
        statementLocalDate: String,
        statementBalance: Money,
        createAdjustment: Boolean,
    ): ListenableFuture<Void?> = RepositoryTasks.write {
        val date = LocalDate.parse(statementLocalDate)
        require(!date.isAfter(LocalDate.now())) { "Statement date cannot be in the future" }
        dao.requireAccountCurrency(accountId, statementBalance.currencyCode)
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
        adjustmentEntry?.let { rates.requestHistoricalRate(it.transactionId, it, date.toString()) }
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

    override fun allTransactions(): ListenableFuture<List<TransactionItem>> = RepositoryTasks.read {
        dao.observeTransactions().first().map(::transactionRowToDomain)
    }

}
