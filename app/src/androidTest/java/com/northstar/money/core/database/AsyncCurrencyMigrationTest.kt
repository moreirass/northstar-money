package com.northstar.money.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.datastore.UserPreferences
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AsyncCurrencyMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NorthstarDatabase::class.java)

    @Test fun constructionOnMainThreadDoesNotWaitAndUpgradeUsesPersistedCurrency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UserPreferences(context)
        val original = runBlocking { preferences.settings.first().baseCurrencyCode }
        val currency = CompletableFuture<String>()
        val executor = Executors.newSingleThreadExecutor()
        val name = "async-currency-upgrade"
        var room: NorthstarDatabase? = null
        try {
            runBlocking { preferences.setBaseCurrencyCode("JPY") }
            helper.createDatabase(name, 9).apply {
                execSQL("INSERT INTO categories VALUES ('c', 'Food', 'EXPENSE', 0, NULL, NULL)")
                execSQL("INSERT INTO budget_allocations VALUES ('b', '2026-10-01', 'c', 10000)")
                close()
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                room = Room.databaseBuilder(context, NorthstarDatabase::class.java, name)
                    .addMigrations(CurrencyAwareMigration(currency) { SchemaMigrations.currencyUpgrade(it) })
                    .build()
            }
            assertFalse(currency.isDone)
            val opened = executor.submit<String> {
                room!!.openHelper.writableDatabase.query("SELECT currencyCode, plannedMinor FROM budget_allocations").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(10000L, it.getLong(1))
                    it.getString(0)
                }
            }
            currency.complete(runBlocking { preferences.settings.first().baseCurrencyCode })
            assertEquals("JPY", opened.get(10, TimeUnit.SECONDS))
        } finally {
            currency.completeExceptionally(IllegalStateException("Test finished"))
            executor.shutdownNow()
            room?.close()
            context.deleteDatabase(name)
            runBlocking { preferences.setBaseCurrencyCode(original) }
        }
    }

    @Test fun preferenceFailureDoesNotSilentlyReinterpretBudgetsAsEuros() {
        val name = "failed-currency-upgrade"
        helper.createDatabase(name, 9).close()
        val currency = CompletableFuture<String>().apply { completeExceptionally(java.io.IOException("synthetic failure")) }
        assertThrows(IllegalStateException::class.java) {
            helper.runMigrationsAndValidate(name, 10, true, CurrencyAwareMigration(currency) { SchemaMigrations.currencyUpgrade(it) })
        }
    }
}
