package com.northstar.money.core.database;

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** Aguarda a leitura assíncrona das preferências apenas no executor de abertura de Room. */
public final class CurrencyAwareMigration extends Migration {
    private final CompletableFuture<String> currency;
    private final Function<String, Migration> migration;

    public CurrencyAwareMigration(@NonNull CompletableFuture<String> currency,
            @NonNull Function<String, Migration> migration) {
        super(9, 10);
        this.currency = Objects.requireNonNull(currency);
        this.migration = Objects.requireNonNull(migration);
    }

    @Override
    @WorkerThread
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Database migration must run on a background thread");
        }
        final String code;
        try {
            code = currency.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database migration was interrupted", error);
        } catch (ExecutionException error) {
            // Não atribuir EUR silenciosamente a alocações de outra moeda.
            throw new IllegalStateException("Cannot read the reporting currency for database migration", error.getCause());
        }
        migration.apply(code).migrate(database);
    }
}
