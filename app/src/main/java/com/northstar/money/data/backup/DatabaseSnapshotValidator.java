package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
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
import com.northstar.money.domain.model.CurrencyMath;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class DatabaseSnapshotValidator {
    private static final List<String> ACCOUNT_TYPES = Arrays.asList("CASH", "CHECKING", "SAVINGS", "CREDIT");
    private static final List<String> CATEGORY_KINDS = Arrays.asList("INCOME", "EXPENSE");
    private static final List<String> TRANSACTION_KINDS = Arrays.asList("INCOME", "EXPENSE", "TRANSFER");
    private static final List<String> FREQUENCIES = Arrays.asList("WEEKLY", "MONTHLY", "YEARLY");
    private static final List<String> GOAL_STATUSES = Arrays.asList("ACTIVE", "PAUSED", "COMPLETED");
    private static final List<String> OCR_STATUSES = Arrays.asList("PENDING", "PROCESSING", "COMPLETE", "FAILED");
    private static final List<String> RATE_STATUSES = Arrays.asList("PENDING", "AVAILABLE", "FAILED");

    public void validate(@NonNull DatabaseSnapshot snapshot) {
        int recordCount = snapshot.getAccounts().size() + snapshot.getCategories().size()
                + snapshot.getTransactions().size() + snapshot.getTransactionEntries().size()
                + snapshot.getReceiptAttachments().size() + snapshot.getTransactionExchangeRates().size()
                + snapshot.getGoals().size() + snapshot.getGoalContributions().size()
                + snapshot.getRecurringSchedules().size() + snapshot.getBudgetAllocations().size()
                + snapshot.getReconciliations().size() + snapshot.getDebtProfiles().size();
        require(recordCount <= 500_000, "Backup contains too many records");
        long receiptBytes = 0;
        for (ReceiptAttachmentEntity receipt : snapshot.getReceiptAttachments()) {
            receiptBytes += receipt.getByteSize();
        }
        require(receiptBytes <= 32L * 1024 * 1024, "Backup receipts are too large");

        Map<String, AccountEntity> accounts = index("accounts", snapshot.getAccounts(), AccountEntity::getId);
        Map<String, CategoryEntity> categories = index("categories", snapshot.getCategories(), CategoryEntity::getId);
        Map<String, TransactionEntity> transactions = index("transactions", snapshot.getTransactions(), TransactionEntity::getId);
        Map<String, TransactionEntryEntity> entries = index("transaction entries", snapshot.getTransactionEntries(),
                TransactionEntryEntity::getId);
        index("reconciliations", snapshot.getReconciliations(), ReconciliationEntity::getId);
        index("budget allocations", snapshot.getBudgetAllocations(), BudgetAllocationEntity::getId);
        Map<String, GoalEntity> goals = index("goals", snapshot.getGoals(), GoalEntity::getId);
        index("goal contributions", snapshot.getGoalContributions(), GoalContributionEntity::getId);
        index("recurring schedules", snapshot.getRecurringSchedules(), RecurringScheduleEntity::getId);
        index("debt profiles", snapshot.getDebtProfiles(), DebtProfileEntity::getId);
        index("receipt attachments", snapshot.getReceiptAttachments(), ReceiptAttachmentEntity::getId);
        index("transaction exchange rates", snapshot.getTransactionExchangeRates(), TransactionExchangeRateEntity::getId);

        requireUnique(snapshot.getCategories(), row -> Arrays.asList(row.getKind(), row.getName()),
                "Backup contains duplicate category names for the same type");
        requireUnique(snapshot.getBudgetAllocations(), row -> Arrays.asList(row.getMonthStart(), row.getCategoryId(), row.getCurrencyCode()),
                "Backup contains duplicate budget allocations");
        requireUnique(snapshot.getDebtProfiles(), DebtProfileEntity::getAccountId,
                "Backup contains more than one debt profile for an account");

        for (AccountEntity account : snapshot.getAccounts()) {
            require(!isBlank(account.getName()) && ACCOUNT_TYPES.contains(account.getType())
                    && CurrencyMath.validCurrency(account.getCurrencyCode()), "Backup contains an invalid account");
        }
        for (CategoryEntity category : snapshot.getCategories()) {
            require(!isBlank(category.getName()) && CATEGORY_KINDS.contains(category.getKind())
                    && category.getSortOrder() >= 0, "Backup contains an invalid category");
            String targetId = category.getMergedIntoCategoryId();
            if (targetId != null) {
                CategoryEntity target = categories.get(targetId);
                require(category.getArchivedAt() != null && !targetId.equals(category.getId()) && target != null
                        && target.getKind().equals(category.getKind()) && target.getArchivedAt() == null
                        && target.getMergedIntoCategoryId() == null, "Backup contains an invalid category merge");
            }
        }

        Map<String, List<TransactionEntryEntity>> entriesByTransaction = validateTransactions(
                snapshot, accounts, categories, transactions);
        validateReconciliations(snapshot.getReconciliations(), accounts, transactions, entriesByTransaction);
        validatePlanning(snapshot, accounts, categories, goals);
        validateReceipts(snapshot.getReceiptAttachments(), transactions);
        validateRates(snapshot, accounts, transactions, entries);
    }

    private static Map<String, List<TransactionEntryEntity>> validateTransactions(DatabaseSnapshot snapshot,
            Map<String, AccountEntity> accounts, Map<String, CategoryEntity> categories,
            Map<String, TransactionEntity> transactions) {
        for (TransactionEntity transaction : snapshot.getTransactions()) {
            Long deletedAt = transaction.getDeletedAt();
            require(TRANSACTION_KINDS.contains(transaction.getKind()), "Backup contains an invalid transaction type");
            require(deletedAt == null || deletedAt >= 0, "Backup contains an invalid deletion timestamp");
            require(transaction.getPayee().length() <= 4096 && transaction.getNote().length() <= 4096,
                    "Backup transaction text is too long");
            requireDate(transaction.getLocalDate(), "transaction");
        }

        Map<String, List<TransactionEntryEntity>> entriesByTransaction = new HashMap<>();
        for (TransactionEntryEntity entry : snapshot.getTransactionEntries()) {
            TransactionEntity transaction = transactions.get(entry.getTransactionId());
            AccountEntity account = accounts.get(entry.getAccountId());
            require(transaction != null && account != null, "Backup contains a transaction entry with a missing parent");
            String categoryId = entry.getCategoryId();
            if (categoryId != null) {
                CategoryEntity category = categories.get(categoryId);
                require(category != null, "Backup contains a transaction entry with a missing category");
                require(category.getKind().equals(transaction.getKind()), "Backup transaction category does not match its type");
            }
            require(CurrencyMath.validCurrency(entry.getCurrencyCode()), "Backup contains an invalid transaction currency");
            require(entry.getCurrencyCode().equals(account.getCurrencyCode()),
                    "Backup contains a transaction currency that does not match its account");
            entriesByTransaction.computeIfAbsent(entry.getTransactionId(), id -> new ArrayList<>()).add(entry);
        }

        require(entriesByTransaction.keySet().containsAll(transactions.keySet()),
                "Backup contains a transaction without entries");
        for (TransactionEntity transaction : snapshot.getTransactions()) {
            List<TransactionEntryEntity> entries = entriesByTransaction.get(transaction.getId());
            long firstAmount = entries.get(0).getAmountMinor();
            switch (transaction.getKind()) {
                case "INCOME":
                    require(entries.size() == 1 && firstAmount > 0, "Backup contains invalid income entries");
                    break;
                case "EXPENSE":
                    require(entries.size() == 1 && firstAmount < 0 && firstAmount != Long.MIN_VALUE,
                            "Backup contains invalid expense entries");
                    break;
                case "TRANSFER":
                    validateTransfer(entries);
                    break;
            }
        }
        return entriesByTransaction;
    }

    private static void validateTransfer(List<TransactionEntryEntity> entries) {
        require(entries.size() == 2, "Backup contains an invalid transfer");
        TransactionEntryEntity first = entries.get(0);
        TransactionEntryEntity second = entries.get(1);
        TransactionEntryEntity source = first.getAmountMinor() < 0 ? first : second;
        TransactionEntryEntity destination = source == first ? second : first;
        require(source.getCategoryId() == null && destination.getCategoryId() == null
                && source.getAmountMinor() < 0 && destination.getAmountMinor() > 0,
                "Backup contains an invalid transfer");
        require(!source.getAccountId().equals(destination.getAccountId()) && source.getAmountMinor() != Long.MIN_VALUE,
                "Backup transfer accounts must differ");
        if (source.getCurrencyCode().equals(destination.getCurrencyCode())) {
            require(Math.addExact(source.getAmountMinor(), destination.getAmountMinor()) == 0,
                    "Backup contains an unbalanced transfer");
        }
    }

    private static void validateReconciliations(List<ReconciliationEntity> reconciliations,
            Map<String, AccountEntity> accounts, Map<String, TransactionEntity> transactions,
            Map<String, List<TransactionEntryEntity>> entriesByTransaction) {
        for (ReconciliationEntity row : reconciliations) {
            String adjustmentId = row.getAdjustmentTransactionId();
            require(accounts.containsKey(row.getAccountId()) && (adjustmentId == null || transactions.containsKey(adjustmentId)),
                    "Backup contains an invalid reconciliation reference");
            requireDate(row.getStatementLocalDate(), "reconciliation");
            require(Math.subtractExact(row.getStatementBalanceMinor(), row.getCalculatedBalanceMinor()) == row.getDifferenceMinor(),
                    "Backup contains inconsistent reconciliation amounts");
            if (adjustmentId != null) {
                TransactionEntity adjustment = transactions.get(adjustmentId);
                List<TransactionEntryEntity> entries = entriesByTransaction.get(adjustmentId);
                TransactionEntryEntity entry = entries.get(0);
                require(adjustment.getDeletedAt() == null && adjustment.getLocalDate().equals(row.getStatementLocalDate())
                        && entries.size() == 1 && entry.getAccountId().equals(row.getAccountId())
                        && entry.getAmountMinor() == row.getDifferenceMinor() && entry.getCleared(),
                        "Backup contains an invalid reconciliation adjustment");
            }
        }
    }

    private static void validatePlanning(DatabaseSnapshot snapshot, Map<String, AccountEntity> accounts,
            Map<String, CategoryEntity> categories, Map<String, GoalEntity> goals) {
        for (BudgetAllocationEntity row : snapshot.getBudgetAllocations()) {
            CategoryEntity category = categories.get(row.getCategoryId());
            require(category != null && "EXPENSE".equals(category.getKind()) && row.getPlannedMinor() >= 0
                    && CurrencyMath.validCurrency(row.getCurrencyCode()), "Backup contains an invalid budget allocation");
            require(requireDate(row.getMonthStart(), "budget allocation").getDayOfMonth() == 1,
                    "Backup contains a budget outside a month boundary");
        }
        for (GoalEntity row : snapshot.getGoals()) {
            require(!isBlank(row.getName()) && row.getTargetMinor() > 0 && row.getSavedMinor() == 0
                    && CurrencyMath.validCurrency(row.getCurrencyCode()) && GOAL_STATUSES.contains(row.getStatus()),
                    "Backup contains an invalid goal");
            if (row.getTargetLocalDate() != null) requireDate(row.getTargetLocalDate(), "goal");
        }
        for (GoalContributionEntity row : snapshot.getGoalContributions()) {
            Long deletedAt = row.getDeletedAt();
            require(goals.containsKey(row.getGoalId()) && row.getAmountMinor() > 0 && row.getNote().length() <= 500
                    && (deletedAt == null || deletedAt >= 0), "Backup contains an invalid goal contribution");
            requireDate(row.getLocalDate(), "goal contribution");
        }
        for (RecurringScheduleEntity row : snapshot.getRecurringSchedules()) {
            AccountEntity account = accounts.get(row.getAccountId());
            CategoryEntity category = categories.get(row.getCategoryId());
            Long deletedAt = row.getDeletedAt();
            require(!isBlank(row.getName()) && CATEGORY_KINDS.contains(row.getKind()) && row.getAmountMinor() > 0
                    && CurrencyMath.validCurrency(row.getCurrencyCode()) && account != null
                    && row.getCurrencyCode().equals(account.getCurrencyCode()) && FREQUENCIES.contains(row.getFrequency())
                    && row.getIntervalCount() >= 1 && row.getIntervalCount() <= 1000
                    && row.getAnchorDay() >= 1 && row.getAnchorDay() <= 31
                    && row.getAnchorMonth() >= 1 && row.getAnchorMonth() <= 12
                    && category != null && category.getKind().equals(row.getKind()), "Backup contains an invalid recurring schedule");
            require(deletedAt == null || deletedAt >= 0, "Backup contains an invalid recurring deletion timestamp");
            requireDate(row.getNextLocalDate(), "recurring schedule");
        }
        for (DebtProfileEntity row : snapshot.getDebtProfiles()) {
            require(accounts.containsKey(row.getAccountId()) && row.getAnnualRateBasisPoints() >= 0
                    && row.getMinimumPaymentMinor() >= 0 && row.getDueDay() >= 1 && row.getDueDay() <= 31,
                    "Backup contains an invalid debt profile");
        }
    }

    private static void validateReceipts(List<ReceiptAttachmentEntity> receipts, Map<String, TransactionEntity> transactions) {
        for (ReceiptAttachmentEntity row : receipts) {
            require(transactions.containsKey(row.getTransactionId()) && !isBlank(row.getOriginalName())
                    && row.getMimeType().startsWith("image/") && row.getContent().length > 0
                    && row.getContent().length == row.getByteSize() && row.getByteSize() <= 8L * 1024 * 1024
                    && OCR_STATUSES.contains(row.getOcrStatus()), "Backup contains an invalid receipt attachment");
            require(row.getByteSize() <= 1_000_000, "Backup receipt must be normalized");
            require(row.getDetectedAmountMinor() == null || row.getDetectedAmountMinor() > 0, "Invalid detected receipt amount");
            require(row.getDetectedCurrencyCode() == null || CurrencyMath.validCurrency(row.getDetectedCurrencyCode()),
                    "Invalid detected receipt currency");
            if (row.getDetectedLocalDate() != null) requireDate(row.getDetectedLocalDate(), "receipt");
        }
    }

    private static void validateRates(DatabaseSnapshot snapshot, Map<String, AccountEntity> accounts,
            Map<String, TransactionEntity> transactions, Map<String, TransactionEntryEntity> entries) {
        requireUnique(snapshot.getTransactionExchangeRates(), TransactionExchangeRateEntity::getEntryId,
                "Backup contains duplicate exchange rates for an entry");
        for (TransactionExchangeRateEntity row : snapshot.getTransactionExchangeRates()) {
            Long rateMicros = row.getRateMicros();
            Long converted = row.getConvertedAmountMinor();
            boolean available = "AVAILABLE".equals(row.getStatus());
            TransactionEntity transaction = transactions.get(row.getTransactionId());
            TransactionEntryEntity entry = entries.get(row.getEntryId());
            require(transaction != null && entry != null && CurrencyMath.validCurrency(row.getBaseCurrencyCode())
                    && CurrencyMath.validCurrency(row.getQuoteCurrencyCode()) && RATE_STATUSES.contains(row.getStatus())
                    && (rateMicros == null || rateMicros > 0) && (!available || (rateMicros != null && converted != null)),
                    "Backup contains an invalid historical exchange rate");
            require(entry.getTransactionId().equals(row.getTransactionId()) && entry.getCurrencyCode().equals(row.getBaseCurrencyCode()),
                    "Exchange rate does not match its entry");
            require(row.getRateLocalDate().equals(transaction.getLocalDate()), "Exchange rate date does not match its transaction");
            if (available) {
                require(converted == CurrencyMath.convertCurrencyMinor(entry.getAmountMinor(),
                        row.getBaseCurrencyCode(), row.getQuoteCurrencyCode(), rateMicros), "Exchange conversion is inconsistent");
            }
            requireDate(row.getRateLocalDate(), "exchange rate");
        }
        requireUnique(snapshot.getAccountExchangeRates(), AccountExchangeRateEntity::getAccountId,
                "Duplicate opening balance rates");
        for (AccountExchangeRateEntity row : snapshot.getAccountExchangeRates()) {
            AccountEntity account = accounts.get(row.getAccountId());
            require(account != null, "Missing account for opening rate");
            require(row.getBaseCurrencyCode().equals(account.getCurrencyCode())
                    && CurrencyMath.validCurrency(row.getQuoteCurrencyCode()) && row.getRateMicros() > 0, "Failed requirement.");
            require(row.getOpeningBalanceMinor() == account.getOpeningBalanceMinor()
                    && row.getConvertedAmountMinor() == CurrencyMath.convertCurrencyMinor(account.getOpeningBalanceMinor(),
                    account.getCurrencyCode(), row.getQuoteCurrencyCode(), row.getRateMicros()), "Invalid opening conversion");
            requireDate(row.getRateLocalDate(), "opening rate");
        }
    }

    private static <T> Map<String, T> index(String label, List<T> rows, Function<T, String> idOf) {
        Map<String, T> result = new HashMap<>();
        for (T row : rows) {
            String id = idOf.apply(row);
            if (isBlank(id) || result.put(id, row) != null) {
                throw new IllegalArgumentException("Backup contains blank or duplicate " + label + " identifiers");
            }
        }
        return result;
    }

    private static <T> void requireUnique(List<T> rows, Function<T, ?> keyOf, String message) {
        Set<Object> seen = new HashSet<>();
        for (T row : rows) require(seen.add(keyOf.apply(row)), message);
    }

    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isWhitespace(ch) && !Character.isSpaceChar(ch)) return false;
        }
        return true;
    }

    private static LocalDate requireDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Backup contains an invalid " + label + " date", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
