package com.northstar.money.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.core.database.FinanceDatabaseExecutor;
import com.northstar.money.core.database.SuspendingFinanceDao;
import com.northstar.money.domain.repository.CategoriesRepository;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;
import com.northstar.money.core.database.CategoryEntity;
import com.northstar.money.domain.model.Category;
import com.northstar.money.domain.model.CategoryKind;
import com.northstar.money.domain.model.ArchivedCategory;
import java.util.UUID;
import static com.northstar.money.data.repository.RepositoryValues.require;
import static com.northstar.money.data.repository.RepositoryValues.trimmed;

final class RoomCategoriesRepository implements CategoriesRepository {
    private final SuspendingFinanceDao queries;
    private final FinanceDatabaseExecutor database;

    RoomCategoriesRepository(SuspendingFinanceDao queries) {
        this.queries = queries;
        database = queries.getExecutor();
    }

    @Override @NonNull
    public LiveData<List<Category>> observeCategories() {
        return RoomObservations.rows(queries.observeCategories(), row ->
                new Category(row.getId(), row.getName(), CategoryKind.valueOf(row.getKind())));
    }

    @Override @NonNull
    public LiveData<List<ArchivedCategory>> observeArchivedCategories() {
        return RoomObservations.rows(queries.observeArchivedCategories(), row -> new ArchivedCategory(
                row.getId(), row.getName(), CategoryKind.valueOf(row.getKind()),
                row.getMergedIntoCategoryId(), row.getMergedIntoCategoryName()));
    }

    @Override @NonNull
    public ListenableFuture<Void> createCategory(@NonNull String name, @NonNull CategoryKind kind) {
        return database.write(dao -> {
            String normalized = trimmed(name);
            require(!normalized.isEmpty(), "Failed requirement.");
            dao.insertCategory(new CategoryEntity(UUID.randomUUID().toString(), normalized, kind.name(),
                    dao.countActiveCategories(kind.name())));
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> renameCategory(@NonNull String id, @NonNull String name) {
        return database.write(dao -> {
            String normalized = trimmed(name);
            require(!normalized.isEmpty(), "Category name is required");
            dao.renameCategory(id, normalized);
        });
    }

    @Override @NonNull
    public ListenableFuture<Void> archiveCategory(@NonNull String id) {
        return database.write(dao -> dao.archiveCategory(id, System.currentTimeMillis()));
    }

    @Override @NonNull
    public ListenableFuture<Void> restoreCategory(@NonNull String id) {
        return database.write(dao -> dao.restoreCategory(id));
    }

    @Override @NonNull
    public ListenableFuture<Void> mergeCategory(@NonNull String sourceId, @NonNull String targetId) {
        return database.write(dao -> dao.mergeCategory(sourceId, targetId, System.currentTimeMillis()));
    }

    @Override @NonNull
    public ListenableFuture<Void> undoCategoryMerge(@NonNull String id) {
        return database.write(dao -> dao.undoCategoryMerge(id));
    }
}
