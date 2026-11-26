package com.northstar.money.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.core.database.FinanceDatabaseExecutor;
import com.northstar.money.core.database.SuspendingFinanceDao;
import com.northstar.money.domain.repository.GoalsRepository;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.annotation.Nullable;
import com.northstar.money.core.database.FinanceDao;
import com.northstar.money.core.database.GoalEntity;
import com.northstar.money.core.database.GoalContributionEntity;
import com.northstar.money.core.database.GoalContributionRow;
import com.northstar.money.domain.model.EditableGoal;
import com.northstar.money.domain.model.GoalContribution;
import com.northstar.money.domain.model.Money;
import com.northstar.money.domain.model.SavingsGoal;
import java.time.LocalDate;
import java.util.UUID;
import static com.northstar.money.data.repository.RepositoryValues.*;

final class RoomGoalsRepository implements GoalsRepository {
    private final SuspendingFinanceDao queries;
    private final FinanceDatabaseExecutor database;

    RoomGoalsRepository(SuspendingFinanceDao queries) {
        this.queries = queries;
        database = queries.getExecutor();
    }

    @Override @NonNull
    public LiveData<List<SavingsGoal>> observeGoals() {
        return RoomObservations.rows(queries.observeGoals(), row -> new SavingsGoal(row.getId(), row.getName(),
                new Money(row.getTargetMinor(), row.getCurrencyCode()), new Money(row.getSavedMinor(), row.getCurrencyCode()),
                row.getTargetLocalDate(), row.getStatus()));
    }

    private static GoalContribution contribution(GoalContributionRow row) {
        return new GoalContribution(row.getId(), row.getGoalId(), row.getGoalName(),
                new Money(row.getAmountMinor(), row.getCurrencyCode()), row.getLocalDate(), row.getNote());
    }

    @Override @NonNull
    public LiveData<List<GoalContribution>> observeGoalContributions() {
        return RoomObservations.rows(queries.observeGoalContributions(), RoomGoalsRepository::contribution);
    }

    @Override @NonNull
    public LiveData<List<GoalContribution>> observeDeletedGoalContributions() {
        return RoomObservations.rows(queries.observeDeletedGoalContributions(), RoomGoalsRepository::contribution);
    }

    private static GoalEntity goal(FinanceDao dao, String id) {
        return present(dao.getGoal(id), "Savings goal is missing");
    }

    @Nullable
    private static String targetDate(@Nullable String value) {
        if (value == null || trimmed(value).isEmpty()) return null;
        LocalDate.parse(value);
        return value;
    }

    @Override @NonNull
    public ListenableFuture<Void> createGoal(@NonNull String name, @NonNull Money target,
            @NonNull Money saved, @Nullable String date) {
        return database.write(dao -> {
            String normalized = trimmed(name);
            require(!normalized.isEmpty() && target.getMinor() > 0 && saved.getMinor() >= 0, "Failed requirement.");
            require(target.getCurrencyCode().equals(saved.getCurrencyCode()), "Goal and saved amount currencies must match");
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            GoalEntity goal = new GoalEntity(id, normalized, target.getMinor(), 0, target.getCurrencyCode(),
                    targetDate(date), "ACTIVE", now);
            GoalContributionEntity opening = saved.getMinor() == 0 ? null : new GoalContributionEntity(
                    UUID.randomUUID().toString(), id, saved.getMinor(), LocalDate.now().toString(),
                    "Opening saved amount", now, now);
            dao.insertGoalWithInitialContribution(goal, opening);
        });
    }

    @Override @NonNull
    public ListenableFuture<EditableGoal> getGoalForEdit(@NonNull String id) {
        return database.read(dao -> {
            GoalEntity stored = goal(dao, id);
            return new EditableGoal(id, stored.getName(), new Money(stored.getTargetMinor(), stored.getCurrencyCode()),
                    stored.getTargetLocalDate(), stored.getStatus());
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> updateGoal(@NonNull EditableGoal editable) {
        return database.write(dao -> {
            String name = trimmed(editable.getName());
            Money target = editable.getTarget();
            require(!name.isEmpty() && target.getMinor() > 0, "Name and positive target are required");
            String status = editable.getStatus();
            require("ACTIVE".equals(status) || "PAUSED".equals(status) || "COMPLETED".equals(status), "Invalid goal status");
            String date = targetDate(editable.getTargetLocalDate());
            GoalEntity stored = goal(dao, editable.getId());
            require(target.getCurrencyCode().equals(stored.getCurrencyCode()), "Goal currency cannot be changed");
            dao.updateGoal(stored.withDetails(name, target.getMinor(), date, status));
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> addGoalContribution(@NonNull String goalId, @NonNull Money amount,
            @NonNull String localDate, @NonNull String note) {
        return database.write(dao -> {
            require(amount.getMinor() > 0, "Contribution must be positive");
            GoalEntity goal = goal(dao, goalId);
            require(amount.getCurrencyCode().equals(goal.getCurrencyCode()), "Contribution currency must match the goal");
            String date = LocalDate.parse(localDate).toString();
            require(note.length() <= 500, "Contribution note is too long");
            long now = System.currentTimeMillis();
            dao.insertGoalContribution(new GoalContributionEntity(UUID.randomUUID().toString(), goalId, amount.getMinor(),
                    date, trimmed(note), now, now));
        });
    }

    @Override @NonNull
    public ListenableFuture<GoalContribution> getGoalContributionForEdit(@NonNull String id) {
        return database.read(dao -> {
            GoalContributionEntity stored = present(dao.getGoalContributionForEdit(id), "Contribution is missing or deleted");
            GoalEntity goal = goal(dao, stored.getGoalId());
            return new GoalContribution(id, goal.getId(), goal.getName(), new Money(stored.getAmountMinor(), goal.getCurrencyCode()),
                    stored.getLocalDate(), stored.getNote());
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> updateGoalContribution(@NonNull GoalContribution contribution) {
        return database.write(dao -> {
            Money amount = contribution.getAmount();
            require(amount.getMinor() > 0, "Contribution must be positive");
            require(contribution.getNote().length() <= 500, "Contribution note is too long");
            String date = LocalDate.parse(contribution.getLocalDate()).toString();
            GoalContributionEntity stored = present(dao.getGoalContributionForEdit(contribution.getId()), "Contribution is missing or deleted");
            GoalEntity goal = goal(dao, contribution.getGoalId());
            require(amount.getCurrencyCode().equals(goal.getCurrencyCode()), "Contribution currency must match the goal");
            dao.updateGoalContribution(stored.withDetails(goal.getId(), amount.getMinor(), date, trimmed(contribution.getNote()),
                    System.currentTimeMillis()));
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> deleteGoalContribution(@NonNull String id) {
        return database.write(dao -> dao.deleteGoalContribution(id, System.currentTimeMillis()));
    }

    @Override @NonNull
    public ListenableFuture<Void> restoreGoalContribution(@NonNull String id) {
        return database.write(dao -> dao.restoreGoalContribution(id, System.currentTimeMillis()));
    }
}
