package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.Objects;

public final class TransactionImportItem {
    @NonNull private final TransactionEntity transaction;
    @NonNull private final TransactionEntryEntity entry;

    public TransactionImportItem(@NonNull TransactionEntity transaction, @NonNull TransactionEntryEntity entry) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.entry = Objects.requireNonNull(entry, "entry");
    }

    @NonNull public TransactionEntity getTransaction() { return transaction; }
    @NonNull public TransactionEntryEntity getEntry() { return entry; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionImportItem)) return false;
        TransactionImportItem that = (TransactionImportItem) other;
        return Objects.equals(transaction, that.transaction)
                && Objects.equals(entry, that.entry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction, entry);
    }
}
