package com.northstar.money.core.database

internal fun databaseSnapshot(
    accounts: List<AccountEntity>,
    accountExchangeRates: List<AccountExchangeRateEntity> = emptyList(),
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    transactionEntries: List<TransactionEntryEntity>,
    reconciliations: List<ReconciliationEntity>,
    budgetAllocations: List<BudgetAllocationEntity>,
    goals: List<GoalEntity>,
    goalContributions: List<GoalContributionEntity> = emptyList(),
    recurringSchedules: List<RecurringScheduleEntity>,
    debtProfiles: List<DebtProfileEntity>,
    receiptAttachments: List<ReceiptAttachmentEntity> = emptyList(),
    transactionExchangeRates: List<TransactionExchangeRateEntity> = emptyList(),
) = DatabaseSnapshot(
    accounts, accountExchangeRates, categories, transactions,
    transactionEntries, reconciliations, budgetAllocations, goals,
    goalContributions, recurringSchedules, debtProfiles, receiptAttachments,
    transactionExchangeRates,
)

internal fun DatabaseSnapshot.copy(
    accounts: List<AccountEntity> = this.accounts,
    accountExchangeRates: List<AccountExchangeRateEntity> = this.accountExchangeRates,
    categories: List<CategoryEntity> = this.categories,
    transactions: List<TransactionEntity> = this.transactions,
    transactionEntries: List<TransactionEntryEntity> = this.transactionEntries,
    reconciliations: List<ReconciliationEntity> = this.reconciliations,
    budgetAllocations: List<BudgetAllocationEntity> = this.budgetAllocations,
    goals: List<GoalEntity> = this.goals,
    goalContributions: List<GoalContributionEntity> = this.goalContributions,
    recurringSchedules: List<RecurringScheduleEntity> = this.recurringSchedules,
    debtProfiles: List<DebtProfileEntity> = this.debtProfiles,
    receiptAttachments: List<ReceiptAttachmentEntity> = this.receiptAttachments,
    transactionExchangeRates: List<TransactionExchangeRateEntity> = this.transactionExchangeRates,
) = DatabaseSnapshot(
    accounts, accountExchangeRates, categories, transactions,
    transactionEntries, reconciliations, budgetAllocations, goals,
    goalContributions, recurringSchedules, debtProfiles, receiptAttachments,
    transactionExchangeRates,
)
