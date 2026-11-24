package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "transactions", indices = {@Index("localDate"), @Index("createdAt"), @Index("deletedAt")})
public final class TransactionEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String kind;
    @NonNull private final String localDate;
    @NonNull private final String payee;
    @NonNull private final String note;
    private final long createdAt;
    private final long updatedAt;
    @Nullable private final Long deletedAt;

    public TransactionEntity(@NonNull String id, @NonNull String kind, @NonNull String localDate,
            @NonNull String payee, @NonNull String note, long createdAt,
            long updatedAt, @Nullable Long deletedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.localDate = Objects.requireNonNull(localDate, "localDate");
        this.payee = Objects.requireNonNull(payee, "payee");
        this.note = Objects.requireNonNull(note, "note");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    @Ignore
    public TransactionEntity(@NonNull String id, @NonNull String kind, @NonNull String localDate,
            @NonNull String payee, @NonNull String note, long createdAt, long updatedAt) {
        this(id, kind, localDate, payee, note, createdAt, updatedAt, null);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getKind() { return kind; }
    @NonNull public String getLocalDate() { return localDate; }
    @NonNull public String getPayee() { return payee; }
    @NonNull public String getNote() { return note; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    @Nullable public Long getDeletedAt() { return deletedAt; }

    @NonNull
    public TransactionEntity withDetails(@NonNull String localDate, @NonNull String payee,
            @NonNull String note, long updatedAt) {
        return new TransactionEntity(id, kind, localDate, payee, note, createdAt, updatedAt, deletedAt);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionEntity)) return false;
        TransactionEntity that = (TransactionEntity) other;
        return id.equals(that.id)
                && kind.equals(that.kind)
                && localDate.equals(that.localDate)
                && payee.equals(that.payee)
                && note.equals(that.note)
                && createdAt == that.createdAt
                && updatedAt == that.updatedAt
                && Objects.equals(deletedAt, that.deletedAt);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + localDate.hashCode();
        result = 31 * result + payee.hashCode();
        result = 31 * result + note.hashCode();
        result = 31 * result + Long.hashCode(createdAt);
        result = 31 * result + Long.hashCode(updatedAt);
        result = 31 * result + Objects.hashCode(deletedAt);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "TransactionEntity(id=" + id
                + ", kind=" + kind + ", localDate=" + localDate
                + ", payee=" + payee + ", note=" + note
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt
                + ", deletedAt=" + deletedAt + ")";
    }
}
