package com.northstar.money

import android.app.Application
import androidx.room.Room
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.data.repository.OfflineFinanceRepository
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NorthstarApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, NorthstarDatabase::class.java, "northstar.db")
            .addMigrations(com.northstar.money.core.database.MIGRATION_1_2)
            .addMigrations(com.northstar.money.core.database.MIGRATION_2_3)
            .addMigrations(com.northstar.money.core.database.MIGRATION_3_4)
            .build()
    }

    val financeRepository by lazy { OfflineFinanceRepository(database.financeDao()) }
    val userPreferences by lazy { com.northstar.money.core.datastore.UserPreferences(this) }

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
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "financial-review-reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.northstar.money.data.worker.ReviewReminderWorker>(
                24, TimeUnit.HOURS
            ).build(),
        )
    }
}
