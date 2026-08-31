package com.northstar.money.data.repository

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.RecurringScheduleEntity
import com.northstar.money.data.exchange.HistoricalRateProvider
import com.northstar.money.data.exchange.RateQuote
import com.northstar.money.data.receipt.ReceiptImageNormalizer
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.util.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoricalExchangeRateIntegrationTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var rateProvider: MutableRateProvider
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        database.financeDao().insertAccount(
            AccountEntity("usd-account", "USD account", "CHECKING", "USD", 10_000, null, 1, 1),
        )
        database.financeDao().insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))
        rateProvider = MutableRateProvider()
        repository = OfflineFinanceRepository(
            database.financeDao(),
            historicalRateProvider = rateProvider,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun reconciliationRecurringAndCsvImportPersistRatesImmediately() = runBlocking {
        val today = LocalDate.now()
        repository.reconcile("usd-account", today.toString(), Money(9_000, "USD"), createAdjustment = true)
        database.financeDao().insertRecurring(
            RecurringScheduleEntity(
                "recurring", "Rent", "EXPENSE", 500, "USD", "usd-account", "food",
                "MONTHLY", 1, today.toString(), true, 1,
            ),
        )
        assertEquals(1, repository.postDueRecurringOccurrences(today.toString()))
        val importResult = repository.importCsv(
            "date,type,payee,category,account,amount,currency\n${today},EXPENSE,Shop,Food,USD account,-250,USD",
        )

        assertEquals(1, importResult.imported)
        val snapshot = database.financeDao().exportSnapshot()
        assertEquals(3, snapshot.transactionExchangeRates.size)
        assertTrue(snapshot.transactionExchangeRates.all { it.status == "AVAILABLE" })
        assertTrue(snapshot.transactionExchangeRates.all { it.rateMicros == 2_000_000L })
    }

    @Test
    fun failedRateRefreshDuringEditRetainsAvailableRateAndRecalculatesAmount() = runBlocking {
        repository.addTransaction(TransactionKind.EXPENSE, Money(1_000, "USD"), "usd-account", "food", "Shop")
        val transaction = repository.observeTransactions().first().single()
        val originalRate = database.financeDao().exportSnapshot().transactionExchangeRates.single()
        rateProvider.fail = true

        repository.updateTransaction(
            repository.getTransactionForEdit(transaction.id).copy(
                localDate = LocalDate.now().minusDays(1).toString(),
                amount = Money(1_500, "USD"),
            ),
        )

        val retained = database.financeDao().exportSnapshot().transactionExchangeRates.single()
        assertEquals("AVAILABLE", retained.status)
        assertEquals(originalRate.rateMicros, retained.rateMicros)
        assertEquals(originalRate.rateLocalDate, retained.rateLocalDate)
        assertEquals(-3_000L, retained.convertedAmountMinor)
    }

    @Test
    fun largeReceiptIsNormalizedBeforeRoomPersistsAndObservesIt() = runBlocking {
        repository.addTransaction(TransactionKind.EXPENSE, Money(1_000, "USD"), "usd-account", "food", "Shop")
        val transaction = repository.observeTransactions().first().single()
        val bitmap = Bitmap.createBitmap(1_200, 1_600, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        bitmap.setPixels(IntArray(bitmap.width * bitmap.height) { random.nextInt() }, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val input = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        assertTrue(input.size > ReceiptImageNormalizer.MAX_STORED_BYTES)

        repository.addReceiptAttachment(transaction.id, "large.jpg", "image/jpeg", input)

        val stored = database.financeDao().exportSnapshot().receiptAttachments.single()
        assertTrue(stored.byteSize <= ReceiptImageNormalizer.MAX_STORED_BYTES)
        assertEquals(1, repository.observeReceiptAttachments().first().size)
    }

    private class MutableRateProvider : HistoricalRateProvider {
        var fail = false

        override suspend fun getRate(base: String, quote: String, localDate: String): RateQuote {
            if (fail) error("offline")
            return RateQuote(localDate, 2_000_000L, "Test")
        }
    }
}
