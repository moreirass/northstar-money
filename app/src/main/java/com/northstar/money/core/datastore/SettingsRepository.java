package com.northstar.money.core.datastore;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import com.northstar.money.core.security.AppAuthentication;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SettingsRepository {
    public static final Set<String> SUPPORTED_CURRENCIES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("EUR", "USD", "GBP", "BRL", "CHF", "JPY")));
    private static final Preferences.Key<Boolean> APP_LOCK = PreferencesKeys.booleanKey("app_lock");
    private static final Preferences.Key<Boolean> REMINDERS = PreferencesKeys.booleanKey("reminders");
    private static final Preferences.Key<Boolean> ONBOARDING_COMPLETED = PreferencesKeys.booleanKey("onboarding_completed");
    private static final Preferences.Key<Boolean> MONEY_VALUES_HIDDEN = PreferencesKeys.booleanKey("money_values_hidden");
    private static final Preferences.Key<String> BASE_CURRENCY_CODE = PreferencesKeys.stringKey("base_currency_code");
    private static final Preferences.Key<Long> SPENDING_LIMIT_MINOR = PreferencesKeys.longKey("spending_limit_minor");
    private static final Preferences.Key<String> SPENDING_LIMIT_CURRENCY = PreferencesKeys.stringKey("spending_limit_currency");
    private static final Preferences.Key<String> SPENDING_LIMIT_PERIOD = PreferencesKeys.stringKey("spending_limit_period");
    private static final Preferences.Key<String> SPENDING_LIMIT_START = PreferencesKeys.stringKey("spending_limit_start");
    private static final Preferences.Key<String> SPENDING_LIMIT_END = PreferencesKeys.stringKey("spending_limit_end");

    private static SettingsRepository instance;
    private final Context context;
    private final RxDataStore<Preferences> store;

    private SettingsRepository(Context context) {
        this.context = context;
        store = new RxPreferenceDataStoreBuilder(context, "northstar_settings").build();
    }

    @NonNull
    public static synchronized SettingsRepository get(@NonNull Context context) {
        if (instance == null) instance = new SettingsRepository(context.getApplicationContext());
        return instance;
    }

    @NonNull
    public Flowable<AppSettings> settings() {
        return store.data().map(values -> new AppSettings(
                value(values, APP_LOCK, false), value(values, REMINDERS, true),
                value(values, ONBOARDING_COMPLETED, false), value(values, MONEY_VALUES_HIDDEN, false),
                value(values, BASE_CURRENCY_CODE, "EUR"), spendingLimit(values)));
    }

    @NonNull
    public CompletableFuture<String> readBaseCurrency() {
        CompletableFuture<String> result = new CompletableFuture<>();
        store.data().firstOrError().subscribe(
                values -> result.complete(value(values, BASE_CURRENCY_CODE, "EUR")),
                result::completeExceptionally);
        return result;
    }

    private static SpendingLimit spendingLimit(Preferences values) {
        Long amount = values.get(SPENDING_LIMIT_MINOR);
        if (amount == null) return null;
        BudgetPeriod period;
        try {
            period = BudgetPeriod.valueOf(value(values, SPENDING_LIMIT_PERIOD, ""));
        } catch (IllegalArgumentException unknownPeriod) {
            period = BudgetPeriod.MONTH;
        }
        return new SpendingLimit(amount, value(values, SPENDING_LIMIT_CURRENCY, "EUR"), period,
                value(values, SPENDING_LIMIT_START, ""), value(values, SPENDING_LIMIT_END, ""));
    }

    private static <T> T value(Preferences values, Preferences.Key<T> key, T fallback) {
        T stored = values.get(key);
        return stored == null ? fallback : stored;
    }

    private Completable edit(Consumer<MutablePreferences> update) {
        return store.updateDataAsync(current -> {
            MutablePreferences next = current.toMutablePreferences();
            update.accept(next);
            return Single.just(next);
        }).ignoreElement();
    }

    @NonNull
    public Completable setAppLock(boolean enabled) {
        return edit(values -> {
            if (enabled && !AppAuthentication.INSTANCE.available(context)) {
                throw new IllegalArgumentException("Set up a screen lock or biometrics before enabling app lock");
            }
            values.set(APP_LOCK, enabled);
        });
    }

    @NonNull
    public Completable setReminders(boolean enabled) {
        return edit(values -> {
            values.set(REMINDERS, enabled);
        });
    }

    @NonNull
    public Completable setOnboardingCompleted(boolean enabled) {
        return edit(values -> {
            values.set(ONBOARDING_COMPLETED, enabled);
        });
    }

    @NonNull
    public Completable setMoneyValuesHidden(boolean enabled) {
        return edit(values -> {
            values.set(MONEY_VALUES_HIDDEN, enabled);
        });
    }

    @NonNull
    public Completable setBaseCurrencyCode(@NonNull String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(normalized)) throw new IllegalArgumentException("Unsupported base currency: " + code);
        return edit(values -> values.set(BASE_CURRENCY_CODE, normalized));
    }

    @NonNull
    public Completable setSpendingLimit(@NonNull SpendingLimit limit) {
        if (limit.getAmountMinor() <= 0) throw new IllegalArgumentException("Spending limit must be positive");
        if (!SUPPORTED_CURRENCIES.contains(limit.getCurrencyCode())) throw new IllegalArgumentException("Unsupported spending limit currency");
        LocalDate start = LocalDate.parse(limit.getStartDate());
        if (LocalDate.parse(limit.getEndDate()).isBefore(start)) throw new IllegalArgumentException("Spending limit end date must not precede its start date");
        return edit(values -> {
            values.set(SPENDING_LIMIT_MINOR, limit.getAmountMinor());
            values.set(SPENDING_LIMIT_CURRENCY, limit.getCurrencyCode());
            values.set(SPENDING_LIMIT_PERIOD, limit.getPeriod().name());
            values.set(SPENDING_LIMIT_START, limit.getStartDate());
            values.set(SPENDING_LIMIT_END, limit.getEndDate());
        });
    }

    @NonNull
    public Completable clearSpendingLimit() {
        return edit(values -> {
            values.remove(SPENDING_LIMIT_MINOR);
            values.remove(SPENDING_LIMIT_CURRENCY);
            values.remove(SPENDING_LIMIT_PERIOD);
            values.remove(SPENDING_LIMIT_START);
            values.remove(SPENDING_LIMIT_END);
        });
    }
}
