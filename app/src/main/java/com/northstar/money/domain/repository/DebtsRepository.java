package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.DebtProfile;
import com.northstar.money.domain.model.Money;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface DebtsRepository {
    @NonNull
    LiveData<List<DebtProfile>> observeDebts();

    @NonNull
    ListenableFuture<Void> createDebt(
            @NonNull String accountId,
            int annualRateBasisPoints,
            @NonNull Money minimumPayment,
            int dueDay);

    @NonNull
    ListenableFuture<DebtProfile> getDebtForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateDebt(@NonNull DebtProfile debt);
}
