package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import java.io.OutputStream;
import com.google.common.util.concurrent.ListenableFuture;

public interface BackupsRepository {
    @NonNull
    ListenableFuture<String> createFullBackup();

    @NonNull
    ListenableFuture<Void> writeFullBackup(@NonNull OutputStream output, @NonNull char[] password);

    @NonNull
    ListenableFuture<Void> restoreFullBackup(@NonNull String backup, @NonNull char[] recoveryPassword);

    @NonNull
    ListenableFuture<Void> undoLastFullRestore(@NonNull char[] recoveryPassword);
}
