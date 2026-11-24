package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "transaction_entries", foreignKeys = {
        @ForeignKey(entity = TransactionEntity.class, parentColumns = "id", childColumns = "transactionId",
                onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = AccountEntity.class, parentColumns = "id", childColumns = "accountId",
                onDelete = ForeignKey.RESTRICT),
        @ForeignKey(entity = CategoryEntity.class, parentColumns = "id", childColumns = "categoryId",
                onDelete = ForeignKey.SET_NULL)
}, indices = {@Index("transactionId"), @Index("accountId"), @Index("categoryId")})
public final class TransactionEntryEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String transactionId;
    @NonNull private final String accountId;
    @Nullable private final String categoryId;
    private final long amountMinor;
    @NonNull private final String currencyCode;
    private final boolean cleared;

    public TransactionEntryEntity(@NonNull String id, @NonNull String transactionId, @NonNull String accountId,
            @Nullable String categoryId, long amountMinor, @NonNull String currencyCode,
            boolean cleared) {
        this.id = Objects.requireNonNull(id, "id");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = categoryId;
        this.amountMinor = amountMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.cleared = cleared;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getTransactionId() { return transactionId; }
    @NonNull public String getAccountId() { return accountId; }
    @Nullable public String getCategoryId() { return categoryId; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    public boolean getCleared() { return cleared; }

    @NonNull
    public TransactionEntryEntity withDetails(@NonNull String accountId, @Nullable String categoryId,
            long amountMinor, @NonNull String currencyCode) {
        return new TransactionEntryEntity(id, transactionId, accountId, categoryId, amountMinor, currencyCode, cleared);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionEntryEntity)) return false;
        TransactionEntryEntity that = (TransactionEntryEntity) other;
        return id.equals(that.id)
                && transactionId.equals(that.transactionId)
                && accountId.equals(that.accountId)
                && Objects.equals(categoryId, that.categoryId)
                && amountMinor == that.amountMinor
                && currencyCode.equals(that.currencyCode)
                && cleared == that.cleared;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + transactionId.hashCode();
        result = 31 * result + accountId.hashCode();
        result = 31 * result + Objects.hashCode(categoryId);
        result = 31 * result + Long.hashCode(amountMinor);
        result = 31 * result + currencyCode.hashCode();
        result = 31 * result + Boolean.hashCode(cleared);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "TransactionEntryEntity(id=" + id
                + ", transactionId=" + transactionId + ", accountId=" + accountId
                + ", categoryId=" + categoryId + ", amountMinor=" + amountMinor
                + ", currencyCode=" + currencyCode + ", cleared=" + cleared + ")";
    }
}
