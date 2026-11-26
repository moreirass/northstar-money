package com.northstar.money.data.repository

import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionImportItem
import com.northstar.money.data.importing.TransactionCsvValidator
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.northstar.money.domain.repository.ImportsRepository

internal class OfflineImportsRepository(
    private val dao: SuspendingFinanceDao,
    private val csvValidator: TransactionCsvValidator,
    private val rates: OfflineExchangeRatesRepository,
) : ImportsRepository {
    override fun importCsv(csv: String): ListenableFuture<com.northstar.money.domain.model.ImportResult> = RepositoryTasks.read {
        val accounts = dao.observeAccounts().first()
        val categories = dao.observeCategories().first()
        val validation = csvValidator.validate(csv, accounts, categories)
        if (validation.errors > 0) {
            return@read com.northstar.money.domain.model.ImportResult(0, validation.skippedDuplicates, validation.errors)
        }
        val now = System.currentTimeMillis()
        val items = validation.transactions.map { row ->
            val transactionId = UUID.randomUUID().toString()
            TransactionImportItem(
                TransactionEntity(
                    transactionId,
                    row.kind.name,
                    row.localDate,
                    row.payee,
                    "",
                    now,
                    now,
                ),
                TransactionEntryEntity(
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
                ?.let { stored -> rates.requestHistoricalRate(stored.transaction.id, stored.entries.single(), stored.transaction.localDate) }
        }
        return@read com.northstar.money.domain.model.ImportResult(
            imported = writeResult.imported,
            skippedDuplicates = validation.skippedDuplicates + writeResult.skippedDuplicates,
            errors = 0,
        )
    }

}
