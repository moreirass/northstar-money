package com.northstar.money.core.database;

import java.util.Objects;

public final class SummaryRow {
    private final int missingConversions;
    private final long balanceMinor;
    private final long incomeMinor;
    private final long expenseMinor;

    public SummaryRow(int missingConversions, long balanceMinor, long incomeMinor,
            long expenseMinor) {
        this.missingConversions = missingConversions;
        this.balanceMinor = balanceMinor;
        this.incomeMinor = incomeMinor;
        this.expenseMinor = expenseMinor;
    }

    public int getMissingConversions() { return missingConversions; }
    public long getBalanceMinor() { return balanceMinor; }
    public long getIncomeMinor() { return incomeMinor; }
    public long getExpenseMinor() { return expenseMinor; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SummaryRow)) return false;
        SummaryRow that = (SummaryRow) other;
        return missingConversions == that.missingConversions
                && balanceMinor == that.balanceMinor
                && incomeMinor == that.incomeMinor
                && expenseMinor == that.expenseMinor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(missingConversions, balanceMinor, incomeMinor, expenseMinor);
    }
}
