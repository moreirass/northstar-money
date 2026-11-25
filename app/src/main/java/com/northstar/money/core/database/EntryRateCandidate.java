package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

public final class EntryRateCandidate {
    @NonNull private final String transactionId;
    @NonNull private final String localDate;
    @NonNull private final String entryId;
    @NonNull private final String accountId;
    @Nullable private final String categoryId;
    private final long amountMinor;
    @NonNull private final String currencyCode;
    private final boolean cleared;

    public EntryRateCandidate(@NonNull String transactionId, @NonNull String localDate, @NonNull String entryId,
            @NonNull String accountId, @Nullable String categoryId, long amountMinor,
            @NonNull String currencyCode, boolean cleared) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.localDate = Objects.requireNonNull(localDate, "localDate");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = categoryId;
        this.amountMinor = amountMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.cleared = cleared;
    }

    @NonNull public String getTransactionId() { return transactionId; }
    @NonNull public String getLocalDate() { return localDate; }
    @NonNull public String getEntryId() { return entryId; }
    @NonNull public String getAccountId() { return accountId; }
    @Nullable public String getCategoryId() { return categoryId; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    public boolean getCleared() { return cleared; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntryRateCandidate)) return false;
        EntryRateCandidate that = (EntryRateCandidate) other;
        return Objects.equals(transactionId, that.transactionId)
                && Objects.equals(localDate, that.localDate)
                && Objects.equals(entryId, that.entryId)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(categoryId, that.categoryId)
                && amountMinor == that.amountMinor
                && Objects.equals(currencyCode, that.currencyCode)
                && cleared == that.cleared;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, localDate, entryId, accountId, categoryId,
            amountMinor, currencyCode, cleared);
    }
}
