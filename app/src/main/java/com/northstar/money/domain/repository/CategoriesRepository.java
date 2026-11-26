package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.ArchivedCategory;
import com.northstar.money.domain.model.Category;
import com.northstar.money.domain.model.CategoryKind;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface CategoriesRepository {
    @NonNull
    LiveData<List<Category>> observeCategories();

    @NonNull
    LiveData<List<ArchivedCategory>> observeArchivedCategories();

    @NonNull
    ListenableFuture<Void> createCategory(@NonNull String name, @NonNull CategoryKind kind);

    @NonNull
    ListenableFuture<Void> renameCategory(@NonNull String id, @NonNull String name);

    @NonNull
    ListenableFuture<Void> archiveCategory(@NonNull String id);

    @NonNull
    ListenableFuture<Void> restoreCategory(@NonNull String id);

    @NonNull
    ListenableFuture<Void> mergeCategory(@NonNull String sourceId, @NonNull String targetId);

    @NonNull
    ListenableFuture<Void> undoCategoryMerge(@NonNull String id);
}
