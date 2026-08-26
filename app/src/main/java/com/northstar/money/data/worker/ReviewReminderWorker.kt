package com.northstar.money.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val enabled = com.northstar.money.core.datastore.UserPreferences(applicationContext)
            .settings.first().remindersEnabled
        if (!enabled) return Result.success()
        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
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
        return Result.success()
    }
}
