package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import java.util.List;
import java.util.Objects;

public final class StoredTransaction {
    @NonNull private final TransactionEntity transaction;
    @NonNull private final List<TransactionEntryEntity> entries;
    private final boolean isReconciliationAdjustment;

    public StoredTransaction(@NonNull TransactionEntity transaction, @NonNull List<TransactionEntryEntity> entries, boolean isReconciliationAdjustment) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.isReconciliationAdjustment = isReconciliationAdjustment;
    }

    @NonNull public TransactionEntity getTransaction() { return transaction; }
    @NonNull public List<TransactionEntryEntity> getEntries() { return entries; }
    public boolean isReconciliationAdjustment() { return isReconciliationAdjustment; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StoredTransaction)) return false;
        StoredTransaction that = (StoredTransaction) other;
        return Objects.equals(transaction, that.transaction)
                && Objects.equals(entries, that.entries)
                && isReconciliationAdjustment == that.isReconciliationAdjustment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction, entries, isReconciliationAdjustment);
    }
}
