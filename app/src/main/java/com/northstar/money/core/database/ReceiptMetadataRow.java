package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/** Receipt projection without image bytes or raw OCR text. */
public final class ReceiptMetadataRow {
    @NonNull private final String id;
    @NonNull private final String transactionId;
    @NonNull private final String originalName;
    @NonNull private final String mimeType;
    private final long byteSize;
    private final long createdAt;
    @NonNull private final String ocrStatus;
    @Nullable private final Long detectedAmountMinor;
    @Nullable private final String detectedCurrencyCode;
    @Nullable private final String detectedLocalDate;
    @Nullable private final String detectedMerchant;

    public ReceiptMetadataRow(@NonNull String id, @NonNull String transactionId, @NonNull String originalName,
            @NonNull String mimeType, long byteSize, long createdAt,
            @NonNull String ocrStatus, @Nullable Long detectedAmountMinor, @Nullable String detectedCurrencyCode,
            @Nullable String detectedLocalDate, @Nullable String detectedMerchant) {
        this.id = Objects.requireNonNull(id, "id");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.byteSize = byteSize;
        this.createdAt = createdAt;
        this.ocrStatus = Objects.requireNonNull(ocrStatus, "ocrStatus");
        this.detectedAmountMinor = detectedAmountMinor;
        this.detectedCurrencyCode = detectedCurrencyCode;
        this.detectedLocalDate = detectedLocalDate;
        this.detectedMerchant = detectedMerchant;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getTransactionId() { return transactionId; }
    @NonNull public String getOriginalName() { return originalName; }
    @NonNull public String getMimeType() { return mimeType; }
    public long getByteSize() { return byteSize; }
    public long getCreatedAt() { return createdAt; }
    @NonNull public String getOcrStatus() { return ocrStatus; }
    @Nullable public Long getDetectedAmountMinor() { return detectedAmountMinor; }
    @Nullable public String getDetectedCurrencyCode() { return detectedCurrencyCode; }
    @Nullable public String getDetectedLocalDate() { return detectedLocalDate; }
    @Nullable public String getDetectedMerchant() { return detectedMerchant; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReceiptMetadataRow)) return false;
        ReceiptMetadataRow that = (ReceiptMetadataRow) other;
        return Objects.equals(id, that.id)
                && Objects.equals(transactionId, that.transactionId)
                && Objects.equals(originalName, that.originalName)
                && Objects.equals(mimeType, that.mimeType)
                && byteSize == that.byteSize
                && createdAt == that.createdAt
                && Objects.equals(ocrStatus, that.ocrStatus)
                && Objects.equals(detectedAmountMinor, that.detectedAmountMinor)
                && Objects.equals(detectedCurrencyCode, that.detectedCurrencyCode)
                && Objects.equals(detectedLocalDate, that.detectedLocalDate)
                && Objects.equals(detectedMerchant, that.detectedMerchant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId, originalName, mimeType, byteSize,
            createdAt, ocrStatus, detectedAmountMinor, detectedCurrencyCode, detectedLocalDate,
            detectedMerchant);
    }
}
