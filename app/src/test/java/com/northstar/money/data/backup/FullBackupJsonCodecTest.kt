package com.northstar.money.data.backup

import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.BudgetAllocationEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.DatabaseSnapshot
import com.northstar.money.core.database.DebtProfileEntity
import com.northstar.money.core.database.GoalEntity
import com.northstar.money.core.database.GoalContributionEntity
import com.northstar.money.core.database.ReconciliationEntity
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBackupJsonCodecTest {
    private val codec = FullBackupJsonCodec()

    @Test
    fun javaWriterMatchesOriginalJsonIncludingUnicodeAndLegacyEscapes() {
        val base = completeSnapshot()
        val snapshot = base.copy(accounts = listOf(base.accounts.single().copy(
            name = "Ação <>& \" \\u2028 \u2028\u2029 \u0000\b\n", openingBalanceMinor = Long.MIN_VALUE)),
            receiptAttachments = listOf(com.northstar.money.core.database.ReceiptAttachmentEntity(
                "r", "transaction", byteArrayOf(-1, 0, 127), "recibo", "image/png", 3, 4)),
            accountExchangeRates = listOf(com.northstar.money.core.database.AccountExchangeRateEntity(
                "account", "EUR", "JPY", 100, 170, 170000000, "2026-08-01")),
            transactionExchangeRates = listOf(com.northstar.money.core.database.TransactionExchangeRateEntity(
                "rate", "transaction", "entry", "EUR", "JPY", null, null, "2026-08-01", "test", "PENDING", null)))
        val document = FullBackupDocument(
            FullBackupJsonCodec.FORMAT, FullBackupJsonCodec.FORMAT_VERSION, 10, 1234,
            snapshot.accounts, snapshot.accountExchangeRates, null, snapshot.categories,
            snapshot.transactions, snapshot.transactionEntries, snapshot.reconciliations,
            snapshot.budgetAllocations, snapshot.goals, snapshot.goalContributions,
            snapshot.recurringSchedules, snapshot.debtProfiles, snapshot.receiptAttachments,
            snapshot.transactionExchangeRates)
        val reference = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(
            FullBackupDocument.serializer(), document)
        val result = java.io.StringWriter().also { BackupJsonWriter.write(it, document) }.toString()
        assertEquals(reference, result)
        assertEquals(snapshot, codec.decode(result).toSnapshot())
        assertEquals(snapshot, BackupJsonReader.read(reference).toSnapshot())
        val numericReceipts = reference.replace("\"/wB/\"", "[-1,0,127]")
        assertEquals(snapshot, BackupJsonReader.read(numericReceipts).toSnapshot())
    }

    @Test
    fun encodeAndDecode_preservesEveryDatabaseTable() {
        val snapshot = completeSnapshot()

        val encoded = codec.encode(snapshot, 6, 1234, null)
        val decoded = codec.decode(encoded)

        assertEquals(FullBackupJsonCodec.FORMAT, decoded.format)
        assertEquals(FullBackupJsonCodec.FORMAT_VERSION, decoded.formatVersion)
        assertEquals(6, decoded.databaseVersion)
        assertEquals(1234L, decoded.createdAtEpochMillis)
        assertEquals(snapshot, decoded.toSnapshot())
    }

    @Test
    fun decode_rejectsForeignBackupFormat() {
        val encoded = codec.encode(completeSnapshot(), 4, 1234, null)
            .replace(FullBackupJsonCodec.FORMAT, "some-other-backup")

        assertThrows(IllegalArgumentException::class.java) { codec.decode(encoded) }
    }

    @Test
    fun javaReaderRejectsMissingFieldsInvalidRecordsAndTrailingDocuments() {
        val valid = codec.encode(completeSnapshot(), 10, 1234, null)
        for (malformed in listOf(
            valid.replace("\"payee\":\"Shop\",", ""),
            valid.replace("\"note\":\"Note\"", "\"note\":null"),
            valid.replace("\"amountMinor\":-25", "\"amountMinor\":9223372036854775808"),
            valid.replace("\"status\":\"PAUSED\"", "\"unknownField\":1,\"status\":\"PAUSED\""),
            valid + "{}",
        )) {
            assertTrue(malformed != valid)
            assertThrows(IllegalArgumentException::class.java) { codec.decode(malformed) }
        }
    }

    @Test
    fun decode_acceptsBackupCreatedBeforeCategoryMerges() {
        val legacy = codec.encode(
            completeSnapshot().copy(goalContributions = emptyList()),
            5, 1234, null,
        )
            .replace(",\"mergedIntoCategoryId\":\"target\"", "")
            .replace(",\"mergedIntoCategoryId\":null", "")
            .replace(",\"deletedAt\":null", "")
            .replace(",\"goalContributions\":[]", "")

        val decoded = codec.decode(legacy)
        assertEquals(kotlinx.serialization.json.Json.decodeFromString(FullBackupDocument.serializer(), legacy), decoded)

        assertTrue(decoded.categories.all { it.mergedIntoCategoryId == null })
        assertTrue(decoded.recurringSchedules.all { it.deletedAt == null })
        assertTrue(decoded.goalContributions.isEmpty())
    }

    private fun completeSnapshot() = DatabaseSnapshot(
        accounts = listOf(AccountEntity("account", "Account", "CHECKING", "EUR", 100, 9, 1, 2)),
        categories = listOf(
            CategoryEntity("category", "Food", "EXPENSE", 1, 9, "target"),
            CategoryEntity("target", "Living", "EXPENSE", 2),
        ),
        transactions = listOf(TransactionEntity("transaction", "EXPENSE", "2026-08-01", "Shop", "Note", 3, 4)),
        transactionEntries = listOf(
            TransactionEntryEntity("entry", "transaction", "account", "category", -25, "EUR", false),
        ),
        reconciliations = listOf(
            ReconciliationEntity("reconciliation", "account", "2026-08-01", 75, 75, 0, null, 5),
        ),
        budgetAllocations = listOf(BudgetAllocationEntity("budget", "2026-08-01", "category", 200)),
        goals = listOf(GoalEntity("goal", "Reserve", 1000, 0, "EUR", null, "PAUSED", 6)),
        goalContributions = listOf(
            GoalContributionEntity("contribution", "goal", 100, "2026-08-01", "Opening", 6, 7, 9),
        ),
        recurringSchedules = listOf(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "EUR", "account", "category",
                "MONTHLY", 1, "2026-09-01", false, 7,
            ),
        ),
        debtProfiles = listOf(DebtProfileEntity("debt", "account", 350, 50, 15, 8)),
    )
}
