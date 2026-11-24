package com.northstar.money.core.database

import androidx.room.migration.Migration
import com.northstar.money.core.database.reference.MIGRATION_1_2 as original1To2
import com.northstar.money.core.database.reference.MIGRATION_2_3 as original2To3
import com.northstar.money.core.database.reference.MIGRATION_3_4 as original3To4
import com.northstar.money.core.database.reference.MIGRATION_4_5 as original4To5
import com.northstar.money.core.database.reference.MIGRATION_5_6 as original5To6
import com.northstar.money.core.database.reference.MIGRATION_6_7 as original6To7
import com.northstar.money.core.database.reference.MIGRATION_7_8 as original7To8
import com.northstar.money.core.database.reference.MIGRATION_8_9 as original8To9
import com.northstar.money.core.database.reference.migration9To10 as original9To10
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationSqlCompatibilityTest {
    @Test fun allUpgradeStepsPreserveStatementsOrderAndCurrencyBinding() {
        val original = listOf(original1To2, original2To3, original3To4, original4To5,
            original5To6, original6To7, original7To8, original8To9, original9To10("JPY"))
        val migrated = listOf(SchemaMigrations.V1_TO_2, SchemaMigrations.V2_TO_3, SchemaMigrations.V3_TO_4,
            SchemaMigrations.V4_TO_5, SchemaMigrations.V5_TO_6, SchemaMigrations.V6_TO_7,
            SchemaMigrations.V7_TO_8, SchemaMigrations.V8_TO_9, SchemaMigrations.currencyUpgrade("JPY"))
        original.zip(migrated).forEach { (before, after) ->
            assertEquals(before.startVersion, after.startVersion)
            assertEquals(before.endVersion, after.endVersion)
            assertEquals(statements(before), statements(after))
        }
    }

    private fun statements(migration: Migration): List<Pair<String, List<*>>> {
        val calls = mutableListOf<Pair<String, List<*>>>()
        val type = SupportSQLiteDatabase::class.java
        val recorder = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            check(method.name == "execSQL")
            calls += (args[0] as String) to ((args.getOrNull(1) as? Array<*>)?.toList() ?: emptyList<Any>())
            null
        } as SupportSQLiteDatabase
        migration.migrate(recorder)
        return calls
    }
}
