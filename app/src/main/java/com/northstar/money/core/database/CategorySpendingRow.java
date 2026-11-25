package com.northstar.money.core.database;

import androidx.annotation.Nullable;
import java.util.Objects;

public final class CategorySpendingRow {
    @Nullable private final String categoryName;
    private final long amountMinor;

    public CategorySpendingRow(@Nullable String categoryName, long amountMinor) {
        this.categoryName = categoryName;
        this.amountMinor = amountMinor;
    }

    @Nullable public String getCategoryName() { return categoryName; }
    public long getAmountMinor() { return amountMinor; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CategorySpendingRow)) return false;
        CategorySpendingRow that = (CategorySpendingRow) other;
        return Objects.equals(categoryName, that.categoryName) && amountMinor == that.amountMinor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryName, amountMinor);
    }
}
