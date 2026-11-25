package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Objects;

@Entity(tableName = "transaction_exchange_rates", foreignKeys = {
        @ForeignKey(entity = TransactionEntity.class, parentColumns = "id", childColumns = "transactionId",
                onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = TransactionEntryEntity.class, parentColumns = "id", childColumns = "entryId",
                onDelete = ForeignKey.CASCADE)
}, indices = {@Index("transactionId"), @Index(value = "entryId", unique = true), @Index("rateLocalDate")})
public final class TransactionExchangeRateEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String transactionId;
    @NonNull private final String entryId;
    @NonNull private final String baseCurrencyCode;
    @NonNull private final String quoteCurrencyCode;
    @Nullable private final Long rateMicros;
    @Nullable private final Long convertedAmountMinor;
    @NonNull private final String rateLocalDate;
    @NonNull private final String source;
    @NonNull private final String status;
    @Nullable private final Long fetchedAt;

    public TransactionExchangeRateEntity(@NonNull String id, @NonNull String transactionId, @NonNull String entryId,
            @NonNull String baseCurrencyCode, @NonNull String quoteCurrencyCode, @Nullable Long rateMicros,
            @Nullable Long convertedAmountMinor, @NonNull String rateLocalDate, @NonNull String source,
            @NonNull String status, @Nullable Long fetchedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.baseCurrencyCode = Objects.requireNonNull(baseCurrencyCode, "baseCurrencyCode");
        this.quoteCurrencyCode = Objects.requireNonNull(quoteCurrencyCode, "quoteCurrencyCode");
        this.rateMicros = rateMicros;
        this.convertedAmountMinor = convertedAmountMinor;
        this.rateLocalDate = Objects.requireNonNull(rateLocalDate, "rateLocalDate");
        this.source = Objects.requireNonNull(source, "source");
        this.status = Objects.requireNonNull(status, "status");
        this.fetchedAt = fetchedAt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getTransactionId() { return transactionId; }
    @NonNull public String getEntryId() { return entryId; }
    @NonNull public String getBaseCurrencyCode() { return baseCurrencyCode; }
    @NonNull public String getQuoteCurrencyCode() { return quoteCurrencyCode; }
    @Nullable public Long getRateMicros() { return rateMicros; }
    @Nullable public Long getConvertedAmountMinor() { return convertedAmountMinor; }
    @NonNull public String getRateLocalDate() { return rateLocalDate; }
    @NonNull public String getSource() { return source; }
    @NonNull public String getStatus() { return status; }
    @Nullable public Long getFetchedAt() { return fetchedAt; }

    @NonNull
    public TransactionExchangeRateEntity asPending(@NonNull String transactionId, @NonNull String baseCurrencyCode,
            @NonNull String rateLocalDate) {
        return new TransactionExchangeRateEntity(id, transactionId, entryId, baseCurrencyCode, quoteCurrencyCode,
                null, null, rateLocalDate, source, "PENDING", null);
    }

    @NonNull
    public TransactionExchangeRateEntity withQuote(long rateMicros, long convertedAmountMinor,
            @NonNull String source, long fetchedAt) {
        return new TransactionExchangeRateEntity(id, transactionId, entryId, baseCurrencyCode, quoteCurrencyCode,
                rateMicros, convertedAmountMinor, rateLocalDate, source, "AVAILABLE", fetchedAt);
    }

    @NonNull
    public TransactionExchangeRateEntity withConvertedAmount(long convertedAmountMinor) {
        return new TransactionExchangeRateEntity(id, transactionId, entryId, baseCurrencyCode, quoteCurrencyCode,
                rateMicros, convertedAmountMinor, rateLocalDate, source, status, fetchedAt);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionExchangeRateEntity)) return false;
        TransactionExchangeRateEntity that = (TransactionExchangeRateEntity) other;
        return id.equals(that.id)
                && transactionId.equals(that.transactionId)
                && entryId.equals(that.entryId)
                && baseCurrencyCode.equals(that.baseCurrencyCode)
                && quoteCurrencyCode.equals(that.quoteCurrencyCode)
                && Objects.equals(rateMicros, that.rateMicros)
                && Objects.equals(convertedAmountMinor, that.convertedAmountMinor)
                && rateLocalDate.equals(that.rateLocalDate)
                && source.equals(that.source)
                && status.equals(that.status)
                && Objects.equals(fetchedAt, that.fetchedAt);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + transactionId.hashCode();
        result = 31 * result + entryId.hashCode();
        result = 31 * result + baseCurrencyCode.hashCode();
        result = 31 * result + quoteCurrencyCode.hashCode();
        result = 31 * result + Objects.hashCode(rateMicros);
        result = 31 * result + Objects.hashCode(convertedAmountMinor);
        result = 31 * result + rateLocalDate.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + Objects.hashCode(fetchedAt);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "TransactionExchangeRateEntity(id=" + id
                + ", transactionId=" + transactionId + ", entryId=" + entryId
                + ", baseCurrencyCode=" + baseCurrencyCode + ", quoteCurrencyCode=" + quoteCurrencyCode
                + ", rateMicros=" + rateMicros + ", convertedAmountMinor=" + convertedAmountMinor
                + ", rateLocalDate=" + rateLocalDate + ", source=" + source
                + ", status=" + status + ", fetchedAt=" + fetchedAt + ")";
    }
}
