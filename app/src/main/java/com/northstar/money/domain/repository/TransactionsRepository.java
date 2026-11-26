package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.EditableTransaction;
import com.northstar.money.domain.model.Money;
import com.northstar.money.domain.model.TransactionItem;
import com.northstar.money.domain.model.TransactionKind;
import com.northstar.money.domain.model.TransactionPage;
import com.northstar.money.domain.model.TransactionWindow;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface TransactionsRepository {
    @NonNull
    LiveData<List<TransactionItem>> observeTransactions();

    @NonNull
    LiveData<TransactionPage> observeTransactionWindow(@NonNull TransactionWindow window);

    @NonNull
    LiveData<List<TransactionItem>> observeRecentTransactions();

    @NonNull
    LiveData<List<TransactionItem>> observeDeletedTransactions();

    @NonNull
    ListenableFuture<Void> addTransaction(
            @NonNull TransactionKind kind,
            @NonNull Money amount,
            @NonNull String accountId,
            @NonNull String categoryId,
            @NonNull String payee);

    @NonNull
    ListenableFuture<Void> deleteTransaction(@NonNull String id);

    @NonNull
    ListenableFuture<Void> restoreTransaction(@NonNull String id);

    @NonNull
    ListenableFuture<EditableTransaction> getTransactionForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateTransaction(@NonNull EditableTransaction transaction);

    @NonNull
    ListenableFuture<Void> transfer(
            @NonNull Money sourceAmount,
            @NonNull Money destinationAmount,
            @NonNull String sourceAccountId,
            @NonNull String destinationAccountId,
            @NonNull String note);

    @NonNull
    ListenableFuture<Void> setTransactionCleared(@NonNull String id, boolean cleared);

    @NonNull
    ListenableFuture<Void> reconcile(
            @NonNull String accountId,
            @NonNull String statementLocalDate,
            @NonNull Money statementBalance,
            boolean createAdjustment);

    @NonNull
    ListenableFuture<List<TransactionItem>> allTransactions();
}
