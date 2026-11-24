package com.northstar.money.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.northstar.money.NorthstarApplication
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class RecurringPostingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val application = applicationContext as? NorthstarApplication
                ?: return Result.failure(workDataOf("error" to "application_unavailable"))
            val posted = application.financeRepository.postDueRecurringOccurrences(LocalDate.now().toString())
            Result.success(workDataOf("posted" to posted))
        } catch (error: CancellationException) {
            throw error
        } catch (_: android.database.sqlite.SQLiteException) {
            retryStorageFailure()
        } catch (_: java.io.IOException) {
            retryStorageFailure()
        } catch (_: IllegalArgumentException) {
            android.util.Log.e(TAG, "Recurring posting validation failed")
            Result.failure(workDataOf("error" to "recurring_posting_failed"))
        } catch (_: ArithmeticException) {
            android.util.Log.e(TAG, "Recurring posting arithmetic failed")
            Result.failure(workDataOf("error" to "recurring_posting_failed"))
        }
    }

    private fun retryStorageFailure(): Result {
        // As mensagens e causas das exceções podem conter dados financeiros.
        android.util.Log.e(TAG, "Automatic recurring posting storage failed")
        return if (runAttemptCount < MAX_RETRIES) Result.retry()
        else Result.failure(workDataOf("error" to "recurring_posting_failed"))
    }

    companion object {
        const val STARTUP_WORK_NAME = "recurring-posting-startup"
        const val PERIODIC_WORK_NAME = "recurring-posting-periodic"
        private const val TAG = "RecurringPostingWorker"
        private const val MAX_RETRIES = 3
    }
}
