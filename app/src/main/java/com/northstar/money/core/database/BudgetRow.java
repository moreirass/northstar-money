package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.Objects;

public final class BudgetRow {
    private final int missingConversions;
    @NonNull private final String categoryId;
    @NonNull private final String categoryName;
    private final long allocatedMinor;
    private final long rolloverMinor;
    private final long plannedMinor;
    private final long spentMinor;

    public BudgetRow(int missingConversions, @NonNull String categoryId, @NonNull String categoryName,
            long allocatedMinor, long rolloverMinor, long plannedMinor,
            long spentMinor) {
        this.missingConversions = missingConversions;
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.categoryName = Objects.requireNonNull(categoryName, "categoryName");
        this.allocatedMinor = allocatedMinor;
        this.rolloverMinor = rolloverMinor;
        this.plannedMinor = plannedMinor;
        this.spentMinor = spentMinor;
    }

    public int getMissingConversions() { return missingConversions; }
    @NonNull public String getCategoryId() { return categoryId; }
    @NonNull public String getCategoryName() { return categoryName; }
    public long getAllocatedMinor() { return allocatedMinor; }
    public long getRolloverMinor() { return rolloverMinor; }
    public long getPlannedMinor() { return plannedMinor; }
    public long getSpentMinor() { return spentMinor; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BudgetRow)) return false;
        BudgetRow that = (BudgetRow) other;
        return missingConversions == that.missingConversions
                && Objects.equals(categoryId, that.categoryId)
                && Objects.equals(categoryName, that.categoryName)
                && allocatedMinor == that.allocatedMinor
                && rolloverMinor == that.rolloverMinor
                && plannedMinor == that.plannedMinor
                && spentMinor == that.spentMinor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(missingConversions, categoryId, categoryName, allocatedMinor, rolloverMinor,
            plannedMinor, spentMinor);
    }
}
