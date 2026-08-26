package com.northstar.money.core.database

data class DatabaseSnapshot(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transactionEntries: List<TransactionEntryEntity>,
    val reconciliations: List<ReconciliationEntity>,
    val budgetAllocations: List<BudgetAllocationEntity>,
    val goals: List<GoalEntity>,
    val goalContributions: List<GoalContributionEntity> = emptyList(),
    val recurringSchedules: List<RecurringScheduleEntity>,
    val debtProfiles: List<DebtProfileEntity>,
)
