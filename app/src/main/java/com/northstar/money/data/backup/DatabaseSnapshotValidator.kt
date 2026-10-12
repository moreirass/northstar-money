package com.northstar.money.data.backup

import com.northstar.money.core.database.DatabaseSnapshot
import java.time.LocalDate
import com.northstar.money.domain.model.validCurrency
import com.northstar.money.data.exchange.HistoricalRateClient

class DatabaseSnapshotValidator {
    fun validate(snapshot: DatabaseSnapshot) {
        val recordCount = listOf(snapshot.accounts.size, snapshot.categories.size, snapshot.transactions.size,
            snapshot.transactionEntries.size, snapshot.receiptAttachments.size, snapshot.transactionExchangeRates.size,
            snapshot.goals.size, snapshot.goalContributions.size, snapshot.recurringSchedules.size,
            snapshot.budgetAllocations.size, snapshot.reconciliations.size, snapshot.debtProfiles.size).sum()
        require(recordCount <= 500_000) { "Backup contains too many records" }
        require(snapshot.receiptAttachments.sumOf { it.byteSize } <= 32L * 1024 * 1024) { "Backup receipts are too large" }
        requireUniqueIds("accounts", snapshot.accounts.map { it.id })
        requireUniqueIds("categories", snapshot.categories.map { it.id })
        requireUniqueIds("transactions", snapshot.transactions.map { it.id })
        requireUniqueIds("transaction entries", snapshot.transactionEntries.map { it.id })
        requireUniqueIds("reconciliations", snapshot.reconciliations.map { it.id })
        requireUniqueIds("budget allocations", snapshot.budgetAllocations.map { it.id })
        requireUniqueIds("goals", snapshot.goals.map { it.id })
        requireUniqueIds("goal contributions", snapshot.goalContributions.map { it.id })
        requireUniqueIds("recurring schedules", snapshot.recurringSchedules.map { it.id })
        requireUniqueIds("debt profiles", snapshot.debtProfiles.map { it.id })
        requireUniqueIds("receipt attachments", snapshot.receiptAttachments.map { it.id })
        requireUniqueIds("transaction exchange rates", snapshot.transactionExchangeRates.map { it.id })

        val accountIds = snapshot.accounts.mapTo(mutableSetOf()) { it.id }
        val accountCurrencies = snapshot.accounts.associate { it.id to it.currencyCode }
        val categoryIds = snapshot.categories.mapTo(mutableSetOf()) { it.id }
        val categoriesById = snapshot.categories.associateBy { it.id }
        val transactionIds = snapshot.transactions.mapTo(mutableSetOf()) { it.id }
        val transactionsById = snapshot.transactions.associateBy { it.id }
        val entriesByTransaction = snapshot.transactionEntries.groupBy { it.transactionId }
        val goalsById = snapshot.goals.associateBy { it.id }

        require(snapshot.categories.distinctBy { it.kind to it.name }.size == snapshot.categories.size) {
            "Backup contains duplicate category names for the same type"
        }
        require(snapshot.budgetAllocations.distinctBy { Triple(it.monthStart, it.categoryId, it.currencyCode) }.size == snapshot.budgetAllocations.size) {
            "Backup contains duplicate budget allocations"
        }
        require(snapshot.debtProfiles.distinctBy { it.accountId }.size == snapshot.debtProfiles.size) {
            "Backup contains more than one debt profile for an account"
        }

        snapshot.accounts.forEach {
            require(it.name.isNotBlank() && it.type in ACCOUNT_TYPES && it.currencyCode.isCurrencyCode()) {
                "Backup contains an invalid account"
            }
        }
        snapshot.categories.forEach {
            require(it.name.isNotBlank() && it.kind in CATEGORY_KINDS && it.sortOrder >= 0) {
                "Backup contains an invalid category"
            }
            it.mergedIntoCategoryId?.let { targetId ->
                val target = categoriesById[targetId]
                require(
                    it.archivedAt != null && targetId != it.id && target != null &&
                        target.kind == it.kind && target.archivedAt == null && target.mergedIntoCategoryId == null,
                ) { "Backup contains an invalid category merge" }
            }
        }
        snapshot.transactions.forEach {
            require(it.kind in TRANSACTION_KINDS) { "Backup contains an invalid transaction type" }
            require(it.deletedAt == null || it.deletedAt >= 0) { "Backup contains an invalid deletion timestamp" }
            require(it.payee.length <= 4096 && it.note.length <= 4096) { "Backup transaction text is too long" }
            requireDate(it.localDate, "transaction")
        }
        snapshot.transactionEntries.forEach {
            require(it.transactionId in transactionIds && it.accountId in accountIds) {
                "Backup contains a transaction entry with a missing parent"
            }
            require(it.categoryId == null || it.categoryId in categoryIds) {
                "Backup contains a transaction entry with a missing category"
            }
            require(it.categoryId == null || categoriesById.getValue(it.categoryId).kind == transactionsById.getValue(it.transactionId).kind) {
                "Backup transaction category does not match its type"
            }
            require(it.currencyCode.isCurrencyCode()) { "Backup contains an invalid transaction currency" }
            require(it.currencyCode == accountCurrencies[it.accountId]) {
                "Backup contains a transaction currency that does not match its account"
            }
        }
        require(snapshot.transactionEntries.groupBy { it.transactionId }.keys.containsAll(transactionIds)) {
            "Backup contains a transaction without entries"
        }
        snapshot.transactions.forEach { transaction ->
            val entries = entriesByTransaction.getValue(transaction.id)
            when (transaction.kind) {
                "INCOME" -> require(entries.size == 1 && entries.all { it.amountMinor > 0 }) { "Backup contains invalid income entries" }
                "EXPENSE" -> require(entries.size == 1 && entries.all { it.amountMinor < 0 && it.amountMinor != Long.MIN_VALUE }) { "Backup contains invalid expense entries" }
                "TRANSFER" -> {
                    val source = entries.singleOrNull { it.amountMinor < 0 }
                    val destination = entries.singleOrNull { it.amountMinor > 0 }
                    require(entries.size == 2 && entries.all { it.categoryId == null } && source != null && destination != null) {
                        "Backup contains an invalid transfer"
                    }
                    require(source.accountId != destination.accountId && source.amountMinor != Long.MIN_VALUE) { "Backup transfer accounts must differ" }
                    if (source.currencyCode == destination.currencyCode) {
                        require(Math.addExact(source.amountMinor, destination.amountMinor) == 0L) {
                            "Backup contains an unbalanced transfer"
                        }
                    }
                }
            }
        }
        snapshot.reconciliations.forEach {
            require(it.accountId in accountIds && (it.adjustmentTransactionId == null || it.adjustmentTransactionId in transactionIds)) {
                "Backup contains an invalid reconciliation reference"
            }
            requireDate(it.statementLocalDate, "reconciliation")
            require(Math.subtractExact(it.statementBalanceMinor, it.calculatedBalanceMinor) == it.differenceMinor) {
                "Backup contains inconsistent reconciliation amounts"
            }
            it.adjustmentTransactionId?.let { adjustmentId ->
                val adjustment = transactionsById.getValue(adjustmentId)
                val adjustmentEntries = entriesByTransaction.getValue(adjustmentId)
                require(
                    adjustment.deletedAt == null && adjustment.localDate == it.statementLocalDate &&
                        adjustmentEntries.size == 1 && adjustmentEntries.single().accountId == it.accountId &&
                        adjustmentEntries.single().amountMinor == it.differenceMinor &&
                        adjustmentEntries.single().cleared,
                ) { "Backup contains an invalid reconciliation adjustment" }
            }
        }
        snapshot.budgetAllocations.forEach {
            require(it.categoryId in categoryIds && categoriesById[it.categoryId]?.kind == "EXPENSE" && it.plannedMinor >= 0 && validCurrency(it.currencyCode)) { "Backup contains an invalid budget allocation" }
            requireDate(it.monthStart, "budget allocation")
            require(LocalDate.parse(it.monthStart).dayOfMonth == 1) { "Backup contains a budget outside a month boundary" }
        }
        snapshot.goals.forEach {
            require(
                it.name.isNotBlank() && it.targetMinor > 0 && it.savedMinor == 0L &&
                    it.currencyCode.isCurrencyCode() && it.status in GOAL_STATUSES,
            ) {
                "Backup contains an invalid goal"
            }
            it.targetLocalDate?.let { date -> requireDate(date, "goal") }
        }
        snapshot.goalContributions.forEach {
            require(
                it.goalId in goalsById && it.amountMinor > 0 &&
                    it.note.length <= 500 && (it.deletedAt == null || it.deletedAt >= 0),
            ) { "Backup contains an invalid goal contribution" }
            requireDate(it.localDate, "goal contribution")
        }
        snapshot.recurringSchedules.forEach {
            require(
                it.name.isNotBlank() && it.kind in RECURRING_KINDS && it.amountMinor > 0 &&
                    it.currencyCode.isCurrencyCode() && it.accountId in accountIds &&
                    it.currencyCode == accountCurrencies[it.accountId] &&
                    (it.categoryId == null || it.categoryId in categoryIds) && it.frequency in FREQUENCIES &&
                    it.intervalCount in 1..1_000 && it.anchorDay in 1..31 && it.anchorMonth in 1..12 &&
                    it.categoryId != null && categoriesById[it.categoryId]?.kind == it.kind,
            ) { "Backup contains an invalid recurring schedule" }
            require(it.deletedAt == null || it.deletedAt >= 0) {
                "Backup contains an invalid recurring deletion timestamp"
            }
            requireDate(it.nextLocalDate, "recurring schedule")
        }
        snapshot.debtProfiles.forEach {
            require(
                it.accountId in accountIds && it.annualRateBasisPoints >= 0 &&
                    it.minimumPaymentMinor >= 0 && it.dueDay in 1..31,
            ) { "Backup contains an invalid debt profile" }
        }
        snapshot.receiptAttachments.forEach {
            require(
                it.transactionId in transactionIds && it.originalName.isNotBlank() &&
                    it.mimeType.startsWith("image/") && it.content.isNotEmpty() &&
                    it.content.size.toLong() == it.byteSize && it.byteSize <= 8L * 1024 * 1024 &&
                    it.ocrStatus in setOf("PENDING", "PROCESSING", "COMPLETE", "FAILED"),
            ) { "Backup contains an invalid receipt attachment" }
            require(it.byteSize <= 1_000_000) { "Backup receipt must be normalized" }
            require(it.detectedAmountMinor == null || it.detectedAmountMinor > 0) { "Invalid detected receipt amount" }
            require(it.detectedCurrencyCode == null || validCurrency(it.detectedCurrencyCode)) { "Invalid detected receipt currency" }
            it.detectedLocalDate?.let { date -> requireDate(date, "receipt") }
        }
        val entriesById = snapshot.transactionEntries.associateBy { it.id }
        val entryIds = snapshot.transactionEntries.mapTo(mutableSetOf()) { it.id }
        require(snapshot.transactionExchangeRates.distinctBy { it.entryId }.size == snapshot.transactionExchangeRates.size) {
            "Backup contains duplicate exchange rates for an entry"
        }
        snapshot.transactionExchangeRates.forEach {
            require(
                it.transactionId in transactionIds && it.entryId in entryIds &&
                    it.baseCurrencyCode.isCurrencyCode() && it.quoteCurrencyCode.isCurrencyCode() &&
                    it.status in setOf("PENDING", "AVAILABLE", "FAILED") &&
                    (it.rateMicros == null || it.rateMicros > 0) &&
                    (it.status != "AVAILABLE" || (it.rateMicros != null && it.convertedAmountMinor != null)),
            ) { "Backup contains an invalid historical exchange rate" }
            val entry = entriesById.getValue(it.entryId)
            require(entry.transactionId == it.transactionId && entry.currencyCode == it.baseCurrencyCode) { "Exchange rate does not match its entry" }
            require(it.rateLocalDate == transactionsById.getValue(it.transactionId).localDate) { "Exchange rate date does not match its transaction" }
            if (it.status == "AVAILABLE") require(it.convertedAmountMinor == HistoricalRateClient.convertMinor(
                entry.amountMinor, it.baseCurrencyCode, it.quoteCurrencyCode, requireNotNull(it.rateMicros))) { "Exchange conversion is inconsistent" }
            requireDate(it.rateLocalDate, "exchange rate")
        }
        require(snapshot.accountExchangeRates.distinctBy { it.accountId }.size == snapshot.accountExchangeRates.size) { "Duplicate opening balance rates" }
        val accountsById = snapshot.accounts.associateBy { it.id }
        snapshot.accountExchangeRates.forEach { rate ->
            val account = requireNotNull(accountsById[rate.accountId]) { "Missing account for opening rate" }
            require(rate.baseCurrencyCode == account.currencyCode && validCurrency(rate.quoteCurrencyCode) && rate.rateMicros > 0)
            require(rate.openingBalanceMinor == account.openingBalanceMinor && rate.convertedAmountMinor ==
                HistoricalRateClient.convertMinor(account.openingBalanceMinor, account.currencyCode, rate.quoteCurrencyCode, rate.rateMicros)) { "Invalid opening conversion" }
            requireDate(rate.rateLocalDate, "opening rate")
        }
    }

    private fun requireUniqueIds(label: String, ids: List<String>) {
        require(ids.none(String::isBlank) && ids.distinct().size == ids.size) {
            "Backup contains blank or duplicate $label identifiers"
        }
    }

    private fun requireDate(value: String, label: String) {
        runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("Backup contains an invalid $label date", it) }
    }

    private fun String.isCurrencyCode(): Boolean = validCurrency(this)

    companion object {
        private val ACCOUNT_TYPES = setOf("CASH", "CHECKING", "SAVINGS", "CREDIT")
        private val CATEGORY_KINDS = setOf("INCOME", "EXPENSE")
        private val TRANSACTION_KINDS = setOf("INCOME", "EXPENSE", "TRANSFER")
        private val RECURRING_KINDS = setOf("INCOME", "EXPENSE")
        private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")
        private val GOAL_STATUSES = setOf("ACTIVE", "PAUSED", "COMPLETED")
    }
}
