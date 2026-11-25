package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.room.Ignore;
import java.util.Objects;

public final class AccountBalanceRow {
    @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String type;
    @NonNull private final String currencyCode;
    private final long balanceMinor;
    private final long clearedBalanceMinor;

    public AccountBalanceRow(@NonNull String id, @NonNull String name, @NonNull String type,
            @NonNull String currencyCode, long balanceMinor, long clearedBalanceMinor) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.balanceMinor = balanceMinor;
        this.clearedBalanceMinor = clearedBalanceMinor;
    }

    @Ignore
    public AccountBalanceRow(@NonNull String id, @NonNull String name, @NonNull String type,
            @NonNull String currencyCode, long balanceMinor) {
        this(id, name, type, currencyCode, balanceMinor,
            balanceMinor);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getType() { return type; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    public long getBalanceMinor() { return balanceMinor; }
    public long getClearedBalanceMinor() { return clearedBalanceMinor; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountBalanceRow)) return false;
        AccountBalanceRow that = (AccountBalanceRow) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(type, that.type)
                && Objects.equals(currencyCode, that.currencyCode)
                && balanceMinor == that.balanceMinor
                && clearedBalanceMinor == that.clearedBalanceMinor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, currencyCode, balanceMinor,
            clearedBalanceMinor);
    }
}
