package com.northstar.money

import android.app.Application
import androidx.room.Room
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.SchemaMigrations
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.northstar.money.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class NorthstarApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rateRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val widgetPresence = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun refreshWidgetPresence() {
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        widgetPresence.value = manager.getAppWidgetIds(android.content.ComponentName(this,
            com.northstar.money.widget.FinanceWidgetProvider::class.java)).isNotEmpty()
    }

    val database by lazy {
        val migrationCurrency = SettingsRepository.get(this).readBaseCurrency()
        Room.databaseBuilder(this, NorthstarDatabase::class.java, "northstar.db")
            .addMigrations(*SchemaMigrations.beforeCurrencyUpgrade())
            .addMigrations(com.northstar.money.core.database.CurrencyAwareMigration(migrationCurrency) {
                SchemaMigrations.currencyUpgrade(it)
            })
            .build()
    }

    val userPreferences by lazy { com.northstar.money.core.datastore.UserPreferences(this) }
    val financeRepository by lazy {
        OfflineFinanceRepository(
            dao = database.financeDao(),
            restoreRecoveryStore = FileRestoreRecoveryStore(this),
            baseCurrencyCode = userPreferences.settings.map { it.baseCurrencyCode },
            scheduleRateRefresh = { rateRequests.trySend(Unit) },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        refreshWidgetPresence()
        applicationScope.launch {
            widgetPresence.flatMapLatest { present ->
                if (present) combine(financeRepository.observeSummary(), financeRepository.observeRecentTransactions(),
                    userPreferences.settings) { _, _, _ -> Unit } else kotlinx.coroutines.flow.emptyFlow()
            }.collect {
                try { com.northstar.money.widget.FinanceWidgetProvider.updateAll(this@NorthstarApplication) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: java.io.IOException) { android.util.Log.e("NorthstarWidget", "Widget settings unavailable") }
                catch (_: android.database.sqlite.SQLiteException) { android.util.Log.e("NorthstarWidget", "Widget database unavailable") }
            }
        }
        applicationScope.launch {
            for (request in rateRequests) {
                kotlinx.coroutines.delay(250)
                while (rateRequests.tryReceive().isSuccess) { /* coalesce batch writes */ }
                WorkManager.getInstance(this@NorthstarApplication).enqueueUniqueWork(
                    com.northstar.money.data.worker.ExchangeRateWorker.WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<com.northstar.money.data.worker.ExchangeRateWorker>()
                        .setConstraints(androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build(),
                )
            }
        }
        applicationScope.launch {
            userPreferences.settings.map { it.baseCurrencyCode }.distinctUntilChanged().collect { rateRequests.trySend(Unit) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel("upcoming", "Upcoming bills", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel("budget", "Budget warnings", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel("backup", "Backup reminders", NotificationManager.IMPORTANCE_LOW),
                )
            )
        }
        try {
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
        } catch (_: IllegalStateException) {
            android.util.Log.e("NorthstarApplication", "Could not schedule background financial work")
        }
    }
}
