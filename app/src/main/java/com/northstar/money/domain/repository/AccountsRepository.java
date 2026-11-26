package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.Account;
import com.northstar.money.domain.model.AccountType;
import com.northstar.money.domain.model.EditableAccount;
import com.northstar.money.domain.model.Money;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface AccountsRepository {
    @NonNull
    LiveData<List<Account>> observeAccounts();

    @NonNull
    LiveData<List<Account>> observeArchivedAccounts();

    @NonNull
    ListenableFuture<Void> seedIfEmpty();

    @NonNull
    ListenableFuture<Void> createAccount(@NonNull String name, @NonNull AccountType type, @NonNull Money openingBalance);

    @NonNull
    ListenableFuture<Void> configureInitialAccount(@NonNull String name, @NonNull Money openingBalance);

    @NonNull
    ListenableFuture<EditableAccount> getAccountForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateAccount(@NonNull EditableAccount account);

    @NonNull
    ListenableFuture<Void> archiveAccount(@NonNull String id);

    @NonNull
    ListenableFuture<Void> restoreAccount(@NonNull String id);
}
