package com.northstar.money.data.backup

import com.northstar.money.core.database.DatabaseSnapshot
import java.time.LocalDate

class DatabaseSnapshotValidator {
    fun validate(snapshot: DatabaseSnapshot) {
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

        val accountIds = snapshot.accounts.mapTo(mutableSetOf()) { it.id }
        val accountCurrencies = snapshot.accounts.associate { it.id to it.currencyCode }
        val categoryIds = snapshot.categories.mapTo(mutableSetOf()) { it.id }
        val categoriesById = snapshot.categories.associateBy { it.id }
        val transactionIds = snapshot.transactions.mapTo(mutableSetOf()) { it.id }
        val goalsById = snapshot.goals.associateBy { it.id }

        require(snapshot.categories.distinctBy { it.kind to it.name }.size == snapshot.categories.size) {
            "Backup contains duplicate category names for the same type"
        }
        require(snapshot.budgetAllocations.distinctBy { it.monthStart to it.categoryId }.size == snapshot.budgetAllocations.size) {
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
            requireDate(it.localDate, "transaction")
        }
        snapshot.transactionEntries.forEach {
            require(it.transactionId in transactionIds && it.accountId in accountIds) {
                "Backup contains a transaction entry with a missing parent"
            }
            require(it.categoryId == null || it.categoryId in categoryIds) {
                "Backup contains a transaction entry with a missing category"
            }
            require(it.currencyCode.isCurrencyCode()) { "Backup contains an invalid transaction currency" }
            require(it.currencyCode == accountCurrencies[it.accountId]) {
                "Backup contains a transaction currency that does not match its account"
            }
        }
        require(snapshot.transactionEntries.groupBy { it.transactionId }.keys.containsAll(transactionIds)) {
            "Backup contains a transaction without entries"
        }
        val entriesByTransaction = snapshot.transactionEntries.groupBy { it.transactionId }
        snapshot.transactions.forEach { transaction ->
            val entries = entriesByTransaction.getValue(transaction.id)
            when (transaction.kind) {
                "INCOME" -> require(entries.all { it.amountMinor > 0 }) { "Backup contains invalid income entries" }
                "EXPENSE" -> require(entries.all { it.amountMinor < 0 }) { "Backup contains invalid expense entries" }
                "TRANSFER" -> {
                    require(entries.size >= 2 && entries.all { it.categoryId == null }) {
                        "Backup contains an invalid transfer"
                    }
                    require(entries.groupBy { it.currencyCode }.values.all { currencyEntries ->
                        currencyEntries.sumOf { it.amountMinor } == 0L
                    }) { "Backup contains an unbalanced transfer" }
                }
            }
        }
        snapshot.reconciliations.forEach {
            require(it.accountId in accountIds && (it.adjustmentTransactionId == null || it.adjustmentTransactionId in transactionIds)) {
                "Backup contains an invalid reconciliation reference"
            }
            requireDate(it.statementLocalDate, "reconciliation")
        }
        snapshot.budgetAllocations.forEach {
            require(it.categoryId in categoryIds && it.plannedMinor >= 0) { "Backup contains an invalid budget allocation" }
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
                    it.intervalCount > 0,
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
    }

    private fun requireUniqueIds(label: String, ids: List<String>) {
        require(ids.none(String::isBlank) && ids.distinct().size == ids.size) {
            "Backup contains blank or duplicate $label identifiers"
        }
    }

    private fun requireDate(value: String, label: String) {
        runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("Backup contains an invalid $label date", it) }
    }

    private fun String.isCurrencyCode(): Boolean = length == 3 && all(Char::isUpperCase)

    companion object {
        private val ACCOUNT_TYPES = setOf("CASH", "CHECKING", "SAVINGS", "CREDIT")
        private val CATEGORY_KINDS = setOf("INCOME", "EXPENSE")
        private val TRANSACTION_KINDS = setOf("INCOME", "EXPENSE", "TRANSFER")
        private val RECURRING_KINDS = setOf("INCOME", "EXPENSE")
        private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")
        private val GOAL_STATUSES = setOf("ACTIVE", "PAUSED", "COMPLETED")
    }
}
