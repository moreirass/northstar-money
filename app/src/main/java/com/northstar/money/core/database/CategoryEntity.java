package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "categories",
        foreignKeys = @ForeignKey(entity = CategoryEntity.class, parentColumns = "id",
                childColumns = "mergedIntoCategoryId", onDelete = ForeignKey.SET_NULL, deferred = true),
        indices = {@Index(value = {"kind", "name"}, unique = true), @Index("mergedIntoCategoryId")})
public final class CategoryEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String kind;
    private final int sortOrder;
    @Nullable private final Long archivedAt;
    @Nullable private final String mergedIntoCategoryId;

    public CategoryEntity(@NonNull String id, @NonNull String name, @NonNull String kind,
            int sortOrder, @Nullable Long archivedAt, @Nullable String mergedIntoCategoryId) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sortOrder = sortOrder;
        this.archivedAt = archivedAt;
        this.mergedIntoCategoryId = mergedIntoCategoryId;
    }

    @Ignore
    public CategoryEntity(@NonNull String id, @NonNull String name, @NonNull String kind, int sortOrder) {
        this(id, name, kind, sortOrder, null, null);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getKind() { return kind; }
    public int getSortOrder() { return sortOrder; }
    @Nullable public Long getArchivedAt() { return archivedAt; }
    @Nullable public String getMergedIntoCategoryId() { return mergedIntoCategoryId; }

    @NonNull
    public CategoryEntity withMergeTarget(@Nullable String targetId) {
        return new CategoryEntity(id, name, kind, sortOrder, archivedAt, targetId);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof CategoryEntity)) return false;
        CategoryEntity that = (CategoryEntity) other;
        return sortOrder == that.sortOrder && id.equals(that.id) && name.equals(that.name)
                && kind.equals(that.kind) && Objects.equals(archivedAt, that.archivedAt)
                && Objects.equals(mergedIntoCategoryId, that.mergedIntoCategoryId);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + sortOrder;
        result = 31 * result + Objects.hashCode(archivedAt);
        return 31 * result + Objects.hashCode(mergedIntoCategoryId);
    }

    @Override
    @NonNull
    public String toString() {
        return "CategoryEntity(id=" + id + ", name=" + name + ", kind=" + kind + ", sortOrder="
                + sortOrder + ", archivedAt=" + archivedAt + ", mergedIntoCategoryId=" + mergedIntoCategoryId + ")";
    }
}
