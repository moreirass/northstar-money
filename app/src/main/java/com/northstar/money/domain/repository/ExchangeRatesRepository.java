package com.northstar.money.domain.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.northstar.money.domain.model.HistoricalExchangeRate;
import java.util.List;
import com.google.common.util.concurrent.ListenableFuture;

public interface ExchangeRatesRepository {
    @NonNull
    LiveData<List<HistoricalExchangeRate>> observeHistoricalExchangeRates();

    @NonNull
    ListenableFuture<Void> requestExchangeRateRefresh();

    @NonNull
    ListenableFuture<Integer> refreshPendingExchangeRates();
}
