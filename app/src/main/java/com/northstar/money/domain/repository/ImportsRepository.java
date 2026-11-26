package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import com.northstar.money.domain.model.ImportResult;
import com.google.common.util.concurrent.ListenableFuture;

public interface ImportsRepository {
    @NonNull
    ListenableFuture<ImportResult> importCsv(@NonNull String csv);
}
