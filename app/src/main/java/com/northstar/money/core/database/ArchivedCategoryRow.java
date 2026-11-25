package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

public final class ArchivedCategoryRow {
    @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String kind;
    @Nullable private final String mergedIntoCategoryId;
    @Nullable private final String mergedIntoCategoryName;

    public ArchivedCategoryRow(@NonNull String id, @NonNull String name, @NonNull String kind,
            @Nullable String mergedIntoCategoryId, @Nullable String mergedIntoCategoryName) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.mergedIntoCategoryId = mergedIntoCategoryId;
        this.mergedIntoCategoryName = mergedIntoCategoryName;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getName() { return name; }
    @NonNull public String getKind() { return kind; }
    @Nullable public String getMergedIntoCategoryId() { return mergedIntoCategoryId; }
    @Nullable public String getMergedIntoCategoryName() { return mergedIntoCategoryName; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ArchivedCategoryRow)) return false;
        ArchivedCategoryRow that = (ArchivedCategoryRow) other;
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(kind, that.kind)
                && Objects.equals(mergedIntoCategoryId, that.mergedIntoCategoryId)
                && Objects.equals(mergedIntoCategoryName, that.mergedIntoCategoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, kind, mergedIntoCategoryId, mergedIntoCategoryName);
    }
}
