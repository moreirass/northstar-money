package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FullBackupDocument(
    val format: String,
    val formatVersion: Int,
    val databaseVersion: Int,
    val createdAtEpochMillis: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transactionEntries: List<TransactionEntryEntity>,
    val reconciliations: List<ReconciliationEntity>,
    val budgetAllocations: List<BudgetAllocationEntity>,
    val goals: List<GoalEntity>,
    val recurringSchedules: List<RecurringScheduleEntity>,
    val debtProfiles: List<DebtProfileEntity>,
) {
    fun toSnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        accounts = accounts,
        categories = categories,
        transactions = transactions,
        transactionEntries = transactionEntries,
        reconciliations = reconciliations,
        budgetAllocations = budgetAllocations,
        goals = goals,
        recurringSchedules = recurringSchedules,
        debtProfiles = debtProfiles,
    )
}

class FullBackupJsonCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(
        snapshot: DatabaseSnapshot,
        databaseVersion: Int,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ): String = json.encodeToString(
        FullBackupDocument(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            databaseVersion = databaseVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            accounts = snapshot.accounts,
            categories = snapshot.categories,
            transactions = snapshot.transactions,
            transactionEntries = snapshot.transactionEntries,
            reconciliations = snapshot.reconciliations,
            budgetAllocations = snapshot.budgetAllocations,
            goals = snapshot.goals,
            recurringSchedules = snapshot.recurringSchedules,
            debtProfiles = snapshot.debtProfiles,
        ),
    )

    fun decode(encoded: String): FullBackupDocument {
        val document = json.decodeFromString<FullBackupDocument>(encoded)
        require(document.format == FORMAT) { "Not a Northstar Money database backup" }
        require(document.formatVersion == FORMAT_VERSION) {
            "Unsupported backup format version ${document.formatVersion}"
        }
        require(document.databaseVersion > 0) { "Invalid database version" }
        return document
    }

    companion object {
        const val FORMAT = "northstar-money-database-backup"
        const val FORMAT_VERSION = 1
    }
}
