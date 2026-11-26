package com.northstar.money.data.repository;

import com.northstar.money.core.database.AccountEntity;
import com.northstar.money.core.database.CategoryEntity;
import com.northstar.money.core.database.FinanceDao;
import com.northstar.money.core.database.FinanceDatabaseExecutor;
import com.northstar.money.domain.model.AccountType;
import com.northstar.money.domain.model.EditableAccount;
import com.northstar.money.domain.model.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.google.common.util.concurrent.ListenableFuture;

final class AccountOperations {
    private static final String INITIAL_ACCOUNT = "main-account";
    private final FinanceDatabaseExecutor database;

    AccountOperations(FinanceDatabaseExecutor database) {
        this.database = database;
    }

    ListenableFuture<Void> seed(String currency) {
        return database.write(dao -> {
            long now = System.currentTimeMillis();
            List<CategoryEntity> categories = new ArrayList<>();
            for (String name : new String[]{"Housing", "Groceries", "Transport", "Dining", "Health", "Shopping", "Other"}) {
                categories.add(new CategoryEntity("expense-" + name.toLowerCase(Locale.ROOT), name,
                        "EXPENSE", categories.size()));
            }
            categories.add(new CategoryEntity("income-salary", "Salary", "INCOME", 0));
            AccountEntity account = new AccountEntity(INITIAL_ACCOUNT, "Conta Principal", "CHECKING",
                    currency, 0, null, now, now);
            dao.seedIfEmpty(Collections.singletonList(account), categories);
        });
    }

    ListenableFuture<Void> create(String name, AccountType type, Money opening) {
        return database.write(dao -> dao.insertAccount(newAccount(name, type, opening)));
    }

    ListenableFuture<Boolean> configureInitial(String name, Money opening) {
        return database.transaction(dao -> {
            if (opening.getMinor() < 0) throw new IllegalArgumentException("Initial balance cannot be negative");
            AccountEntity initial = dao.getActiveAccount(INITIAL_ACCOUNT);
            if (initial != null && dao.countEntriesForAccount(INITIAL_ACCOUNT) == 0) {
                dao.updateAccount(initial.withDetails(name, initial.getType(), opening.getCurrencyCode(),
                        opening.getMinor(), System.currentTimeMillis()));
                return false;
            }
            dao.insertAccount(newAccount(name, AccountType.CHECKING, opening));
            return true;
        });
    }

    private AccountEntity newAccount(String name, AccountType type, Money opening) {
        long now = System.currentTimeMillis();
        return new AccountEntity(UUID.randomUUID().toString(), name, type.name(), opening.getCurrencyCode(),
                opening.getMinor(), null, now, now);
    }

    private AccountEntity active(FinanceDao dao, String id) {
        AccountEntity stored = dao.getActiveAccount(id);
        if (stored == null) throw new IllegalArgumentException("Account is missing or archived");
        return stored;
    }

    ListenableFuture<EditableAccount> forEdit(String id) {
        return database.read(dao -> {
            AccountEntity stored = active(dao, id);
            return new EditableAccount(id, stored.getName(), AccountType.valueOf(stored.getType()),
                    new Money(stored.getOpeningBalanceMinor(), stored.getCurrencyCode()));
        });
    }

    ListenableFuture<Void> update(EditableAccount account, String name) {
        return database.write(dao -> {
            AccountEntity stored = active(dao, account.getId());
            Money opening = account.getOpeningBalance();
            if (!opening.getCurrencyCode().equals(stored.getCurrencyCode())) {
                throw new IllegalArgumentException("Account currency cannot be changed");
            }
            dao.updateAccount(stored.withDetails(name, account.getType().name(), stored.getCurrencyCode(),
                    opening.getMinor(), System.currentTimeMillis()));
        });
    }

    ListenableFuture<Void> archive(String id) {
        return database.write(dao -> dao.archiveAccount(id, System.currentTimeMillis()));
    }

    ListenableFuture<Void> restore(String id) {
        return database.write(dao -> dao.restoreAccount(id, System.currentTimeMillis()));
    }
}
