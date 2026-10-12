package com.northstar.money.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.northstar.money.NorthstarApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ExchangeRateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as NorthstarApplication
        app.financeRepository.refreshPendingExchangeRates()
        val currency = app.userPreferences.settings.first().baseCurrencyCode
        val missing = app.database.financeDao().getEntriesMissingExchangeRates(currency).isNotEmpty() ||
            app.financeRepository.observeSummary().first().missingConversions > 0
        if (missing) { if (runAttemptCount < 3) Result.retry() else Result.failure() } else Result.success()
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }

    companion object { const val WORK_NAME = "historical-exchange-rates" }
}
