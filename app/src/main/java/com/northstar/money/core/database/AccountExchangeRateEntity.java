package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "account_exchange_rates", foreignKeys = @ForeignKey(
        entity = AccountEntity.class, parentColumns = "id", childColumns = "accountId",
        onDelete = ForeignKey.CASCADE))
public final class AccountExchangeRateEntity {
    @PrimaryKey @NonNull private final String accountId;
    @NonNull private final String baseCurrencyCode;
    @NonNull private final String quoteCurrencyCode;
    private final long openingBalanceMinor;
    private final long convertedAmountMinor;
    private final long rateMicros;
    @NonNull private final String rateLocalDate;

    public AccountExchangeRateEntity(@NonNull String accountId, @NonNull String baseCurrencyCode,
            @NonNull String quoteCurrencyCode, long openingBalanceMinor, long convertedAmountMinor,
            long rateMicros, @NonNull String rateLocalDate) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.baseCurrencyCode = Objects.requireNonNull(baseCurrencyCode, "baseCurrencyCode");
        this.quoteCurrencyCode = Objects.requireNonNull(quoteCurrencyCode, "quoteCurrencyCode");
        this.openingBalanceMinor = openingBalanceMinor;
        this.convertedAmountMinor = convertedAmountMinor;
        this.rateMicros = rateMicros;
        this.rateLocalDate = Objects.requireNonNull(rateLocalDate, "rateLocalDate");
    }

    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getBaseCurrencyCode() { return baseCurrencyCode; }
    @NonNull public String getQuoteCurrencyCode() { return quoteCurrencyCode; }
    public long getOpeningBalanceMinor() { return openingBalanceMinor; }
    public long getConvertedAmountMinor() { return convertedAmountMinor; }
    public long getRateMicros() { return rateMicros; }
    @NonNull public String getRateLocalDate() { return rateLocalDate; }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof AccountExchangeRateEntity)) return false;
        AccountExchangeRateEntity that = (AccountExchangeRateEntity) other;
        return accountId.equals(that.accountId)
                && baseCurrencyCode.equals(that.baseCurrencyCode)
                && quoteCurrencyCode.equals(that.quoteCurrencyCode)
                && openingBalanceMinor == that.openingBalanceMinor
                && convertedAmountMinor == that.convertedAmountMinor
                && rateMicros == that.rateMicros
                && rateLocalDate.equals(that.rateLocalDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, baseCurrencyCode, quoteCurrencyCode, openingBalanceMinor,
                convertedAmountMinor, rateMicros, rateLocalDate);
    }

    @NonNull
    @Override
    public String toString() {
        return "AccountExchangeRateEntity(accountId=" + accountId
                + ", baseCurrencyCode=" + baseCurrencyCode + ", quoteCurrencyCode=" + quoteCurrencyCode
                + ", openingBalanceMinor=" + openingBalanceMinor + ", convertedAmountMinor=" + convertedAmountMinor
                + ", rateMicros=" + rateMicros + ", rateLocalDate=" + rateLocalDate + ")";
    }
}
