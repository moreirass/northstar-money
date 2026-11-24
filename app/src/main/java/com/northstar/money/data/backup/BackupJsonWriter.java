package com.northstar.money.data.backup;

import com.google.gson.stream.JsonWriter;
import com.northstar.money.core.database.*;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Base64;

/** A ordem dos campos e os nulos explícitos fazem parte do formato portátil. */
final class BackupJsonWriter {
    private BackupJsonWriter() {}

    static void write(Writer destination, FullBackupDocument document) throws IOException {
        JsonWriter out = new JsonWriter(destination);
        out.setSerializeNulls(true);
        write(out, document);
        out.flush();
    }

    private static void string(JsonWriter out, String value) throws IOException {
        if (value == null || (value.indexOf('\u2028') < 0 && value.indexOf('\u2029') < 0)) {
            out.value(value);
            return;
        }
        // Gson escapa estes separadores para JavaScript. O formato anterior escreve-os literalmente.
        StringWriter buffer = new StringWriter();
        new JsonWriter(buffer).value(value);
        String escaped = buffer.toString();
        StringBuilder compatible = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\') {
                if (escaped.startsWith("\\u2028", i) || escaped.startsWith("\\u2029", i)) {
                    compatible.append(escaped.charAt(i + 5) == '8' ? '\u2028' : '\u2029');
                    i += 5;
                    continue;
                }
                compatible.append(c);
                c = escaped.charAt(++i);
            }
            compatible.append(c);
        }
        out.jsonValue(compatible.toString());
    }

    private static void write(JsonWriter out, AccountEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("name"), row.getName());
        string(out.name("type"), row.getType());
        string(out.name("currencyCode"), row.getCurrencyCode());
        out.name("openingBalanceMinor").value(row.getOpeningBalanceMinor());
        out.name("archivedAt").value(row.getArchivedAt());
        out.name("createdAt").value(row.getCreatedAt());
        out.name("updatedAt").value(row.getUpdatedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, CategoryEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("name"), row.getName());
        string(out.name("kind"), row.getKind());
        out.name("sortOrder").value(row.getSortOrder());
        out.name("archivedAt").value(row.getArchivedAt());
        string(out.name("mergedIntoCategoryId"), row.getMergedIntoCategoryId());
        out.endObject();
    }

    private static void write(JsonWriter out, TransactionEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("kind"), row.getKind());
        string(out.name("localDate"), row.getLocalDate());
        string(out.name("payee"), row.getPayee());
        string(out.name("note"), row.getNote());
        out.name("createdAt").value(row.getCreatedAt());
        out.name("updatedAt").value(row.getUpdatedAt());
        out.name("deletedAt").value(row.getDeletedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, TransactionEntryEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("transactionId"), row.getTransactionId());
        string(out.name("accountId"), row.getAccountId());
        string(out.name("categoryId"), row.getCategoryId());
        out.name("amountMinor").value(row.getAmountMinor());
        string(out.name("currencyCode"), row.getCurrencyCode());
        out.name("cleared").value(row.getCleared());
        out.endObject();
    }

    private static void write(JsonWriter out, ReceiptAttachmentEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("transactionId"), row.getTransactionId());
        out.name("content").value(Base64.getEncoder().encodeToString(row.getContent()));
        string(out.name("originalName"), row.getOriginalName());
        string(out.name("mimeType"), row.getMimeType());
        out.name("byteSize").value(row.getByteSize());
        out.name("createdAt").value(row.getCreatedAt());
        string(out.name("ocrStatus"), row.getOcrStatus());
        string(out.name("ocrText"), row.getOcrText());
        out.name("detectedAmountMinor").value(row.getDetectedAmountMinor());
        string(out.name("detectedCurrencyCode"), row.getDetectedCurrencyCode());
        string(out.name("detectedLocalDate"), row.getDetectedLocalDate());
        string(out.name("detectedMerchant"), row.getDetectedMerchant());
        out.endObject();
    }

    private static void write(JsonWriter out, TransactionExchangeRateEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("transactionId"), row.getTransactionId());
        string(out.name("entryId"), row.getEntryId());
        string(out.name("baseCurrencyCode"), row.getBaseCurrencyCode());
        string(out.name("quoteCurrencyCode"), row.getQuoteCurrencyCode());
        out.name("rateMicros").value(row.getRateMicros());
        out.name("convertedAmountMinor").value(row.getConvertedAmountMinor());
        string(out.name("rateLocalDate"), row.getRateLocalDate());
        string(out.name("source"), row.getSource());
        string(out.name("status"), row.getStatus());
        out.name("fetchedAt").value(row.getFetchedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, ReconciliationEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("accountId"), row.getAccountId());
        string(out.name("statementLocalDate"), row.getStatementLocalDate());
        out.name("statementBalanceMinor").value(row.getStatementBalanceMinor());
        out.name("calculatedBalanceMinor").value(row.getCalculatedBalanceMinor());
        out.name("differenceMinor").value(row.getDifferenceMinor());
        string(out.name("adjustmentTransactionId"), row.getAdjustmentTransactionId());
        out.name("completedAt").value(row.getCompletedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, BudgetAllocationEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("monthStart"), row.getMonthStart());
        string(out.name("categoryId"), row.getCategoryId());
        out.name("plannedMinor").value(row.getPlannedMinor());
        string(out.name("currencyCode"), row.getCurrencyCode());
        out.endObject();
    }

    private static void write(JsonWriter out, GoalEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("name"), row.getName());
        out.name("targetMinor").value(row.getTargetMinor());
        out.name("savedMinor").value(row.getSavedMinor());
        string(out.name("currencyCode"), row.getCurrencyCode());
        string(out.name("targetLocalDate"), row.getTargetLocalDate());
        string(out.name("status"), row.getStatus());
        out.name("createdAt").value(row.getCreatedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, GoalContributionEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("goalId"), row.getGoalId());
        out.name("amountMinor").value(row.getAmountMinor());
        string(out.name("localDate"), row.getLocalDate());
        string(out.name("note"), row.getNote());
        out.name("createdAt").value(row.getCreatedAt());
        out.name("updatedAt").value(row.getUpdatedAt());
        out.name("deletedAt").value(row.getDeletedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, RecurringScheduleEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("name"), row.getName());
        string(out.name("kind"), row.getKind());
        out.name("amountMinor").value(row.getAmountMinor());
        string(out.name("currencyCode"), row.getCurrencyCode());
        string(out.name("accountId"), row.getAccountId());
        string(out.name("categoryId"), row.getCategoryId());
        string(out.name("frequency"), row.getFrequency());
        out.name("intervalCount").value(row.getIntervalCount());
        string(out.name("nextLocalDate"), row.getNextLocalDate());
        out.name("active").value(row.getActive());
        out.name("createdAt").value(row.getCreatedAt());
        out.name("deletedAt").value(row.getDeletedAt());
        out.name("anchorDay").value(row.getAnchorDay());
        out.name("anchorMonth").value(row.getAnchorMonth());
        out.endObject();
    }

    private static void write(JsonWriter out, DebtProfileEntity row) throws IOException {
        out.beginObject();
        string(out.name("id"), row.getId());
        string(out.name("accountId"), row.getAccountId());
        out.name("annualRateBasisPoints").value(row.getAnnualRateBasisPoints());
        out.name("minimumPaymentMinor").value(row.getMinimumPaymentMinor());
        out.name("dueDay").value(row.getDueDay());
        out.name("createdAt").value(row.getCreatedAt());
        out.endObject();
    }

    private static void write(JsonWriter out, AccountExchangeRateEntity row) throws IOException {
        out.beginObject();
        string(out.name("accountId"), row.getAccountId());
        string(out.name("baseCurrencyCode"), row.getBaseCurrencyCode());
        string(out.name("quoteCurrencyCode"), row.getQuoteCurrencyCode());
        out.name("openingBalanceMinor").value(row.getOpeningBalanceMinor());
        out.name("convertedAmountMinor").value(row.getConvertedAmountMinor());
        out.name("rateMicros").value(row.getRateMicros());
        string(out.name("rateLocalDate"), row.getRateLocalDate());
        out.endObject();
    }

    private static void write(JsonWriter out, FullBackupDocument row) throws IOException {
        out.beginObject();
        string(out.name("format"), row.getFormat());
        out.name("formatVersion").value(row.getFormatVersion());
        out.name("databaseVersion").value(row.getDatabaseVersion());
        out.name("createdAtEpochMillis").value(row.getCreatedAtEpochMillis());
        out.name("accounts").beginArray();
        for (AccountEntity item : row.getAccounts()) write(out, item);
        out.endArray();
        out.name("accountExchangeRates").beginArray();
        for (AccountExchangeRateEntity item : row.getAccountExchangeRates()) write(out, item);
        out.endArray();
        string(out.name("baseCurrencyCode"), row.getBaseCurrencyCode());
        out.name("categories").beginArray();
        for (CategoryEntity item : row.getCategories()) write(out, item);
        out.endArray();
        out.name("transactions").beginArray();
        for (TransactionEntity item : row.getTransactions()) write(out, item);
        out.endArray();
        out.name("transactionEntries").beginArray();
        for (TransactionEntryEntity item : row.getTransactionEntries()) write(out, item);
        out.endArray();
        out.name("reconciliations").beginArray();
        for (ReconciliationEntity item : row.getReconciliations()) write(out, item);
        out.endArray();
        out.name("budgetAllocations").beginArray();
        for (BudgetAllocationEntity item : row.getBudgetAllocations()) write(out, item);
        out.endArray();
        out.name("goals").beginArray();
        for (GoalEntity item : row.getGoals()) write(out, item);
        out.endArray();
        out.name("goalContributions").beginArray();
        for (GoalContributionEntity item : row.getGoalContributions()) write(out, item);
        out.endArray();
        out.name("recurringSchedules").beginArray();
        for (RecurringScheduleEntity item : row.getRecurringSchedules()) write(out, item);
        out.endArray();
        out.name("debtProfiles").beginArray();
        for (DebtProfileEntity item : row.getDebtProfiles()) write(out, item);
        out.endArray();
        out.name("receiptAttachments").beginArray();
        for (ReceiptAttachmentEntity item : row.getReceiptAttachments()) write(out, item);
        out.endArray();
        out.name("transactionExchangeRates").beginArray();
        for (TransactionExchangeRateEntity item : row.getTransactionExchangeRates()) write(out, item);
        out.endArray();
        out.endObject();
    }
}
