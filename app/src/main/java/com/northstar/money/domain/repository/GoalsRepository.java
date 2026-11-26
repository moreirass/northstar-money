package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.EditableGoal;
import com.northstar.money.domain.model.GoalContribution;
import com.northstar.money.domain.model.Money;
import com.northstar.money.domain.model.SavingsGoal;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface GoalsRepository {
    @NonNull
    LiveData<List<SavingsGoal>> observeGoals();

    @NonNull
    LiveData<List<GoalContribution>> observeGoalContributions();

    @NonNull
    LiveData<List<GoalContribution>> observeDeletedGoalContributions();

    @NonNull
    ListenableFuture<Void> createGoal(
            @NonNull String name,
            @NonNull Money target,
            @NonNull Money saved,
            @Nullable String targetDate);

    @NonNull
    ListenableFuture<EditableGoal> getGoalForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateGoal(@NonNull EditableGoal goal);

    @NonNull
    ListenableFuture<Void> addGoalContribution(
            @NonNull String goalId,
            @NonNull Money amount,
            @NonNull String localDate,
            @NonNull String note);

    @NonNull
    ListenableFuture<GoalContribution> getGoalContributionForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateGoalContribution(@NonNull GoalContribution contribution);

    @NonNull
    ListenableFuture<Void> deleteGoalContribution(@NonNull String id);

    @NonNull
    ListenableFuture<Void> restoreGoalContribution(@NonNull String id);
}
