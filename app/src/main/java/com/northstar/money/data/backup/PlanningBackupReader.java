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

final class PlanningBackupReader {
    private PlanningBackupReader() {}

    static ReconciliationEntity readReconciliationEntity(JsonReader in) throws IOException {
        String id = null;
        String accountId = null;
        String statementLocalDate = null;
        Long statementBalanceMinor = null;
        Long calculatedBalanceMinor = null;
        Long differenceMinor = null;
        String adjustmentTransactionId = null;
        boolean hasAdjustmentTransactionId = false;
        Long completedAt = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "accountId":
                    accountId = string(in);
                    break;
                case "statementLocalDate":
                    statementLocalDate = string(in);
                    break;
                case "statementBalanceMinor":
                    statementBalanceMinor = integer(in);
                    break;
                case "calculatedBalanceMinor":
                    calculatedBalanceMinor = integer(in);
                    break;
                case "differenceMinor":
                    differenceMinor = integer(in);
                    break;
                case "adjustmentTransactionId":
                    adjustmentTransactionId = takeNull(in) ? null : string(in);
                    hasAdjustmentTransactionId = true;
                    break;
                case "completedAt":
                    completedAt = integer(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                accountId == null ||
                statementLocalDate == null ||
                statementBalanceMinor == null ||
                calculatedBalanceMinor == null ||
                differenceMinor == null ||
                !hasAdjustmentTransactionId ||
                completedAt == null) throw invalid();
        return new ReconciliationEntity(
                id, accountId, statementLocalDate, statementBalanceMinor, calculatedBalanceMinor, differenceMinor, adjustmentTransactionId, completedAt);
    }

    static BudgetAllocationEntity readBudgetAllocationEntity(JsonReader in) throws IOException {
        String id = null;
        String monthStart = null;
        String categoryId = null;
        Long plannedMinor = null;
        String currencyCode = "EUR";
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "monthStart":
                    monthStart = string(in);
                    break;
                case "categoryId":
                    categoryId = string(in);
                    break;
                case "plannedMinor":
                    plannedMinor = integer(in);
                    break;
                case "currencyCode":
                    currencyCode = string(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                monthStart == null ||
                categoryId == null ||
                plannedMinor == null) throw invalid();
        return new BudgetAllocationEntity(
                id, monthStart, categoryId, plannedMinor, currencyCode);
    }

    static GoalEntity readGoalEntity(JsonReader in) throws IOException {
        String id = null;
        String name = null;
        Long targetMinor = null;
        Long savedMinor = null;
        String currencyCode = null;
        String targetLocalDate = null;
        boolean hasTargetLocalDate = false;
        String status = null;
        Long createdAt = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "name":
                    name = string(in);
                    break;
                case "targetMinor":
                    targetMinor = integer(in);
                    break;
                case "savedMinor":
                    savedMinor = integer(in);
                    break;
                case "currencyCode":
                    currencyCode = string(in);
                    break;
                case "targetLocalDate":
                    targetLocalDate = takeNull(in) ? null : string(in);
                    hasTargetLocalDate = true;
                    break;
                case "status":
                    status = string(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                name == null ||
                targetMinor == null ||
                savedMinor == null ||
                currencyCode == null ||
                !hasTargetLocalDate ||
                status == null ||
                createdAt == null) throw invalid();
        return new GoalEntity(
                id, name, targetMinor, savedMinor, currencyCode, targetLocalDate, status, createdAt);
    }

    static GoalContributionEntity readGoalContributionEntity(JsonReader in) throws IOException {
        String id = null;
        String goalId = null;
        Long amountMinor = null;
        String localDate = null;
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
                case "goalId":
                    goalId = string(in);
                    break;
                case "amountMinor":
                    amountMinor = integer(in);
                    break;
                case "localDate":
                    localDate = string(in);
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
                goalId == null ||
                amountMinor == null ||
                localDate == null ||
                note == null ||
                createdAt == null ||
                updatedAt == null) throw invalid();
        return new GoalContributionEntity(
                id, goalId, amountMinor, localDate, note, createdAt, updatedAt, deletedAt);
    }

    static RecurringScheduleEntity readRecurringScheduleEntity(JsonReader in) throws IOException {
        String id = null;
        String name = null;
        String kind = null;
        Long amountMinor = null;
        String currencyCode = null;
        String accountId = null;
        String categoryId = null;
        boolean hasCategoryId = false;
        String frequency = null;
        Integer intervalCount = null;
        String nextLocalDate = null;
        Boolean active = null;
        Long createdAt = null;
        Long deletedAt = null;
        Integer anchorDay = null;
        Integer anchorMonth = null;
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
                case "amountMinor":
                    amountMinor = integer(in);
                    break;
                case "currencyCode":
                    currencyCode = string(in);
                    break;
                case "accountId":
                    accountId = string(in);
                    break;
                case "categoryId":
                    categoryId = takeNull(in) ? null : string(in);
                    hasCategoryId = true;
                    break;
                case "frequency":
                    frequency = string(in);
                    break;
                case "intervalCount":
                    intervalCount = intValue(in);
                    break;
                case "nextLocalDate":
                    nextLocalDate = string(in);
                    break;
                case "active":
                    active = bool(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                case "deletedAt":
                    deletedAt = takeNull(in) ? null : integer(in);
                    break;
                case "anchorDay":
                    anchorDay = intValue(in);
                    break;
                case "anchorMonth":
                    anchorMonth = intValue(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                name == null ||
                kind == null ||
                amountMinor == null ||
                currencyCode == null ||
                accountId == null ||
                !hasCategoryId ||
                frequency == null ||
                intervalCount == null ||
                nextLocalDate == null ||
                active == null ||
                createdAt == null) throw invalid();
        if (anchorDay == null) anchorDay = LocalDate.parse(nextLocalDate).getDayOfMonth();
        if (anchorMonth == null) anchorMonth = LocalDate.parse(nextLocalDate).getMonthValue();
        return new RecurringScheduleEntity(
                id, name, kind, amountMinor, currencyCode, accountId, categoryId, frequency, intervalCount, nextLocalDate, active, createdAt, deletedAt, anchorDay, anchorMonth);
    }

    static DebtProfileEntity readDebtProfileEntity(JsonReader in) throws IOException {
        String id = null;
        String accountId = null;
        Integer annualRateBasisPoints = null;
        Long minimumPaymentMinor = null;
        Integer dueDay = null;
        Long createdAt = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "id":
                    id = string(in);
                    break;
                case "accountId":
                    accountId = string(in);
                    break;
                case "annualRateBasisPoints":
                    annualRateBasisPoints = intValue(in);
                    break;
                case "minimumPaymentMinor":
                    minimumPaymentMinor = integer(in);
                    break;
                case "dueDay":
                    dueDay = intValue(in);
                    break;
                case "createdAt":
                    createdAt = integer(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (id == null ||
                accountId == null ||
                annualRateBasisPoints == null ||
                minimumPaymentMinor == null ||
                dueDay == null ||
                createdAt == null) throw invalid();
        return new DebtProfileEntity(
                id, accountId, annualRateBasisPoints, minimumPaymentMinor, dueDay, createdAt);
    }

    static AccountExchangeRateEntity readAccountExchangeRateEntity(JsonReader in) throws IOException {
        String accountId = null;
        String baseCurrencyCode = null;
        String quoteCurrencyCode = null;
        Long openingBalanceMinor = null;
        Long convertedAmountMinor = null;
        Long rateMicros = null;
        String rateLocalDate = null;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "accountId":
                    accountId = string(in);
                    break;
                case "baseCurrencyCode":
                    baseCurrencyCode = string(in);
                    break;
                case "quoteCurrencyCode":
                    quoteCurrencyCode = string(in);
                    break;
                case "openingBalanceMinor":
                    openingBalanceMinor = integer(in);
                    break;
                case "convertedAmountMinor":
                    convertedAmountMinor = integer(in);
                    break;
                case "rateMicros":
                    rateMicros = integer(in);
                    break;
                case "rateLocalDate":
                    rateLocalDate = string(in);
                    break;
                default: throw invalid();
            }
        }
        in.endObject();
        if (accountId == null ||
                baseCurrencyCode == null ||
                quoteCurrencyCode == null ||
                openingBalanceMinor == null ||
                convertedAmountMinor == null ||
                rateMicros == null ||
                rateLocalDate == null) throw invalid();
        return new AccountExchangeRateEntity(
                accountId, baseCurrencyCode, quoteCurrencyCode, openingBalanceMinor, convertedAmountMinor, rateMicros, rateLocalDate);
    }

}
