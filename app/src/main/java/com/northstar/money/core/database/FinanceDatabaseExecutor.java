package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Executa as leituras e transações nos executores da instância Room que possui o DAO. */
public final class FinanceDatabaseExecutor {
    private final NorthstarDatabase database;
    private final FinanceDao dao;

    FinanceDatabaseExecutor(@NonNull NorthstarDatabase database, @NonNull FinanceDao dao) {
        this.database = database;
        this.dao = dao;
    }

    private static <T> ListenableFuture<T> submit(Executor executor, Callable<T> operation) {
        return CallbackToFutureAdapter.getFuture(completer -> {
            FutureTask<Void> task = new FutureTask<>(() -> {
                try {
                    completer.set(operation.call());
                } catch (Throwable failure) {
                    completer.setException(failure);
                }
                return null;
            });
            completer.addCancellationListener(() -> task.cancel(false), Runnable::run);
            executor.execute(task);
            return "FinanceDatabaseExecutor";
        });
    }

    @NonNull
    public <T> ListenableFuture<T> read(@NonNull Function<FinanceDao, T> query) {
        return submit(database.getQueryExecutor(), () -> query.apply(dao));
    }

    @NonNull
    public <T> ListenableFuture<T> transaction(@NonNull Function<FinanceDao, T> work) {
        return submit(database.getTransactionExecutor(), () -> database.runInTransaction(
                (Callable<T>) () -> work.apply(dao)));
    }

    @NonNull
    public ListenableFuture<Void> write(@NonNull Consumer<FinanceDao> mutation) {
        return transaction(dao -> {
            mutation.accept(dao);
            return null;
        });
    }
}
