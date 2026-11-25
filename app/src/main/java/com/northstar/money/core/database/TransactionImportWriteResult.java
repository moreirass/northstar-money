package com.northstar.money.core.database;

import java.util.Objects;

public final class TransactionImportWriteResult {
    private final int imported;
    private final int skippedDuplicates;

    public TransactionImportWriteResult(int imported, int skippedDuplicates) {
        this.imported = imported;
        this.skippedDuplicates = skippedDuplicates;
    }

    public int getImported() { return imported; }
    public int getSkippedDuplicates() { return skippedDuplicates; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionImportWriteResult)) return false;
        TransactionImportWriteResult that = (TransactionImportWriteResult) other;
        return imported == that.imported
                && skippedDuplicates == that.skippedDuplicates;
    }

    @Override
    public int hashCode() {
        return Objects.hash(imported, skippedDuplicates);
    }
}
