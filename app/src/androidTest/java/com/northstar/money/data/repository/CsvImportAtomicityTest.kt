package com.northstar.money.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.northstar.money.core.database.AccountEntity
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.core.database.NorthstarDatabase
import com.northstar.money.core.database.TransactionEntity
import com.northstar.money.core.database.TransactionEntryEntity
import com.northstar.money.core.database.TransactionImportItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvImportAtomicityTest {
    private lateinit var database: NorthstarDatabase
    private lateinit var repository: OfflineFinanceRepository

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NorthstarDatabase::class.java,
        ).build()
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("account", "Current", "CHECKING", "EUR", 0, null, 1, 1))
        dao.insertCategory(CategoryEntity("food", "Food", "EXPENSE", 0))
        repository = OfflineFinanceRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun invalidLaterRow_preventsEveryDatabaseWrite() = runBlocking {
        val csv = HEADER +
            "\n2026-08-01,EXPENSE,Valid,Food,Current,-100,EUR" +
            "\n2026-08-02,EXPENSE,Invalid,Food,Missing,-200,EUR"

        val result = repository.importCsv(csv)

        assertEquals(0, result.imported)
        assertEquals(1, result.errors)
        assertEquals(emptyList<Any>(), database.financeDao().exportSnapshot().transactions)
        assertEquals(emptyList<Any>(), database.financeDao().exportSnapshot().transactionEntries)
    }

    @Test
    fun validRows_areInsertedAtomicallyAndDuplicatesAreSkipped() = runBlocking {
        val row = "2026-08-01,EXPENSE,Shop,Food,Current,-100,EUR"

        val first = repository.importCsv("$HEADER\n$row\n$row")
        val second = repository.importCsv("$HEADER\n$row\n$row")

        assertEquals(1, first.imported)
        assertEquals(1, first.skippedDuplicates)
        assertEquals(0, first.errors)
        assertEquals(0, second.imported)
        assertEquals(2, second.skippedDuplicates)
        assertEquals(1, database.financeDao().exportSnapshot().transactions.size)
        assertEquals(-100L, database.financeDao().exportSnapshot().transactionEntries.single().amountMinor)
    }

    @Test
    fun databaseFailureOnLaterRow_rollsBackWholeImportBatch() = runBlocking {
        val valid = importItem("transaction-1", "entry-1", "account")
        val invalid = importItem("transaction-2", "entry-2", "missing-account")

        val failure = runCatching {
            database.financeDao().importTransactions(listOf(valid, invalid))
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertEquals(emptyList<Any>(), database.financeDao().exportSnapshot().transactions)
        assertEquals(emptyList<Any>(), database.financeDao().exportSnapshot().transactionEntries)
    }

    private fun importItem(transactionId: String, entryId: String, accountId: String) = TransactionImportItem(
        transaction = TransactionEntity(transactionId, "EXPENSE", "2026-08-01", transactionId, "", 1, 1),
        entry = TransactionEntryEntity(entryId, transactionId, accountId, "food", -100, "EUR", true),
    )

    companion object {
        private const val HEADER = "date,type,payee,category,account,amount,currency"
    }
}
