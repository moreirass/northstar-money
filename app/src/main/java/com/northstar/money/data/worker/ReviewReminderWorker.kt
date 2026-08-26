package com.northstar.money.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val enabled = com.northstar.money.core.datastore.UserPreferences(applicationContext)
                .settings.first().remindersEnabled
            if (enabled && (
                    android.os.Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    )
            ) {
                val notification = NotificationCompat.Builder(applicationContext, "upcoming")
                    .setSmallIcon(com.northstar.money.R.drawable.ic_launcher_foreground)
                    .setContentTitle("Review your financial plan")
                    .setContentText("Check upcoming bills, budgets, and your 30-day forecast.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(applicationContext).notify(1001, notification)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Financial review reminder failed", error)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to "review_reminder_failed"))
            }
        }
    }

    companion object {
        private const val TAG = "ReviewReminderWorker"
        private const val MAX_RETRIES = 2
    }
}
