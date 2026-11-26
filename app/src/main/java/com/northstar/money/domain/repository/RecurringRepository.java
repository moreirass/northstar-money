package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.EditableRecurring;
import com.northstar.money.domain.model.Money;
import com.northstar.money.domain.model.RecurringItem;
import com.northstar.money.domain.model.TransactionKind;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface RecurringRepository {
    @NonNull
    LiveData<List<RecurringItem>> observeRecurring();

    @NonNull
    LiveData<List<RecurringItem>> observePausedRecurring();

    @NonNull
    LiveData<List<RecurringItem>> observeDeletedRecurring();

    @NonNull
    ListenableFuture<Void> createRecurring(
            @NonNull String name,
            @NonNull TransactionKind kind,
            @NonNull Money amount,
            @NonNull String accountId,
            @Nullable String categoryId,
            @NonNull String frequency,
            @NonNull String nextDate);

    @NonNull
    ListenableFuture<EditableRecurring> getRecurringForEdit(@NonNull String id);

    @NonNull
    ListenableFuture<Void> updateRecurring(@NonNull EditableRecurring recurring);

    @NonNull
    ListenableFuture<Void> pauseRecurring(@NonNull String id);

    @NonNull
    ListenableFuture<Void> resumeRecurring(@NonNull String id);

    @NonNull
    ListenableFuture<Void> deleteRecurring(@NonNull String id);

    @NonNull
    ListenableFuture<Void> restoreRecurring(@NonNull String id);

    @NonNull
    ListenableFuture<Integer> postDueRecurringOccurrences(@NonNull String throughLocalDate);
}
