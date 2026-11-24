package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "accounts", indices = {@Index("archivedAt")})
public final class AccountEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String type;
    @NonNull private final String currencyCode;
    private final long openingBalanceMinor;
    @Nullable private final Long archivedAt;
    private final long createdAt;
    private final long updatedAt;

    public AccountEntity(@NonNull String id, @NonNull String name, @NonNull String type,
            @NonNull String currencyCode, long openingBalanceMinor, @Nullable Long archivedAt,
            long createdAt, long updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.openingBalanceMinor = openingBalanceMinor;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull public String getId() {
        return id;
    }

    @NonNull public String getName() {
        return name;
    }

    @NonNull public String getType() {
        return type;
    }

    @NonNull public String getCurrencyCode() {
        return currencyCode;
    }

    public long getOpeningBalanceMinor() {
        return openingBalanceMinor;
    }

    @Nullable public Long getArchivedAt() {
        return archivedAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public AccountEntity withDetails(@NonNull String name, @NonNull String type,
            @NonNull String currencyCode, long openingBalanceMinor, long updatedAt) {
        return new AccountEntity(id, name, type, currencyCode, openingBalanceMinor, archivedAt, createdAt, updatedAt);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountEntity)) return false;
        AccountEntity that = (AccountEntity) other;
        return openingBalanceMinor == that.openingBalanceMinor && createdAt == that.createdAt
                && updatedAt == that.updatedAt && id.equals(that.id) && name.equals(that.name)
                && type.equals(that.type) && currencyCode.equals(that.currencyCode)
                && Objects.equals(archivedAt, that.archivedAt);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + currencyCode.hashCode();
        result = 31 * result + Long.hashCode(openingBalanceMinor);
        result = 31 * result + Objects.hashCode(archivedAt);
        result = 31 * result + Long.hashCode(createdAt);
        result = 31 * result + Long.hashCode(updatedAt);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "AccountEntity(id=" + id + ", name=" + name + ", type=" + type
                + ", currencyCode=" + currencyCode + ", openingBalanceMinor=" + openingBalanceMinor
                + ", archivedAt=" + archivedAt + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ")";
    }
}
