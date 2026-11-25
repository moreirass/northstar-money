package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(
    tableName = "debt_profiles",
    foreignKeys = {@ForeignKey(
        entity = AccountEntity.class,
        parentColumns = {"id"},
        childColumns = {"accountId"},
        onDelete = ForeignKey.CASCADE)
    },
    indices = {@Index(value = {"accountId"}, unique = true)})
public final class DebtProfileEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String accountId;
    private final int annualRateBasisPoints;
    private final long minimumPaymentMinor;
    private final int dueDay;
    private final long createdAt;

    public DebtProfileEntity(@NonNull String id, @NonNull String accountId, int annualRateBasisPoints,
            long minimumPaymentMinor, int dueDay, long createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.annualRateBasisPoints = annualRateBasisPoints;
        this.minimumPaymentMinor = minimumPaymentMinor;
        this.dueDay = dueDay;
        this.createdAt = createdAt;
    }

    @NonNull
    public DebtProfileEntity withTerms(int annualRateBasisPoints, long minimumPaymentMinor, int dueDay) {
        return new DebtProfileEntity(id, accountId, annualRateBasisPoints, minimumPaymentMinor, dueDay,
            createdAt);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    public int getAnnualRateBasisPoints() { return annualRateBasisPoints; }
    public long getMinimumPaymentMinor() { return minimumPaymentMinor; }
    public int getDueDay() { return dueDay; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebtProfileEntity)) return false;
        DebtProfileEntity that = (DebtProfileEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(accountId, that.accountId)
                && annualRateBasisPoints == that.annualRateBasisPoints
                && minimumPaymentMinor == that.minimumPaymentMinor
                && dueDay == that.dueDay
                && createdAt == that.createdAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, annualRateBasisPoints, minimumPaymentMinor, dueDay,
            createdAt);
    }
}
