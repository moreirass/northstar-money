package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.LocalDate;
import java.util.Objects;

@Entity(
    tableName = "recurring_schedules",
    foreignKeys = {
        @ForeignKey(entity = AccountEntity.class, parentColumns = {"id"}, childColumns = {"accountId"}, onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = CategoryEntity.class, parentColumns = {"id"}, childColumns = {"categoryId"}, onDelete = ForeignKey.SET_NULL)
    },
    indices = {@Index("accountId"), @Index("categoryId"), @Index("nextLocalDate"), @Index("deletedAt")})
public final class RecurringScheduleEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String kind;
    private final long amountMinor;
    @NonNull private final String currencyCode;
    @NonNull private final String accountId;
    @Nullable private final String categoryId;
    @NonNull private final String frequency;
    private final int intervalCount;
    @NonNull private final String nextLocalDate;
    private final boolean active;
    private final long createdAt;
    @Nullable private final Long deletedAt;
    @ColumnInfo(defaultValue = "1") private final int anchorDay;
    @ColumnInfo(defaultValue = "1") private final int anchorMonth;

    public RecurringScheduleEntity(@NonNull String id, @NonNull String name, @NonNull String kind,
            long amountMinor, @NonNull String currencyCode, @NonNull String accountId,
            @Nullable String categoryId, @NonNull String frequency, int intervalCount,
            @NonNull String nextLocalDate, boolean active, long createdAt,
            @Nullable Long deletedAt, int anchorDay, int anchorMonth) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.amountMinor = amountMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.categoryId = categoryId;
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        this.intervalCount = intervalCount;
        this.nextLocalDate = Objects.requireNonNull(nextLocalDate, "nextLocalDate");
        this.active = active;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.anchorDay = anchorDay;
        this.anchorMonth = anchorMonth;
    }

    @Ignore
    public RecurringScheduleEntity(@NonNull String id, @NonNull String name, @NonNull String kind,
            long amountMinor, @NonNull String currencyCode, @NonNull String accountId,
            @Nullable String categoryId, @NonNull String frequency, int intervalCount,
            @NonNull String nextLocalDate, boolean active, long createdAt) {
        this(id, name, kind, amountMinor, currencyCode,
            accountId, categoryId, frequency, intervalCount, nextLocalDate,
            active, createdAt, null);
    }

    @Ignore
    public RecurringScheduleEntity(@NonNull String id, @NonNull String name, @NonNull String kind,
            long amountMinor, @NonNull String currencyCode, @NonNull String accountId,
            @Nullable String categoryId, @NonNull String frequency, int intervalCount,
            @NonNull String nextLocalDate, boolean active, long createdAt,
            @Nullable Long deletedAt) {
        this(id, name, kind, amountMinor, currencyCode,
            accountId, categoryId, frequency, intervalCount, nextLocalDate,
            active, createdAt, deletedAt, LocalDate.parse(nextLocalDate).getDayOfMonth(), LocalDate.parse(nextLocalDate).getMonthValue());
    }

    @NonNull
    public RecurringScheduleEntity withDetails(@NonNull String name, @NonNull String kind, long amountMinor,
            @NonNull String accountId, @Nullable String categoryId, @NonNull String frequency,
            int intervalCount, @NonNull String nextLocalDate, int anchorDay,
            int anchorMonth) {
        return new RecurringScheduleEntity(id, name, kind, amountMinor, currencyCode,
            accountId, categoryId, frequency, intervalCount, nextLocalDate,
            active, createdAt, deletedAt, anchorDay, anchorMonth);
    }

    @NonNull
    public RecurringScheduleEntity withCategory(@Nullable String categoryId) {
        return new RecurringScheduleEntity(id, name, kind, amountMinor, currencyCode,
            accountId, categoryId, frequency, intervalCount, nextLocalDate,
            active, createdAt, deletedAt, anchorDay, anchorMonth);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getKind() { return kind; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    @NonNull public String getAccountId() { return accountId; }
    @Nullable public String getCategoryId() { return categoryId; }
    @NonNull public String getFrequency() { return frequency; }
    public int getIntervalCount() { return intervalCount; }
    @NonNull public String getNextLocalDate() { return nextLocalDate; }
    public boolean getActive() { return active; }
    public long getCreatedAt() { return createdAt; }
    @Nullable public Long getDeletedAt() { return deletedAt; }
    public int getAnchorDay() { return anchorDay; }
    public int getAnchorMonth() { return anchorMonth; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecurringScheduleEntity)) return false;
        RecurringScheduleEntity that = (RecurringScheduleEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(kind, that.kind)
                && amountMinor == that.amountMinor
                && Objects.equals(currencyCode, that.currencyCode)
                && Objects.equals(accountId, that.accountId)
                && Objects.equals(categoryId, that.categoryId)
                && Objects.equals(frequency, that.frequency)
                && intervalCount == that.intervalCount
                && Objects.equals(nextLocalDate, that.nextLocalDate)
                && active == that.active
                && createdAt == that.createdAt
                && Objects.equals(deletedAt, that.deletedAt)
                && anchorDay == that.anchorDay
                && anchorMonth == that.anchorMonth;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, kind, amountMinor, currencyCode,
            accountId, categoryId, frequency, intervalCount, nextLocalDate,
            active, createdAt, deletedAt, anchorDay, anchorMonth);
    }
}
