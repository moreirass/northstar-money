package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(
    tableName = "reconciliations",
    foreignKeys = {
        @ForeignKey(
            entity = AccountEntity.class,
            parentColumns = {"id"},
            childColumns = {"accountId"},
            onDelete = ForeignKey.CASCADE),
        @ForeignKey(
            entity = TransactionEntity.class,
            parentColumns = {"id"},
            childColumns = {"adjustmentTransactionId"},
            onDelete = ForeignKey.SET_NULL)
    },
    indices = {@Index("accountId"), @Index("adjustmentTransactionId")})
public final class ReconciliationEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String accountId;
    @NonNull private final String statementLocalDate;
    private final long statementBalanceMinor;
    private final long calculatedBalanceMinor;
    private final long differenceMinor;
    @Nullable private final String adjustmentTransactionId;
    private final long completedAt;

    public ReconciliationEntity(@NonNull String id, @NonNull String accountId, @NonNull String statementLocalDate,
            long statementBalanceMinor, long calculatedBalanceMinor, long differenceMinor,
            @Nullable String adjustmentTransactionId, long completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.statementLocalDate = Objects.requireNonNull(statementLocalDate, "statementLocalDate");
        this.statementBalanceMinor = statementBalanceMinor;
        this.calculatedBalanceMinor = calculatedBalanceMinor;
        this.differenceMinor = differenceMinor;
        this.adjustmentTransactionId = adjustmentTransactionId;
        this.completedAt = completedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getStatementLocalDate() { return statementLocalDate; }
    public long getStatementBalanceMinor() { return statementBalanceMinor; }
    public long getCalculatedBalanceMinor() { return calculatedBalanceMinor; }
    public long getDifferenceMinor() { return differenceMinor; }
    @Nullable public String getAdjustmentTransactionId() { return adjustmentTransactionId; }
    public long getCompletedAt() { return completedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReconciliationEntity)) return false;
        ReconciliationEntity that = (ReconciliationEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(statementLocalDate, that.statementLocalDate)
                && statementBalanceMinor == that.statementBalanceMinor
                && calculatedBalanceMinor == that.calculatedBalanceMinor
                && differenceMinor == that.differenceMinor
                && Objects.equals(adjustmentTransactionId, that.adjustmentTransactionId)
                && completedAt == that.completedAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, statementLocalDate, statementBalanceMinor, calculatedBalanceMinor,
            differenceMinor, adjustmentTransactionId, completedAt);
    }
}
