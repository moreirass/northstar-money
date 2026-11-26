package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.ReceiptAttachment;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface ReceiptsRepository {
    @NonNull
    LiveData<List<ReceiptAttachment>> observeReceiptAttachments();

    @NonNull
    ListenableFuture<Void> addReceiptAttachment(
            @NonNull String transactionId,
            @NonNull String originalName,
            @NonNull String mimeType,
            @NonNull byte[] content);

    @NonNull
    ListenableFuture<Void> deleteReceiptAttachment(@NonNull String id);

    @NonNull
    ListenableFuture<Void> retryReceiptOcr(@NonNull String id);

    @NonNull
    ListenableFuture<Void> applyReceiptOcr(@NonNull String id);
}
