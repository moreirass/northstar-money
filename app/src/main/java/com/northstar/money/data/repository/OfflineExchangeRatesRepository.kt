package com.northstar.money.data.repository

import androidx.concurrent.futures.await
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionExchangeRateEntity
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.data.exchange.HistoricalRateClient
import com.northstar.money.data.exchange.HistoricalRateProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.northstar.money.core.database.AccountExchangeRateEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.northstar.money.domain.repository.ExchangeRatesRepository

internal class OfflineExchangeRatesRepository(
    private val dao: SuspendingFinanceDao,
    private val baseCurrencyCode: Flow<String>,
    private val historicalRateProvider: HistoricalRateProvider,
    private val scheduleRateRefresh: (() -> Unit)?,
) : ExchangeRatesRepository {
    private val rateRefreshMutex = Mutex()

    override fun observeHistoricalExchangeRates(): LiveData<List<HistoricalExchangeRate>> = RepositoryTasks.observe {
        dao.observeTransactionExchangeRates().map { rows -> rows.map(::exchangeRateToDomain) }
    }

    override fun requestExchangeRateRefresh(): ListenableFuture<Void?> = RepositoryTasks.write {
        if (scheduleRateRefresh != null) scheduleRateRefresh.invoke() else refreshPendingExchangeRates().await()
    }

    override fun refreshPendingExchangeRates(): ListenableFuture<Int> = RepositoryTasks.read {
        rateRefreshMutex.withLock {
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
    }

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

    internal suspend fun requestHistoricalRate(transactionId: String, entry: TransactionEntryEntity, localDate: String) {
        if (scheduleRateRefresh != null) scheduleRateRefresh.invoke()
        else recordHistoricalRate(transactionId, entry, localDate)
    }

    private suspend fun recordHistoricalRate(transactionId: String, entry: TransactionEntryEntity, localDate: String): Boolean {
        val currency = baseCurrencyCode.first()
        val existing = dao.getTransactionExchangeRate(entry.id)
        val pending = TransactionExchangeRateEntity("rate-${entry.id}", transactionId, entry.id,
            entry.currencyCode, currency, null, null, localDate, HistoricalRateClient.SOURCE, "PENDING", null)
        val result = suspendResult {
            val storedRateMicros = existing?.rateMicros
            val quote = if (existing?.status == "AVAILABLE" && storedRateMicros != null &&
                existing.baseCurrencyCode == entry.currencyCode && existing.quoteCurrencyCode == currency &&
                existing.rateLocalDate == localDate) {
                com.northstar.money.data.exchange.RateQuote(localDate, storedRateMicros, existing.source)
            } else historicalRateProvider.getRate(entry.currencyCode, currency, localDate)
            pending.withQuote(quote.rateMicros, HistoricalRateClient.convertMinor(
                entry.amountMinor, entry.currencyCode, currency, quote.rateMicros),
                if (quote.date == localDate) quote.source else "${quote.source} (${quote.date})",
                System.currentTimeMillis())
        }
        if (baseCurrencyCode.first() != currency) return false
        val rate = result.getOrElse {
            // A rate for a different date is an estimate, not the transaction's historical rate.
            pending
        }
        val stored = dao.storeRateIfUnchanged(rate, entry, localDate)
        return stored && rate.status == "AVAILABLE"
    }
}
