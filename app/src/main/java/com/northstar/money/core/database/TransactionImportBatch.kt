package com.northstar.money.core.database

data class TransactionImportItem(
    val transaction: TransactionEntity,
    val entry: TransactionEntryEntity,
)

data class TransactionImportWriteResult(
    val imported: Int,
    val skippedDuplicates: Int,
)
