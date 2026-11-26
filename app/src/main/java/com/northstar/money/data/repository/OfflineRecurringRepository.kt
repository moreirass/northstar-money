package com.northstar.money.data.repository

import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.domain.model.Category
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.EditableRecurring
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map
import com.northstar.money.domain.model.nextOccurrence
import com.northstar.money.domain.repository.RecurringRepository

private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")
private const val MAX_RECURRING_POSTS_PER_RUN = 10_000

internal class OfflineRecurringRepository(
    private val dao: SuspendingFinanceDao,
    private val rates: OfflineExchangeRatesRepository,
) : RecurringRepository {
    override fun observeRecurring(): LiveData<List<com.northstar.money.domain.model.RecurringItem>> = RepositoryTasks.observe {
        dao.observeRecurring().map { rows ->
            rows.map(::recurringToDomain)
        }
    }

    override fun observePausedRecurring(): LiveData<List<com.northstar.money.domain.model.RecurringItem>> = RepositoryTasks.observe {
        dao.observePausedRecurring().map { rows -> rows.map(::recurringToDomain) }
    }

    override fun observeDeletedRecurring(): LiveData<List<com.northstar.money.domain.model.RecurringItem>> = RepositoryTasks.observe {
        dao.observeDeletedRecurring().map { rows -> rows.map(::recurringToDomain) }
    }

    override fun createRecurring(
        name: String,
        kind: TransactionKind,
        amount: Money,
        accountId: String,
        categoryId: String?,
        frequency: String,
        nextDate: String,
    ): ListenableFuture<Void?> = RepositoryTasks.write {
        require(name.isNotBlank() && amount.minor > 0)
        require(kind == TransactionKind.INCOME || kind == TransactionKind.EXPENSE) { "Recurring transfers are not supported" }
        dao.requireAccountCurrency(accountId, amount.currencyCode)
        val requiredCategoryId = requireNotNull(categoryId) { "Category is required" }
        require(dao.getActiveCategoryKind(requiredCategoryId) == kind.name) {
            "Category does not match the recurring type"
        }
        require(frequency in FREQUENCIES) { "Unsupported recurring frequency" }
        LocalDate.parse(nextDate)
        dao.insertRecurring(
            com.northstar.money.core.database.RecurringScheduleEntity(
                UUID.randomUUID().toString(), name.trim(), kind.name, amount.minor,
                amount.currencyCode, accountId, requiredCategoryId, frequency, 1, nextDate, true,
                System.currentTimeMillis(),
            )
        )
    }

    override fun getRecurringForEdit(id: String): ListenableFuture<EditableRecurring> = RepositoryTasks.read {
        val recurring = requireNotNull(dao.getRecurringForEdit(id)) { "Recurring schedule is missing or deleted" }
        return@read EditableRecurring(
            id = recurring.id,
            name = recurring.name,
            kind = TransactionKind.valueOf(recurring.kind),
            amount = Money(recurring.amountMinor, recurring.currencyCode),
            accountId = recurring.accountId,
            categoryId = recurring.categoryId?.let { dao.getCanonicalActiveCategoryId(it) },
            frequency = recurring.frequency,
            intervalCount = recurring.intervalCount,
            nextLocalDate = recurring.nextLocalDate,
        )
    }

    override fun updateRecurring(recurring: EditableRecurring): ListenableFuture<Void?> = RepositoryTasks.write {
        require(recurring.name.isNotBlank() && recurring.amount.minor > 0) {
            "Name and positive amount are required"
        }
        require(recurring.kind == TransactionKind.INCOME || recurring.kind == TransactionKind.EXPENSE) {
            "Recurring transfers are not supported"
        }
        require(recurring.frequency in FREQUENCIES && recurring.intervalCount in 1..1_000) {
            "Unsupported recurring frequency"
        }
        LocalDate.parse(recurring.nextLocalDate)
        val stored = requireNotNull(dao.getRecurringForEdit(recurring.id)) { "Recurring schedule is missing or deleted" }
        require(recurring.amount.currencyCode == stored.currencyCode) { "Recurring currency cannot be changed" }
        dao.requireAccountCurrency(recurring.accountId, recurring.amount.currencyCode)
        val categoryId = requireNotNull(recurring.categoryId) { "Category is required" }
        require(dao.getActiveCategoryKind(categoryId) == recurring.kind.name) {
            "Category does not match the recurring type"
        }
        dao.updateRecurring(
            stored.withDetails(
                recurring.name.trim(),
                recurring.kind.name,
                recurring.amount.minor,
                recurring.accountId,
                categoryId,
                recurring.frequency,
                recurring.intervalCount,
                recurring.nextLocalDate,
                if (stored.nextLocalDate != recurring.nextLocalDate) LocalDate.parse(recurring.nextLocalDate).dayOfMonth else stored.anchorDay,
                if (stored.nextLocalDate != recurring.nextLocalDate) LocalDate.parse(recurring.nextLocalDate).monthValue else stored.anchorMonth,
            ),
        )
    }

    override fun pauseRecurring(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        dao.pauseRecurring(id)
    }

    override fun resumeRecurring(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        val recurring = requireNotNull(dao.getRecurringForEdit(id)) { "Recurring schedule is missing or deleted" }
        dao.requireAccountCurrency(recurring.accountId, recurring.currencyCode)
        val categoryId = requireNotNull(recurring.categoryId?.let { dao.getCanonicalActiveCategoryId(it) }) {
            "Choose an active category before resuming"
        }
        require(dao.getActiveCategoryKind(categoryId) == recurring.kind) { "Category does not match the recurring type" }
        if (categoryId != recurring.categoryId) dao.updateRecurring(recurring.withCategory(categoryId))
        dao.resumeRecurring(id)
    }

    override fun deleteRecurring(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        dao.deleteRecurring(id, System.currentTimeMillis())
    }

    override fun restoreRecurring(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        dao.restoreRecurring(id)
    }

    override fun postDueRecurringOccurrences(throughLocalDate: String): ListenableFuture<Int> = RepositoryTasks.read {
        val throughDate = LocalDate.parse(throughLocalDate)
        var posted = 0
        dao.getDueRecurringSchedules(throughDate.toString()).forEach { initial ->
            var occurrenceDate = LocalDate.parse(initial.nextLocalDate)
            while (occurrenceDate <= throughDate && posted < MAX_RECURRING_POSTS_PER_RUN) {
                val nextDate = nextOccurrence(occurrenceDate, initial.frequency, initial.intervalCount, initial.anchorDay, initial.anchorMonth)
                val occurrenceKey = "${initial.id}:${occurrenceDate}"
                val transactionId = UUID.nameUUIDFromBytes("transaction:$occurrenceKey".toByteArray()).toString()
                val entryId = UUID.nameUUIDFromBytes("entry:$occurrenceKey".toByteArray()).toString()
                val inserted = dao.postRecurringOccurrence(
                    recurringId = initial.id,
                    occurrenceLocalDate = occurrenceDate.toString(),
                    nextLocalDate = nextDate.toString(),
                    transactionId = transactionId,
                    entryId = entryId,
                    createdAt = System.currentTimeMillis(),
                )
                if (!inserted) return@forEach
                val stored = dao.getTransactionForEdit(transactionId)
                rates.requestHistoricalRate(transactionId, stored.entries.single(), occurrenceDate.toString())
                posted += 1
                occurrenceDate = nextDate
            }
        }
        return@read posted
    }

    private fun recurringToDomain(row: com.northstar.money.core.database.RecurringScheduleEntity) =
        com.northstar.money.domain.model.RecurringItem(
            row.id,
            row.name,
            TransactionKind.valueOf(row.kind),
            Money(row.amountMinor, row.currencyCode),
            row.nextLocalDate,
            row.frequency,
            row.intervalCount, row.anchorDay, row.anchorMonth,
        )
}
