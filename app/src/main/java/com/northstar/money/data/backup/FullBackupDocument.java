package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.northstar.money.core.database.AccountEntity;
import com.northstar.money.core.database.AccountExchangeRateEntity;
import com.northstar.money.core.database.BudgetAllocationEntity;
import com.northstar.money.core.database.CategoryEntity;
import com.northstar.money.core.database.DatabaseSnapshot;
import com.northstar.money.core.database.DebtProfileEntity;
import com.northstar.money.core.database.GoalContributionEntity;
import com.northstar.money.core.database.GoalEntity;
import com.northstar.money.core.database.ReceiptAttachmentEntity;
import com.northstar.money.core.database.ReconciliationEntity;
import com.northstar.money.core.database.RecurringScheduleEntity;
import com.northstar.money.core.database.TransactionEntity;
import com.northstar.money.core.database.TransactionEntryEntity;
import com.northstar.money.core.database.TransactionExchangeRateEntity;
import java.util.List;
import java.util.Objects;

public final class FullBackupDocument {
    @NonNull private final String format;
    private final int formatVersion;
    private final int databaseVersion;
    private final long createdAtEpochMillis;
    @NonNull private final List<AccountEntity> accounts;
    @NonNull private final List<AccountExchangeRateEntity> accountExchangeRates;
    @Nullable private final String baseCurrencyCode;
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

    public FullBackupDocument(@NonNull String format, int formatVersion, int databaseVersion,
            long createdAtEpochMillis, @NonNull List<AccountEntity> accounts, @NonNull List<AccountExchangeRateEntity> accountExchangeRates,
            @Nullable String baseCurrencyCode, @NonNull List<CategoryEntity> categories, @NonNull List<TransactionEntity> transactions,
            @NonNull List<TransactionEntryEntity> transactionEntries, @NonNull List<ReconciliationEntity> reconciliations, @NonNull List<BudgetAllocationEntity> budgetAllocations,
            @NonNull List<GoalEntity> goals, @NonNull List<GoalContributionEntity> goalContributions, @NonNull List<RecurringScheduleEntity> recurringSchedules,
            @NonNull List<DebtProfileEntity> debtProfiles, @NonNull List<ReceiptAttachmentEntity> receiptAttachments, @NonNull List<TransactionExchangeRateEntity> transactionExchangeRates) {
        this.format = Objects.requireNonNull(format, "format");
        this.formatVersion = formatVersion;
        this.databaseVersion = databaseVersion;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.accountExchangeRates = Objects.requireNonNull(accountExchangeRates, "accountExchangeRates");
        this.baseCurrencyCode = baseCurrencyCode;
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

    @NonNull
    public DatabaseSnapshot toSnapshot() {
        return new DatabaseSnapshot(accounts, accountExchangeRates, categories, transactions,
            transactionEntries, reconciliations, budgetAllocations, goals,
            goalContributions, recurringSchedules, debtProfiles, receiptAttachments,
            transactionExchangeRates);
    }

    @NonNull public String getFormat() { return format; }
    public int getFormatVersion() { return formatVersion; }
    public int getDatabaseVersion() { return databaseVersion; }
    public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
    @NonNull public List<AccountEntity> getAccounts() { return accounts; }
    @NonNull public List<AccountExchangeRateEntity> getAccountExchangeRates() { return accountExchangeRates; }
    @Nullable public String getBaseCurrencyCode() { return baseCurrencyCode; }
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
        if (!(other instanceof FullBackupDocument)) return false;
        FullBackupDocument that = (FullBackupDocument) other;
        return Objects.equals(format, that.format)
                && formatVersion == that.formatVersion
                && databaseVersion == that.databaseVersion
                && createdAtEpochMillis == that.createdAtEpochMillis
                && Objects.equals(accounts, that.accounts)
                && Objects.equals(accountExchangeRates, that.accountExchangeRates)
                && Objects.equals(baseCurrencyCode, that.baseCurrencyCode)
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
        return Objects.hash(format, formatVersion, databaseVersion, createdAtEpochMillis, accounts,
            accountExchangeRates, baseCurrencyCode, categories, transactions, transactionEntries,
            reconciliations, budgetAllocations, goals, goalContributions, recurringSchedules,
            debtProfiles, receiptAttachments, transactionExchangeRates);
    }
}
