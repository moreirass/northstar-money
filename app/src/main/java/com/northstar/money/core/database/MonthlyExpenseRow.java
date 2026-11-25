package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.Objects;

public final class MonthlyExpenseRow {
    @NonNull private final String monthStart;
    private final long amountMinor;

    public MonthlyExpenseRow(@NonNull String monthStart, long amountMinor) {
        this.monthStart = Objects.requireNonNull(monthStart, "monthStart");
        this.amountMinor = amountMinor;
    }

    @NonNull public String getMonthStart() { return monthStart; }
    public long getAmountMinor() { return amountMinor; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MonthlyExpenseRow)) return false;
        MonthlyExpenseRow that = (MonthlyExpenseRow) other;
        return Objects.equals(monthStart, that.monthStart) && amountMinor == that.amountMinor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthStart, amountMinor);
    }
}
