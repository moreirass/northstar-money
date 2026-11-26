package com.northstar.money.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.core.database.FinanceDatabaseExecutor;
import com.northstar.money.core.database.SuspendingFinanceDao;
import com.northstar.money.domain.repository.DebtsRepository;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;
import com.northstar.money.core.database.DebtProfileEntity;
import com.northstar.money.domain.model.DebtProfile;
import com.northstar.money.domain.model.Money;
import java.util.UUID;
import static com.northstar.money.data.repository.RepositoryValues.*;

final class RoomDebtsRepository implements DebtsRepository {
    private final SuspendingFinanceDao queries;
    private final FinanceDatabaseExecutor database;

    RoomDebtsRepository(SuspendingFinanceDao queries) {
        this.queries = queries;
        database = queries.getExecutor();
    }

    @Override @NonNull
    public LiveData<List<DebtProfile>> observeDebts() {
        return RoomObservations.rows(queries.observeDebts(), row -> new DebtProfile(row.getId(), row.getAccountId(),
                row.getAnnualRateBasisPoints(), new Money(row.getMinimumPaymentMinor(), row.getCurrencyCode()), row.getDueDay()));
    }

    @Override @NonNull
    public ListenableFuture<Void> createDebt(@NonNull String accountId, int annualRateBasisPoints,
            @NonNull Money minimumPayment, int dueDay) {
        return database.write(dao -> {
            require(validTerms(annualRateBasisPoints, minimumPayment, dueDay), "Failed requirement.");
            requireCurrency(dao, accountId, minimumPayment.getCurrencyCode());
            dao.insertDebtProfile(new DebtProfileEntity(UUID.randomUUID().toString(), accountId, annualRateBasisPoints,
                    minimumPayment.getMinor(), dueDay, System.currentTimeMillis()));
        });
    }

    private boolean validTerms(int rate, Money payment, int day) {
        return rate >= 0 && payment.getMinor() >= 0 && day >= 1 && day <= 31;
    }

    @Override @NonNull
    public ListenableFuture<DebtProfile> getDebtForEdit(@NonNull String id) {
        return database.read(dao -> {
            DebtProfileEntity stored = present(dao.getDebtProfile(id), "Debt profile is missing");
            String currency = present(dao.getActiveAccountCurrency(stored.getAccountId()), "Debt account is missing or archived");
            return new DebtProfile(stored.getId(), stored.getAccountId(), stored.getAnnualRateBasisPoints(),
                    new Money(stored.getMinimumPaymentMinor(), currency), stored.getDueDay());
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> updateDebt(@NonNull DebtProfile debt) {
        return database.write(dao -> {
            Money payment = debt.getMinimumPayment();
            require(validTerms(debt.getAnnualRateBasisPoints(), payment, debt.getDueDay()), "Rate, minimum payment or due day is invalid");
            DebtProfileEntity stored = present(dao.getDebtProfile(debt.getId()), "Debt profile is missing");
            require(stored.getAccountId().equals(debt.getAccountId()), "Debt account cannot be changed");
            requireCurrency(dao, stored.getAccountId(), payment.getCurrencyCode());
            dao.updateDebtProfile(stored.withTerms(debt.getAnnualRateBasisPoints(), payment.getMinor(), debt.getDueDay()));
        });
    }
}
