package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.Objects;

public final class GoalContributionRow {
    @NonNull private final String id;
    @NonNull private final String goalId;
    @NonNull private final String goalName;
    private final long amountMinor;
    @NonNull private final String currencyCode;
    @NonNull private final String localDate;
    @NonNull private final String note;

    public GoalContributionRow(@NonNull String id, @NonNull String goalId, @NonNull String goalName,
            long amountMinor, @NonNull String currencyCode, @NonNull String localDate,
            @NonNull String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.goalName = Objects.requireNonNull(goalName, "goalName");
        this.amountMinor = amountMinor;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.localDate = Objects.requireNonNull(localDate, "localDate");
        this.note = Objects.requireNonNull(note, "note");
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getGoalId() { return goalId; }
    @NonNull public String getGoalName() { return goalName; }
    public long getAmountMinor() { return amountMinor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    @NonNull public String getLocalDate() { return localDate; }
    @NonNull public String getNote() { return note; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GoalContributionRow)) return false;
        GoalContributionRow that = (GoalContributionRow) other;
        return Objects.equals(id, that.id)
                && Objects.equals(goalId, that.goalId)
                && Objects.equals(goalName, that.goalName)
                && amountMinor == that.amountMinor
                && Objects.equals(currencyCode, that.currencyCode)
                && Objects.equals(localDate, that.localDate)
                && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, goalId, goalName, amountMinor, currencyCode,
            localDate, note);
    }
}
