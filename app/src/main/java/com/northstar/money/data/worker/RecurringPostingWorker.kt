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
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Automatic recurring posting failed", error)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to "recurring_posting_failed"))
            }
        }
    }

    companion object {
        const val STARTUP_WORK_NAME = "recurring-posting-startup"
        const val PERIODIC_WORK_NAME = "recurring-posting-periodic"
        private const val TAG = "RecurringPostingWorker"
        private const val MAX_RETRIES = 3
    }
}
