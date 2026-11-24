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

/** Leitura sequencial dos registos, com os valores por omissão das versões anteriores. */
final class BackupJsonReader {
    private BackupJsonReader() {}

    static FullBackupDocument read(String encoded) {
        try (JsonReader in = new JsonReader(new StringReader(encoded))) {
            in.setStrictness(Strictness.STRICT);
            FullBackupDocument document = readFullBackupDocument(in);
            if (in.peek() != JsonToken.END_DOCUMENT) throw invalid();
            return document;
        } catch (IOException | IllegalStateException error) {
            throw new IllegalArgumentException("Invalid backup JSON", error);
        }
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid backup field");
    }

    static String string(JsonReader in) throws IOException {
        if (in.peek() != JsonToken.STRING) throw invalid();
        return in.nextString();
    }

    static boolean takeNull(JsonReader in) throws IOException {
        if (in.peek() != JsonToken.NULL) return false;
        in.nextNull();
        return true;
    }

    static long integer(JsonReader in) throws IOException {
        String value = in.nextString();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            if (value.indexOf('e') < 0 && value.indexOf('E') < 0) throw invalid();
            try {
                return new java.math.BigDecimal(value).longValueExact();
            } catch (ArithmeticException failure) {
                throw invalid();
            }
        }
    }

    static int intValue(JsonReader in) throws IOException {
        long value = integer(in);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw invalid();
        return (int) value;
    }

    static boolean bool(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.BOOLEAN) return in.nextBoolean();
        String value = string(in);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw invalid();
    }

    static byte[] receipt(JsonReader in) throws IOException {
        if (in.peek() != JsonToken.BEGIN_ARRAY) {
            String value = takeNull(in) ? "null" : in.peek() == JsonToken.BOOLEAN
                    ? Boolean.toString(in.nextBoolean()) : in.nextString();
            if (value.length() > 1_333_336) throw invalid();
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length > 1_000_000) throw invalid();
            return bytes;
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        in.beginArray();
        while (in.hasNext()) {
            if (bytes.size() >= 1_000_000) throw invalid();
            int value = Integer.parseInt(in.nextString());
            if (value < -128 || value > 127) throw invalid();
            bytes.write(value);
        }
        in.endArray();
        return bytes.toByteArray();
    }

    private interface ItemReader<T> {
        T read(JsonReader in) throws IOException;
    }

    static <T> List<T> list(JsonReader in, ItemReader<T> reader) throws IOException {
        List<T> items = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) items.add(reader.read(in));
        in.endArray();
        return items;
    }

    static FullBackupDocument readFullBackupDocument(JsonReader in) throws IOException {
        String format = null;
        Integer formatVersion = null;
        Integer databaseVersion = null;
        Long createdAtEpochMillis = null;
        List<AccountEntity> accounts = null;
        List<AccountExchangeRateEntity> accountExchangeRates = Collections.emptyList();
        String baseCurrencyCode = null;
        List<CategoryEntity> categories = null;
        List<TransactionEntity> transactions = null;
        List<TransactionEntryEntity> transactionEntries = null;
        List<ReconciliationEntity> reconciliations = null;
        List<BudgetAllocationEntity> budgetAllocations = null;
        List<GoalEntity> goals = null;
        List<GoalContributionEntity> goalContributions = Collections.emptyList();
        List<RecurringScheduleEntity> recurringSchedules = null;
        List<DebtProfileEntity> debtProfiles = null;
        List<ReceiptAttachmentEntity> receiptAttachments = Collections.emptyList();
        List<TransactionExchangeRateEntity> transactionExchangeRates = Collections.emptyList();
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "format":
                    format = string(in);
                    break;
                case "formatVersion":
                    formatVersion = intValue(in);
                    break;
                case "databaseVersion":
                    databaseVersion = intValue(in);
                    break;
                case "createdAtEpochMillis":
                    createdAtEpochMillis = integer(in);
                    break;
                case "accounts":
                    accounts = list(in, LedgerBackupReader::readAccountEntity);
                    break;
                case "accountExchangeRates":
                    accountExchangeRates = list(in, PlanningBackupReader::readAccountExchangeRateEntity);
                    break;
                case "baseCurrencyCode":
                    baseCurrencyCode = takeNull(in) ? null : string(in);
                    break;
                case "categories":
                    categories = list(in, LedgerBackupReader::readCategoryEntity);
                    break;
                case "transactions":
                    transactions = list(in, LedgerBackupReader::readTransactionEntity);
                    break;
                case "transactionEntries":
                    transactionEntries = list(in, LedgerBackupReader::readTransactionEntryEntity);
                    break;
                case "reconciliations":
                    reconciliations = list(in, PlanningBackupReader::readReconciliationEntity);
                    break;
                case "budgetAllocations":
                    budgetAllocations = list(in, PlanningBackupReader::readBudgetAllocationEntity);
                    break;
                case "goals":
                    goals = list(in, PlanningBackupReader::readGoalEntity);
                    break;
                case "goalContributions":
                    goalContributions = list(in, PlanningBackupReader::readGoalContributionEntity);
                    break;
                case "recurringSchedules":
                    recurringSchedules = list(in, PlanningBackupReader::readRecurringScheduleEntity);
                    break;
                case "debtProfiles":
                    debtProfiles = list(in, PlanningBackupReader::readDebtProfileEntity);
                    break;
                case "receiptAttachments":
                    receiptAttachments = list(in, LedgerBackupReader::readReceiptAttachmentEntity);
                    break;
                case "transactionExchangeRates":
                    transactionExchangeRates = list(in, LedgerBackupReader::readTransactionExchangeRateEntity);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (format == null ||
                formatVersion == null ||
                databaseVersion == null ||
                createdAtEpochMillis == null ||
                accounts == null ||
                categories == null ||
                transactions == null ||
                transactionEntries == null ||
                reconciliations == null ||
                budgetAllocations == null ||
                goals == null ||
                recurringSchedules == null ||
                debtProfiles == null) throw invalid();
        return new FullBackupDocument(
                format, formatVersion, databaseVersion, createdAtEpochMillis, accounts, accountExchangeRates, baseCurrencyCode, categories, transactions, transactionEntries, reconciliations, budgetAllocations, goals, goalContributions, recurringSchedules, debtProfiles, receiptAttachments, transactionExchangeRates);
    }
}
