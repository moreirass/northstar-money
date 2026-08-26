package com.northstar.money.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts", indices = [Index("archivedAt")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val openingBalanceMinor: Long,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["mergedIntoCategoryId"],
        onDelete = ForeignKey.SET_NULL,
        deferred = true,
    )],
    indices = [Index(value = ["kind", "name"], unique = true), Index("mergedIntoCategoryId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val sortOrder: Int,
    val archivedAt: Long? = null,
    val mergedIntoCategoryId: String? = null,
)

data class ArchivedCategoryRow(
    val id: String,
    val name: String,
    val kind: String,
    val mergedIntoCategoryId: String?,
    val mergedIntoCategoryName: String?,
)

@Serializable
@Entity(tableName = "transactions", indices = [Index("localDate"), Index("createdAt"), Index("deletedAt")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val localDate: String,
    val payee: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "transaction_entries",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("transactionId"), Index("accountId"), Index("categoryId")],
)
data class TransactionEntryEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val accountId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val cleared: Boolean,
)

data class TransactionRow(
    val id: String,
    val payee: String,
    val kind: String,
    val localDate: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountName: String,
    val categoryName: String?,
)

data class SummaryRow(
    val balanceMinor: Long,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class AccountBalanceRow(
    val id: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val balanceMinor: Long,
)

@Serializable
@Entity(
    tableName = "reconciliations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["adjustmentTransactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("adjustmentTransactionId")],
)
data class ReconciliationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val statementLocalDate: String,
    val statementBalanceMinor: Long,
    val calculatedBalanceMinor: Long,
    val differenceMinor: Long,
    val adjustmentTransactionId: String?,
    val completedAt: Long,
)

@Serializable
@Entity(
    tableName = "budget_allocations",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId"), Index(value = ["monthStart", "categoryId"], unique = true)],
)
data class BudgetAllocationEntity(
    @PrimaryKey val id: String,
    val monthStart: String,
    val categoryId: String,
    val plannedMinor: Long,
)

@Serializable
@Entity(tableName = "goals", indices = [Index("status")])
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val currencyCode: String,
    val targetLocalDate: String?,
    val status: String,
    val createdAt: Long,
)

data class BudgetRow(
    val categoryId: String,
    val categoryName: String,
    val plannedMinor: Long,
    val spentMinor: Long,
)

@Serializable
@Entity(
    tableName = "recurring_schedules",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("nextLocalDate"), Index("deletedAt")],
)
data class RecurringScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: String,
    val categoryId: String?,
    val frequency: String,
    val intervalCount: Int,
    val nextLocalDate: String,
    val active: Boolean,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "debt_profiles",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["accountId"], unique = true)],
)
data class DebtProfileEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val annualRateBasisPoints: Int,
    val minimumPaymentMinor: Long,
    val dueDay: Int,
    val createdAt: Long,
)

data class DebtProfileRow(
    val id: String,
    val accountId: String,
    val annualRateBasisPoints: Int,
    val minimumPaymentMinor: Long,
    val dueDay: Int,
    val createdAt: Long,
    val currencyCode: String,
)
