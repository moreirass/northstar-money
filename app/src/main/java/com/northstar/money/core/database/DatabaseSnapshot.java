package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Objects;

public final class DatabaseSnapshot {
    @NonNull private final List<AccountEntity> accounts;
    @NonNull private final List<AccountExchangeRateEntity> accountExchangeRates;
    @NonNull private final List<CategoryEntity> categories;
    @NonNull private final List<TransactionEntity> transactions;
    @NonNull private final List<TransactionEntryEntity> transactionEntries;
    @NonNull private final List<ReconciliationEntity> reconciliations;
    @NonNull private final List<BudgetAllocationEntity> budgetAllocations;
    @NonNull private final List<GoalEntity> goals;
    @NonNull private final List<GoalContributionEntity> goalContributions;
    @NonNull private final List<RecurringScheduleEntity> recurringSchedules;
    @NonNull private final List<DebtProfileEntity> debtProfiles;
    @NonNull private final List<ReceiptAttachmentEntity> receiptAttachments;
    @NonNull private final List<TransactionExchangeRateEntity> transactionExchangeRates;

    public DatabaseSnapshot(@NonNull List<AccountEntity> accounts, @NonNull List<AccountExchangeRateEntity> accountExchangeRates, @NonNull List<CategoryEntity> categories,
            @NonNull List<TransactionEntity> transactions, @NonNull List<TransactionEntryEntity> transactionEntries, @NonNull List<ReconciliationEntity> reconciliations,
            @NonNull List<BudgetAllocationEntity> budgetAllocations, @NonNull List<GoalEntity> goals, @NonNull List<GoalContributionEntity> goalContributions,
            @NonNull List<RecurringScheduleEntity> recurringSchedules, @NonNull List<DebtProfileEntity> debtProfiles, @NonNull List<ReceiptAttachmentEntity> receiptAttachments,
            @NonNull List<TransactionExchangeRateEntity> transactionExchangeRates) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.accountExchangeRates = Objects.requireNonNull(accountExchangeRates, "accountExchangeRates");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.transactionEntries = Objects.requireNonNull(transactionEntries, "transactionEntries");
        this.reconciliations = Objects.requireNonNull(reconciliations, "reconciliations");
        this.budgetAllocations = Objects.requireNonNull(budgetAllocations, "budgetAllocations");
        this.goals = Objects.requireNonNull(goals, "goals");
        this.goalContributions = Objects.requireNonNull(goalContributions, "goalContributions");
        this.recurringSchedules = Objects.requireNonNull(recurringSchedules, "recurringSchedules");
        this.debtProfiles = Objects.requireNonNull(debtProfiles, "debtProfiles");
        this.receiptAttachments = Objects.requireNonNull(receiptAttachments, "receiptAttachments");
        this.transactionExchangeRates = Objects.requireNonNull(transactionExchangeRates, "transactionExchangeRates");
    }

    @NonNull public List<AccountEntity> getAccounts() { return accounts; }
    @NonNull public List<AccountExchangeRateEntity> getAccountExchangeRates() { return accountExchangeRates; }
    @NonNull public List<CategoryEntity> getCategories() { return categories; }
    @NonNull public List<TransactionEntity> getTransactions() { return transactions; }
    @NonNull public List<TransactionEntryEntity> getTransactionEntries() { return transactionEntries; }
    @NonNull public List<ReconciliationEntity> getReconciliations() { return reconciliations; }
    @NonNull public List<BudgetAllocationEntity> getBudgetAllocations() { return budgetAllocations; }
    @NonNull public List<GoalEntity> getGoals() { return goals; }
    @NonNull public List<GoalContributionEntity> getGoalContributions() { return goalContributions; }
    @NonNull public List<RecurringScheduleEntity> getRecurringSchedules() { return recurringSchedules; }
    @NonNull public List<DebtProfileEntity> getDebtProfiles() { return debtProfiles; }
    @NonNull public List<ReceiptAttachmentEntity> getReceiptAttachments() { return receiptAttachments; }
    @NonNull public List<TransactionExchangeRateEntity> getTransactionExchangeRates() { return transactionExchangeRates; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DatabaseSnapshot)) return false;
        DatabaseSnapshot that = (DatabaseSnapshot) other;
        return Objects.equals(accounts, that.accounts)
                && Objects.equals(accountExchangeRates, that.accountExchangeRates)
                && Objects.equals(categories, that.categories)
                && Objects.equals(transactions, that.transactions)
                && Objects.equals(transactionEntries, that.transactionEntries)
                && Objects.equals(reconciliations, that.reconciliations)
                && Objects.equals(budgetAllocations, that.budgetAllocations)
                && Objects.equals(goals, that.goals)
                && Objects.equals(goalContributions, that.goalContributions)
                && Objects.equals(recurringSchedules, that.recurringSchedules)
                && Objects.equals(debtProfiles, that.debtProfiles)
                && Objects.equals(receiptAttachments, that.receiptAttachments)
                && Objects.equals(transactionExchangeRates, that.transactionExchangeRates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accounts, accountExchangeRates, categories, transactions, transactionEntries,
            reconciliations, budgetAllocations, goals, goalContributions, recurringSchedules,
            debtProfiles, receiptAttachments, transactionExchangeRates);
    }
}
