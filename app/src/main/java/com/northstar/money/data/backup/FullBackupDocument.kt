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
