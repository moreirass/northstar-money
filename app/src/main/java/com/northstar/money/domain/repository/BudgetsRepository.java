package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.BudgetProgress;
import com.northstar.money.domain.model.CategorySpending;
import com.northstar.money.domain.model.FinanceSummary;
import com.northstar.money.domain.model.Money;
import com.northstar.money.domain.model.MonthlyExpense;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface BudgetsRepository {
    @NonNull
    LiveData<FinanceSummary> observeMonthSummary(@NonNull String monthStart, @NonNull String currency);

    @NonNull
    LiveData<List<CategorySpending>> observeCategorySpending(@Nullable String accountId, @NonNull String currency);

    @NonNull
    LiveData<List<MonthlyExpense>> observeMonthlyExpenses(
            @NonNull String categoryId,
            @NonNull String currency,
            @NonNull String start,
            @NonNull String end);

    @NonNull
    LiveData<FinanceSummary> observeSummary();

    @NonNull
    LiveData<List<BudgetProgress>> observeBudgets();

    @NonNull
    ListenableFuture<Void> setBudget(@NonNull String categoryId, @NonNull Money planned);
}
