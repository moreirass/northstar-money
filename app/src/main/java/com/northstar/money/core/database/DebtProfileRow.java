package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.Objects;

public final class DebtProfileRow {
    @NonNull private final String id;
    @NonNull private final String accountId;
    private final int annualRateBasisPoints;
    private final long minimumPaymentMinor;
    private final int dueDay;
    private final long createdAt;
    @NonNull private final String currencyCode;

    public DebtProfileRow(@NonNull String id, @NonNull String accountId, int annualRateBasisPoints,
            long minimumPaymentMinor, int dueDay, long createdAt,
            @NonNull String currencyCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.annualRateBasisPoints = annualRateBasisPoints;
        this.minimumPaymentMinor = minimumPaymentMinor;
        this.dueDay = dueDay;
        this.createdAt = createdAt;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    public int getAnnualRateBasisPoints() { return annualRateBasisPoints; }
    public long getMinimumPaymentMinor() { return minimumPaymentMinor; }
    public int getDueDay() { return dueDay; }
    public long getCreatedAt() { return createdAt; }
    @NonNull public String getCurrencyCode() { return currencyCode; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DebtProfileRow)) return false;
        DebtProfileRow that = (DebtProfileRow) other;
        return Objects.equals(id, that.id)
                && Objects.equals(accountId, that.accountId)
                && annualRateBasisPoints == that.annualRateBasisPoints
                && minimumPaymentMinor == that.minimumPaymentMinor
                && dueDay == that.dueDay
                && createdAt == that.createdAt
                && Objects.equals(currencyCode, that.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, annualRateBasisPoints, minimumPaymentMinor, dueDay,
            createdAt, currencyCode);
    }
}
