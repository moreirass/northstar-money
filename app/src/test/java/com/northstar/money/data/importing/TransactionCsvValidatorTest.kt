package com.northstar.money.data.importing

import com.northstar.money.core.database.AccountBalanceRow
import com.northstar.money.core.database.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCsvValidatorTest {
    private val validator = TransactionCsvValidator()
    private val accounts = listOf(AccountBalanceRow("account", "Current", "CHECKING", "EUR", 0))
    private val categories = listOf(
        CategoryEntity("food", "Food", "EXPENSE", 0),
        CategoryEntity("travel", "Travel", "EXPENSE", 1),
    )

    @Test
    fun validate_parsesQuotedCommasEscapedQuotesAndNewlines() {
        val csv = HEADER +
            "\n\"2026-08-01\",\"EXPENSE\",\"Super \"\"Shop\"\", Lisbon\",\"Food\",\"Current\",\"-1234\",\"EUR\"" +
            "\n\"2026-08-02\",\"EXPENSE\",\"Two\nlines\",\"Food\",\"Current\",\"-500\",\"EUR\""

        val result = validator.validate(csv, accounts, categories)

        assertEquals(0, result.errors)
        assertEquals(2, result.transactions.size)
        assertEquals("Super \"Shop\", Lisbon", result.transactions[0].payee)
        assertEquals("Two\nlines", result.transactions[1].payee)
    }

    @Test
    fun validate_reportsEveryInvalidRowBeforeWriting() {
        val csv = HEADER +
            "\n2026-08-01,EXPENSE,Valid,Food,Current,-100,EUR" +
            "\n2026-08-02,EXPENSE,Unknown account,Food,Missing,-200,EUR" +
            "\n2026-08-03,EXPENSE,Wrong sign,Food,Current,300,EUR"

        val result = validator.validate(csv, accounts, categories)

        assertEquals(2, result.errors)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun validate_deduplicatesRowsWithinFile() {
        val row = "2026-08-01,EXPENSE,Shop,Food,Current,-100,EUR"

        val result = validator.validate("$HEADER\n$row\n$row", accounts, categories)

        assertEquals(0, result.errors)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skippedDuplicates)
    }

    @Test
    fun validate_doesNotDiscardSameAmountAssignedToDifferentCategories() {
        val first = "2026-08-01,EXPENSE,Shop,Food,Current,-100,EUR"
        val second = "2026-08-01,EXPENSE,Shop,Travel,Current,-100,EUR"

        val result = validator.validate("$HEADER\n$first\n$second", accounts, categories)

        assertEquals(0, result.errors)
        assertEquals(2, result.transactions.size)
        assertEquals(0, result.skippedDuplicates)
    }

    @Test
    fun validate_rejectsMalformedDocumentAndWrongHeader() {
        assertEquals(1, validator.validate("$HEADER\n\"unclosed", accounts, categories).errors)
        assertEquals(1, validator.validate("wrong,header", accounts, categories).errors)
    }

    companion object {
        private const val HEADER = "date,type,payee,category,account,amount,currency"
    }
}
