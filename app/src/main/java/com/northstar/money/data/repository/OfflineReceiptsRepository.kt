package com.northstar.money.data.repository

import androidx.concurrent.futures.await
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture

import com.northstar.money.core.database.copy
import com.northstar.money.core.database.SuspendingFinanceDao
import com.northstar.money.core.database.ReceiptAttachmentEntity
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionKind
import com.northstar.money.domain.model.ReceiptAttachment
import com.northstar.money.data.receipt.ReceiptOcrEngine
import com.northstar.money.data.receipt.ReceiptImageNormalizer
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.northstar.money.core.database.ReceiptMetadataRow
import com.northstar.money.domain.repository.TransactionsRepository
import com.northstar.money.domain.repository.ReceiptsRepository

private const val MAX_RECEIPT_BYTES = 8 * 1024 * 1024
private const val BASE_CURRENCY_CODE = "EUR"

internal class OfflineReceiptsRepository(
    private val dao: SuspendingFinanceDao,
    private val receiptOcrEngine: ReceiptOcrEngine,
    private val transactions: TransactionsRepository,
) : ReceiptsRepository {
    override fun observeReceiptAttachments(): LiveData<List<ReceiptAttachment>> = RepositoryTasks.observe {
        dao.observeReceiptAttachments().map { rows -> rows.map(::receiptAttachmentToDomain) }
    }

    override fun addReceiptAttachment(
        transactionId: String,
        originalName: String,
        mimeType: String,
        content: ByteArray,
    ): ListenableFuture<Void?> = RepositoryTasks.write {
        require(content.isNotEmpty() && content.size <= MAX_RECEIPT_BYTES) { "Receipt images must be 8 MB or smaller" }
        require(mimeType.startsWith("image/")) { "Only receipt images are supported" }
        val stored = dao.getTransactionForEdit(transactionId)
        val normalizedContent = withContext(Dispatchers.Default) { ReceiptImageNormalizer.normalize(content) }
        val attachment = ReceiptAttachmentEntity(
            UUID.randomUUID().toString(),
            transactionId,
            normalizedContent,
            originalName.trim().ifBlank { "receipt" },
            "image/jpeg",
            normalizedContent.size.toLong(),
            System.currentTimeMillis(),
        )
        dao.insertReceiptAttachment(attachment)
        processReceiptOcr(attachment, stored.entries.first().currencyCode)
    }

    override fun deleteReceiptAttachment(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        require(dao.deleteReceiptAttachment(id) == 1) { "Receipt attachment is missing" }
    }

    override fun retryReceiptOcr(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        val attachment = requireNotNull(dao.getReceiptAttachment(id)) { "Receipt attachment is missing" }
        val stored = dao.getTransactionForEdit(attachment.transactionId)
        processReceiptOcr(attachment, stored.entries.first().currencyCode)
    }

    override fun applyReceiptOcr(id: String): ListenableFuture<Void?> = RepositoryTasks.write {
        val attachment = requireNotNull(dao.getReceiptAttachment(id)) { "Receipt attachment is missing" }
        val transaction = transactions.getTransactionForEdit(attachment.transactionId).await()
        require(transaction.kind != TransactionKind.TRANSFER) { "Receipt values cannot be applied to a transfer" }
        val detectedAmount = attachment.detectedAmountMinor?.let { minor ->
            val currency = attachment.detectedCurrencyCode ?: transaction.amount.currencyCode
            require(currency == transaction.amount.currencyCode) { "Receipt currency does not match the account" }
            Money(minor, currency)
        }
        transactions.updateTransaction(
            transaction.copy(
                amount = detectedAmount ?: transaction.amount,
                localDate = attachment.detectedLocalDate ?: transaction.localDate,
                payee = attachment.detectedMerchant ?: transaction.payee,
            ),
        ).await()
    }

    private fun receiptAttachmentToDomain(row: ReceiptMetadataRow) = ReceiptAttachment(
        id = row.id,
        transactionId = row.transactionId,
        originalName = row.originalName,
        mimeType = row.mimeType,
        byteSize = row.byteSize,
        createdAt = row.createdAt,
        ocrStatus = row.ocrStatus,
        detectedAmount = row.detectedAmountMinor?.let { minor ->
            Money(minor, row.detectedCurrencyCode ?: BASE_CURRENCY_CODE)
        },
        detectedLocalDate = row.detectedLocalDate,
        detectedMerchant = row.detectedMerchant,
    )

    private suspend fun processReceiptOcr(attachment: ReceiptAttachmentEntity, currencyCode: String) {
        dao.updateReceiptAttachment(attachment.withOcrStatus("PROCESSING"))
        suspendResult { receiptOcrEngine.recognize(attachment.content, currencyCode) }
            .onSuccess { parsed ->
                dao.updateReceiptAttachment(
                    attachment.withOcrResult(
                        "COMPLETE",
                        parsed.rawText,
                        parsed.amount?.minor,
                        parsed.amount?.currencyCode,
                        parsed.localDate,
                        parsed.merchant,
                    ),
                )
            }
            .onFailure {
                dao.updateReceiptAttachment(attachment.withOcrStatus("FAILED"))
            }
    }
}
