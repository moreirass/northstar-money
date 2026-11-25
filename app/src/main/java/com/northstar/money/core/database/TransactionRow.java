package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

public final class TransactionRow {
    @NonNull private final String accountId;
    @Nullable private final String destinationAccountId;
    @Nullable private final String categoryId;
    @NonNull private final String id;
    @NonNull private final String payee;
    @NonNull private final String kind;
    @NonNull private final String localDate;
    private final long createdAt;
    private final long amountMinor;
    @NonNull private final String currencyCode;
    @NonNull private final String accountName;
    @Nullable private final String categoryName;
    private final boolean cleared;

    public TransactionRow(@NonNull String accountId, @Nullable String destinationAccountId, @Nullable String categoryId,
            @NonNull String id, @NonNull String payee, @NonNull String kind,
            @NonNull String localDate, long createdAt, long amountMinor,
            @NonNull String currencyCode, @NonNull String accountName, @Nullable String categoryName,
            boolean cleared) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.destinationAccountId = destinationAccountId;
        this.categoryId = categoryId;
        this.id = Objects.requireNonNull(id, "id");
        this.payee = Objects.requireNonNull(payee, "payee");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.localDate = Objects.requireNonNull(localDate, "localDate");
        this.createdAt = createdAt;
        this.amountMinor = amountMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.accountName = Objects.requireNonNull(accountName, "accountName");
        this.categoryName = categoryName;
        this.cleared = cleared;
    }

    @NonNull public String getAccountId() { return accountId; }
    @Nullable public String getDestinationAccountId() { return destinationAccountId; }
    @Nullable public String getCategoryId() { return categoryId; }
    @NonNull public String getId() { return id; }
    @NonNull public String getPayee() { return payee; }
    @NonNull public String getKind() { return kind; }
    @NonNull public String getLocalDate() { return localDate; }
    public long getCreatedAt() { return createdAt; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    @NonNull public String getAccountName() { return accountName; }
    @Nullable public String getCategoryName() { return categoryName; }
    public boolean getCleared() { return cleared; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionRow)) return false;
        TransactionRow that = (TransactionRow) other;
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(destinationAccountId, that.destinationAccountId)
                && Objects.equals(categoryId, that.categoryId)
                && Objects.equals(id, that.id)
                && Objects.equals(payee, that.payee)
                && Objects.equals(kind, that.kind)
                && Objects.equals(localDate, that.localDate)
                && createdAt == that.createdAt
                && amountMinor == that.amountMinor
                && Objects.equals(currencyCode, that.currencyCode)
                && Objects.equals(accountName, that.accountName)
                && Objects.equals(categoryName, that.categoryName)
                && cleared == that.cleared;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, destinationAccountId, categoryId, id, payee,
            kind, localDate, createdAt, amountMinor, currencyCode,
            accountName, categoryName, cleared);
    }
}
