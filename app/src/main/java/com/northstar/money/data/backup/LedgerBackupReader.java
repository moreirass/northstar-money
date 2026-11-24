package com.northstar.money.data.backup;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.northstar.money.core.database.*;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static com.northstar.money.data.backup.BackupJsonReader.*;

final class LedgerBackupReader {
    private LedgerBackupReader() {}

    static AccountEntity readAccountEntity(JsonReader in) throws IOException {
        String id = null;
        String name = null;
        String type = null;
        String currencyCode = null;
        Long openingBalanceMinor = null;
        Long archivedAt = null;
        Long createdAt = null;
        Long updatedAt = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "name":
                    name = string(in);
                    break;
                case "type":
                    type = string(in);
                    break;
                case "currencyCode":
                    currencyCode = string(in);
                    break;
                case "openingBalanceMinor":
                    openingBalanceMinor = integer(in);
                    break;
                case "archivedAt":
                    archivedAt = takeNull(in) ? null : integer(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                case "updatedAt":
                    updatedAt = integer(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                name == null ||
                type == null ||
                currencyCode == null ||
                openingBalanceMinor == null ||
                createdAt == null ||
                updatedAt == null) throw invalid();
        return new AccountEntity(
                id, name, type, currencyCode, openingBalanceMinor, archivedAt, createdAt, updatedAt);
    }

    static CategoryEntity readCategoryEntity(JsonReader in) throws IOException {
        String id = null;
        String name = null;
        String kind = null;
        Integer sortOrder = null;
        Long archivedAt = null;
        String mergedIntoCategoryId = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "name":
                    name = string(in);
                    break;
                case "kind":
                    kind = string(in);
                    break;
                case "sortOrder":
                    sortOrder = intValue(in);
                    break;
                case "archivedAt":
                    archivedAt = takeNull(in) ? null : integer(in);
                    break;
                case "mergedIntoCategoryId":
                    mergedIntoCategoryId = takeNull(in) ? null : string(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                name == null ||
                kind == null ||
                sortOrder == null) throw invalid();
        return new CategoryEntity(
                id, name, kind, sortOrder, archivedAt, mergedIntoCategoryId);
    }

    static TransactionEntity readTransactionEntity(JsonReader in) throws IOException {
        String id = null;
        String kind = null;
        String localDate = null;
        String payee = null;
        String note = null;
        Long createdAt = null;
        Long updatedAt = null;
        Long deletedAt = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "kind":
                    kind = string(in);
                    break;
                case "localDate":
                    localDate = string(in);
                    break;
                case "payee":
                    payee = string(in);
                    break;
                case "note":
                    note = string(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                case "updatedAt":
                    updatedAt = integer(in);
                    break;
                case "deletedAt":
                    deletedAt = takeNull(in) ? null : integer(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                kind == null ||
                localDate == null ||
                payee == null ||
                note == null ||
                createdAt == null ||
                updatedAt == null) throw invalid();
        return new TransactionEntity(
                id, kind, localDate, payee, note, createdAt, updatedAt, deletedAt);
    }

    static TransactionEntryEntity readTransactionEntryEntity(JsonReader in) throws IOException {
        String id = null;
        String transactionId = null;
        String accountId = null;
        String categoryId = null;
        boolean hasCategoryId = false;
        Long amountMinor = null;
        String currencyCode = null;
        Boolean cleared = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "transactionId":
                    transactionId = string(in);
                    break;
                case "accountId":
                    accountId = string(in);
                    break;
                case "categoryId":
                    categoryId = takeNull(in) ? null : string(in);
                    hasCategoryId = true;
                    break;
                case "amountMinor":
                    amountMinor = integer(in);
                    break;
                case "currencyCode":
                    currencyCode = string(in);
                    break;
                case "cleared":
                    cleared = bool(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                transactionId == null ||
                accountId == null ||
                !hasCategoryId ||
                amountMinor == null ||
                currencyCode == null ||
                cleared == null) throw invalid();
        return new TransactionEntryEntity(
                id, transactionId, accountId, categoryId, amountMinor, currencyCode, cleared);
    }

    static ReceiptAttachmentEntity readReceiptAttachmentEntity(JsonReader in) throws IOException {
        String id = null;
        String transactionId = null;
        byte[] content = null;
        String originalName = null;
        String mimeType = null;
        Long byteSize = null;
        Long createdAt = null;
        String ocrStatus = "PENDING";
        String ocrText = null;
        Long detectedAmountMinor = null;
        String detectedCurrencyCode = null;
        String detectedLocalDate = null;
        String detectedMerchant = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "transactionId":
                    transactionId = string(in);
                    break;
                case "content":
                    content = receipt(in);
                    break;
                case "originalName":
                    originalName = string(in);
                    break;
                case "mimeType":
                    mimeType = string(in);
                    break;
                case "byteSize":
                    byteSize = integer(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                case "ocrStatus":
                    ocrStatus = string(in);
                    break;
                case "ocrText":
                    ocrText = takeNull(in) ? null : string(in);
                    break;
                case "detectedAmountMinor":
                    detectedAmountMinor = takeNull(in) ? null : integer(in);
                    break;
                case "detectedCurrencyCode":
                    detectedCurrencyCode = takeNull(in) ? null : string(in);
                    break;
                case "detectedLocalDate":
                    detectedLocalDate = takeNull(in) ? null : string(in);
                    break;
                case "detectedMerchant":
                    detectedMerchant = takeNull(in) ? null : string(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                transactionId == null ||
                content == null ||
                originalName == null ||
                mimeType == null ||
                byteSize == null ||
                createdAt == null) throw invalid();
        return new ReceiptAttachmentEntity(
                id, transactionId, content, originalName, mimeType, byteSize, createdAt, ocrStatus, ocrText, detectedAmountMinor, detectedCurrencyCode, detectedLocalDate, detectedMerchant);
    }

    static TransactionExchangeRateEntity readTransactionExchangeRateEntity(JsonReader in) throws IOException {
        String id = null;
        String transactionId = null;
        String entryId = null;
        String baseCurrencyCode = null;
        String quoteCurrencyCode = null;
        Long rateMicros = null;
        boolean hasRateMicros = false;
        Long convertedAmountMinor = null;
        boolean hasConvertedAmountMinor = false;
        String rateLocalDate = null;
        String source = null;
        String status = null;
        Long fetchedAt = null;
        boolean hasFetchedAt = false;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "transactionId":
                    transactionId = string(in);
                    break;
                case "entryId":
                    entryId = string(in);
                    break;
                case "baseCurrencyCode":
                    baseCurrencyCode = string(in);
                    break;
                case "quoteCurrencyCode":
                    quoteCurrencyCode = string(in);
                    break;
                case "rateMicros":
                    rateMicros = takeNull(in) ? null : integer(in);
                    hasRateMicros = true;
                    break;
                case "convertedAmountMinor":
                    convertedAmountMinor = takeNull(in) ? null : integer(in);
                    hasConvertedAmountMinor = true;
                    break;
                case "rateLocalDate":
                    rateLocalDate = string(in);
                    break;
                case "source":
                    source = string(in);
                    break;
                case "status":
                    status = string(in);
                    break;
                case "fetchedAt":
                    fetchedAt = takeNull(in) ? null : integer(in);
                    hasFetchedAt = true;
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                transactionId == null ||
                entryId == null ||
                baseCurrencyCode == null ||
                quoteCurrencyCode == null ||
                !hasRateMicros ||
                !hasConvertedAmountMinor ||
                rateLocalDate == null ||
                source == null ||
                status == null ||
                !hasFetchedAt) throw invalid();
        return new TransactionExchangeRateEntity(
                id, transactionId, entryId, baseCurrencyCode, quoteCurrencyCode, rateMicros, convertedAmountMinor, rateLocalDate, source, status, fetchedAt);
    }

}
