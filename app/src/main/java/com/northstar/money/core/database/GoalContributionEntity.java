package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(
    tableName = "goal_contributions",
    foreignKeys = {@ForeignKey(
        entity = GoalEntity.class,
        parentColumns = {"id"},
        childColumns = {"goalId"},
        onDelete = ForeignKey.CASCADE)
    },
    indices = {@Index("goalId"), @Index("localDate"), @Index("deletedAt")})
public final class GoalContributionEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String goalId;
    private final long amountMinor;
    @NonNull private final String localDate;
    @NonNull private final String note;
    private final long createdAt;
    private final long updatedAt;
    @Nullable private final Long deletedAt;

    public GoalContributionEntity(@NonNull String id, @NonNull String goalId, long amountMinor,
            @NonNull String localDate, @NonNull String note, long createdAt,
            long updatedAt, @Nullable Long deletedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.amountMinor = amountMinor;
        this.localDate = Objects.requireNonNull(localDate, "localDate");
        this.note = Objects.requireNonNull(note, "note");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    @Ignore
    public GoalContributionEntity(@NonNull String id, @NonNull String goalId, long amountMinor,
            @NonNull String localDate, @NonNull String note, long createdAt,
            long updatedAt) {
        this(id, goalId, amountMinor, localDate, note,
            createdAt, updatedAt, null);
    }

    @NonNull
    public GoalContributionEntity withDetails(@NonNull String goalId, long amountMinor, @NonNull String localDate,
            @NonNull String note, long updatedAt) {
        return new GoalContributionEntity(id, goalId, amountMinor, localDate, note,
            createdAt, updatedAt, deletedAt);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getGoalId() { return goalId; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getLocalDate() { return localDate; }
    @NonNull public String getNote() { return note; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    @Nullable public Long getDeletedAt() { return deletedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GoalContributionEntity)) return false;
        GoalContributionEntity that = (GoalContributionEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(goalId, that.goalId)
                && amountMinor == that.amountMinor
                && Objects.equals(localDate, that.localDate)
                && Objects.equals(note, that.note)
                && createdAt == that.createdAt
                && updatedAt == that.updatedAt
                && Objects.equals(deletedAt, that.deletedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, goalId, amountMinor, localDate, note,
            createdAt, updatedAt, deletedAt);
    }
}
