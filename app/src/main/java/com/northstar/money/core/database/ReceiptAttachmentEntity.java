package com.northstar.money.core.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.Arrays;
import java.util.Objects;

@Entity(
    tableName = "receipt_attachments",
    foreignKeys = {@ForeignKey(
        entity = TransactionEntity.class,
        parentColumns = {"id"},
        childColumns = {"transactionId"},
        onDelete = ForeignKey.CASCADE)
    },
    indices = {@Index("transactionId")})
public final class ReceiptAttachmentEntity {
    @PrimaryKey @NonNull private final String id;
    @NonNull private final String transactionId;
    @NonNull private final byte[] content;
    @NonNull private final String originalName;
    @NonNull private final String mimeType;
    private final long byteSize;
    private final long createdAt;
    @NonNull private final String ocrStatus;
    @Nullable private final String ocrText;
    @Nullable private final Long detectedAmountMinor;
    @Nullable private final String detectedCurrencyCode;
    @Nullable private final String detectedLocalDate;
    @Nullable private final String detectedMerchant;

    public ReceiptAttachmentEntity(@NonNull String id, @NonNull String transactionId, @NonNull byte[] content,
            @NonNull String originalName, @NonNull String mimeType, long byteSize,
            long createdAt, @NonNull String ocrStatus, @Nullable String ocrText,
            @Nullable Long detectedAmountMinor, @Nullable String detectedCurrencyCode, @Nullable String detectedLocalDate,
            @Nullable String detectedMerchant) {
        this.id = Objects.requireNonNull(id, "id");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.content = Objects.requireNonNull(content, "content");
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.byteSize = byteSize;
        this.createdAt = createdAt;
        this.ocrStatus = Objects.requireNonNull(ocrStatus, "ocrStatus");
        this.ocrText = ocrText;
        this.detectedAmountMinor = detectedAmountMinor;
        this.detectedCurrencyCode = detectedCurrencyCode;
        this.detectedLocalDate = detectedLocalDate;
        this.detectedMerchant = detectedMerchant;
    }

    @Ignore
    public ReceiptAttachmentEntity(@NonNull String id, @NonNull String transactionId, @NonNull byte[] content,
            @NonNull String originalName, @NonNull String mimeType, long byteSize,
            long createdAt) {
        this(id, transactionId, content, originalName, mimeType,
            byteSize, createdAt, "PENDING", null, null, null, null, null);
    }

    @NonNull
    public ReceiptAttachmentEntity withOcrStatus(@NonNull String ocrStatus) {
        return new ReceiptAttachmentEntity(id, transactionId, content, originalName, mimeType,
            byteSize, createdAt, ocrStatus, ocrText, detectedAmountMinor,
            detectedCurrencyCode, detectedLocalDate, detectedMerchant);
    }

    @NonNull
    public ReceiptAttachmentEntity withOcrResult(@NonNull String ocrStatus, @Nullable String ocrText, @Nullable Long detectedAmountMinor,
            @Nullable String detectedCurrencyCode, @Nullable String detectedLocalDate, @Nullable String detectedMerchant) {
        return new ReceiptAttachmentEntity(id, transactionId, content, originalName, mimeType,
            byteSize, createdAt, ocrStatus, ocrText, detectedAmountMinor,
            detectedCurrencyCode, detectedLocalDate, detectedMerchant);
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getTransactionId() { return transactionId; }
    @NonNull public byte[] getContent() { return content; }
    @NonNull public String getOriginalName() { return originalName; }
    @NonNull public String getMimeType() { return mimeType; }
    public long getByteSize() { return byteSize; }
    public long getCreatedAt() { return createdAt; }
    @NonNull public String getOcrStatus() { return ocrStatus; }
    @Nullable public String getOcrText() { return ocrText; }
    @Nullable public Long getDetectedAmountMinor() { return detectedAmountMinor; }
    @Nullable public String getDetectedCurrencyCode() { return detectedCurrencyCode; }
    @Nullable public String getDetectedLocalDate() { return detectedLocalDate; }
    @Nullable public String getDetectedMerchant() { return detectedMerchant; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReceiptAttachmentEntity)) return false;
        ReceiptAttachmentEntity that = (ReceiptAttachmentEntity) other;
        return Objects.equals(id, that.id)
                && Objects.equals(transactionId, that.transactionId)
                && Arrays.equals(content, that.content)
                && Objects.equals(originalName, that.originalName)
                && Objects.equals(mimeType, that.mimeType)
                && byteSize == that.byteSize
                && createdAt == that.createdAt
                && Objects.equals(ocrStatus, that.ocrStatus)
                && Objects.equals(ocrText, that.ocrText)
                && Objects.equals(detectedAmountMinor, that.detectedAmountMinor)
                && Objects.equals(detectedCurrencyCode, that.detectedCurrencyCode)
                && Objects.equals(detectedLocalDate, that.detectedLocalDate)
                && Objects.equals(detectedMerchant, that.detectedMerchant);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(content);
    }
}
