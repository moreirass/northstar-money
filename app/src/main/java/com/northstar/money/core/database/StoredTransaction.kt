package com.northstar.money.core.database

data class StoredTransaction(
    val transaction: TransactionEntity,
    val entries: List<TransactionEntryEntity>,
    val isReconciliationAdjustment: Boolean,
)
