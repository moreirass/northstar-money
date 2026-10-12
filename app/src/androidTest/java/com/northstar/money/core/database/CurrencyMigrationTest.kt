package com.northstar.money.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CurrencyMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NorthstarDatabase::class.java)

    @Test fun upgradePreservesAmountsAndInstallationCurrencyAndCalendarAnchor() {
        val name = "currency-migration-9-10"
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO accounts VALUES ('a', 'Main', 'CHECKING', 'JPY', 0, NULL, 1, 1)")
            execSQL("INSERT INTO categories VALUES ('c', 'Food', 'EXPENSE', 0, NULL, NULL)")
            execSQL("INSERT INTO categories VALUES ('b', 'Earlier', 'EXPENSE', 0, 1, 'c')")
            execSQL("INSERT INTO categories VALUES ('old', 'Original', 'EXPENSE', 0, 1, 'b')")
            execSQL("INSERT INTO budget_allocations VALUES ('budget', '2026-10-01', 'c', 10000)")
            execSQL("INSERT INTO recurring_schedules VALUES ('r', 'Bill', 'EXPENSE', 100, 'JPY', 'a', 'c', 'MONTHLY', 1, '2026-10-31', 1, 1, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 10, true, migration9To10("JPY")).apply {
            query("SELECT currencyCode, plannedMinor FROM budget_allocations").use {
                it.moveToFirst(); assertEquals("JPY", it.getString(0)); assertEquals(10000L, it.getLong(1))
            }
            query("SELECT anchorDay, anchorMonth FROM recurring_schedules").use {
                it.moveToFirst(); assertEquals(31, it.getInt(0)); assertEquals(10, it.getInt(1))
            }
            query("SELECT mergedIntoCategoryId FROM categories WHERE id = 'old'").use {
                it.moveToFirst(); assertEquals("c", it.getString(0))
            }
            close()
        }
    }
}
