package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountExchangeRateEntity
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.ReceiptAttachmentEntity
import com.northstar.money.core.database.TransactionExchangeRateEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

@Serializable
data class FullBackupDocument(
    val format: String,
    val formatVersion: Int,
    val databaseVersion: Int,
    val createdAtEpochMillis: Long,
    val accounts: List<AccountEntity>,
    val accountExchangeRates: List<AccountExchangeRateEntity> = emptyList(),
    val baseCurrencyCode: String? = null,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transactionEntries: List<TransactionEntryEntity>,
    val reconciliations: List<ReconciliationEntity>,
    val budgetAllocations: List<BudgetAllocationEntity>,
    val goals: List<GoalEntity>,
    val goalContributions: List<GoalContributionEntity> = emptyList(),
    val recurringSchedules: List<RecurringScheduleEntity>,
    val debtProfiles: List<DebtProfileEntity>,
    val receiptAttachments: List<ReceiptAttachmentEntity> = emptyList(),
    val transactionExchangeRates: List<TransactionExchangeRateEntity> = emptyList(),
) {
    fun toSnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        accounts = accounts,
        accountExchangeRates = accountExchangeRates,
        categories = categories,
        transactions = transactions,
        transactionEntries = transactionEntries,
        reconciliations = reconciliations,
        budgetAllocations = budgetAllocations,
        goals = goals,
        goalContributions = goalContributions,
        recurringSchedules = recurringSchedules,
        debtProfiles = debtProfiles,
        receiptAttachments = receiptAttachments,
        transactionExchangeRates = transactionExchangeRates,
    )
}

class FullBackupJsonCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun encodeTo(output: java.io.OutputStream, snapshot: DatabaseSnapshot, databaseVersion: Int, baseCurrencyCode: String) {
        json.encodeToStream(FullBackupDocument(FORMAT, FORMAT_VERSION, databaseVersion, System.currentTimeMillis(),
            accounts = snapshot.accounts, accountExchangeRates = snapshot.accountExchangeRates, baseCurrencyCode = baseCurrencyCode,
            categories = snapshot.categories, transactions = snapshot.transactions, transactionEntries = snapshot.transactionEntries,
            reconciliations = snapshot.reconciliations, budgetAllocations = snapshot.budgetAllocations, goals = snapshot.goals,
            goalContributions = snapshot.goalContributions, recurringSchedules = snapshot.recurringSchedules,
            debtProfiles = snapshot.debtProfiles, receiptAttachments = snapshot.receiptAttachments,
            transactionExchangeRates = snapshot.transactionExchangeRates), output)
    }

    fun encode(
        snapshot: DatabaseSnapshot,
        databaseVersion: Int,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
        baseCurrencyCode: String? = null,
    ): String = json.encodeToString(
        FullBackupDocument(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            databaseVersion = databaseVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            accounts = snapshot.accounts,
            accountExchangeRates = snapshot.accountExchangeRates,
            baseCurrencyCode = baseCurrencyCode,
            categories = snapshot.categories,
            transactions = snapshot.transactions,
            transactionEntries = snapshot.transactionEntries,
            reconciliations = snapshot.reconciliations,
            budgetAllocations = snapshot.budgetAllocations,
            goals = snapshot.goals,
            goalContributions = snapshot.goalContributions,
            recurringSchedules = snapshot.recurringSchedules,
            debtProfiles = snapshot.debtProfiles,
            receiptAttachments = snapshot.receiptAttachments,
            transactionExchangeRates = snapshot.transactionExchangeRates,
        ),
    )

    fun decode(encoded: String): FullBackupDocument {
        require(encoded.length <= com.northstar.money.data.importing.ImportLimits.BACKUP_BYTES) { "Backup is too large" }
        val document = json.decodeFromString<FullBackupDocument>(encoded)
        require(document.format == FORMAT) { "Not a Northstar Money database backup" }
        require(document.formatVersion in 1..FORMAT_VERSION) {
            "Unsupported backup format version ${document.formatVersion}"
        }
        require(document.baseCurrencyCode == null || com.northstar.money.domain.model.validCurrency(document.baseCurrencyCode)) { "Invalid backup currency" }
        require(document.databaseVersion > 0) { "Invalid database version" }
        return document
    }

    companion object {
        const val FORMAT = "northstar-money-database-backup"
        const val FORMAT_VERSION = 3
    }
}
