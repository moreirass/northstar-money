package com.northstar.money

import android.app.Application
import androidx.room.Room
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.repository.OfflineFinanceRepository
import com.northstar.money.data.backup.FileRestoreRecoveryStore
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.map

class NorthstarApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, NorthstarDatabase::class.java, "northstar.db")
            .addMigrations(com.northstar.money.core.database.MIGRATION_1_2)
            .addMigrations(com.northstar.money.core.database.MIGRATION_2_3)
            .addMigrations(com.northstar.money.core.database.MIGRATION_3_4)
            .addMigrations(com.northstar.money.core.database.MIGRATION_4_5)
            .addMigrations(com.northstar.money.core.database.MIGRATION_5_6)
            .addMigrations(com.northstar.money.core.database.MIGRATION_6_7)
            .addMigrations(com.northstar.money.core.database.MIGRATION_7_8)
            .build()
    }

    val userPreferences by lazy { com.northstar.money.core.datastore.UserPreferences(this) }
    val financeRepository by lazy {
        OfflineFinanceRepository(
            dao = database.financeDao(),
            restoreRecoveryStore = FileRestoreRecoveryStore(this),
            baseCurrencyCode = userPreferences.settings.map { it.baseCurrencyCode },
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel("upcoming", "Upcoming bills", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel("budget", "Budget warnings", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel("backup", "Backup reminders", NotificationManager.IMPORTANCE_LOW),
                )
            )
        }
        runCatching {
            val workManager = WorkManager.getInstance(this)
            workManager.enqueueUniquePeriodicWork(
                "financial-review-reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<com.northstar.money.data.worker.ReviewReminderWorker>(
                    24, TimeUnit.HOURS
                ).build(),
            )
            workManager.enqueueUniquePeriodicWork(
                com.northstar.money.data.worker.RecurringPostingWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<com.northstar.money.data.worker.RecurringPostingWorker>(
                    6, TimeUnit.HOURS,
                ).build(),
            )
            workManager.enqueueUniqueWork(
                com.northstar.money.data.worker.RecurringPostingWorker.STARTUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<com.northstar.money.data.worker.RecurringPostingWorker>().build(),
            )
        }.onFailure { error ->
            android.util.Log.e("NorthstarApplication", "Could not schedule background financial work", error)
        }
    }
}
