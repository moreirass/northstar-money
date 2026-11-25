package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(
    tableName = "budget_allocations",
    foreignKeys = {@ForeignKey(
        entity = CategoryEntity.class,
        parentColumns = {"id"},
        childColumns = {"categoryId"},
        onDelete = ForeignKey.CASCADE)
    },
    indices = {@Index("categoryId"), @Index(value = {"monthStart", "categoryId", "currencyCode"}, unique = true)})
public final class BudgetAllocationEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String monthStart;
    @NonNull private final String categoryId;
    private final long plannedMinor;
    @ColumnInfo(defaultValue = "'EUR'") @NonNull private final String currencyCode;

    public BudgetAllocationEntity(@NonNull String id, @NonNull String monthStart, @NonNull String categoryId,
            long plannedMinor, @NonNull String currencyCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.monthStart = Objects.requireNonNull(monthStart, "monthStart");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.plannedMinor = plannedMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
    }

    @Ignore
    public BudgetAllocationEntity(@NonNull String id, @NonNull String monthStart, @NonNull String categoryId,
            long plannedMinor) {
        this(id, monthStart, categoryId, plannedMinor, "EUR");
    }

    @NonNull
    public BudgetAllocationEntity withCurrency(@NonNull String currencyCode) {
        return new BudgetAllocationEntity(id, monthStart, categoryId, plannedMinor, currencyCode);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getMonthStart() { return monthStart; }
    @NonNull public String getCategoryId() { return categoryId; }
    public long getPlannedMinor() { return plannedMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BudgetAllocationEntity)) return false;
        BudgetAllocationEntity that = (BudgetAllocationEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(monthStart, that.monthStart)
                && Objects.equals(categoryId, that.categoryId)
                && plannedMinor == that.plannedMinor
                && Objects.equals(currencyCode, that.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, monthStart, categoryId, plannedMinor, currencyCode);
    }
}
