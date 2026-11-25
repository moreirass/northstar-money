package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "goals", indices = {@Index("status")})
public final class GoalEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String name;
    private final long targetMinor;
    private final long savedMinor;
    @NonNull private final String currencyCode;
    @Nullable private final String targetLocalDate;
    @NonNull private final String status;
    private final long createdAt;

    public GoalEntity(@NonNull String id, @NonNull String name, long targetMinor,
            long savedMinor, @NonNull String currencyCode, @Nullable String targetLocalDate,
            @NonNull String status, long createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.targetMinor = targetMinor;
        this.savedMinor = savedMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.targetLocalDate = targetLocalDate;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = createdAt;
    }

    @NonNull
    public GoalEntity withDetails(@NonNull String name, long targetMinor, @Nullable String targetLocalDate,
            @NonNull String status) {
        return new GoalEntity(id, name, targetMinor, savedMinor, currencyCode,
            targetLocalDate, status, createdAt);
    }

    @NonNull
    public GoalEntity withSavedMinor(long savedMinor) {
        return new GoalEntity(id, name, targetMinor, savedMinor, currencyCode,
            targetLocalDate, status, createdAt);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    public long getTargetMinor() { return targetMinor; }
    public long getSavedMinor() { return savedMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    @Nullable public String getTargetLocalDate() { return targetLocalDate; }
    @NonNull public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GoalEntity)) return false;
        GoalEntity that = (GoalEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && targetMinor == that.targetMinor
                && savedMinor == that.savedMinor
                && Objects.equals(currencyCode, that.currencyCode)
                && Objects.equals(targetLocalDate, that.targetLocalDate)
                && Objects.equals(status, that.status)
                && createdAt == that.createdAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, targetMinor, savedMinor, currencyCode,
            targetLocalDate, status, createdAt);
    }
}
