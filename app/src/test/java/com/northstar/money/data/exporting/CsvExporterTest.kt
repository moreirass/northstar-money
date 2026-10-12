package com.northstar.money.data.exporting

import com.northstar.money.core.database.AccountBalanceRow
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.data.importing.TransactionCsvValidator
import com.northstar.money.domain.model.*
import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class CsvExporterTest {
    @Test fun formulasBecomeTextWhileSignedAmountsRoundTrip() {
        val item = TransactionItem("id", "=1+1", "Food", "Main", TransactionKind.EXPENSE, Money(-1234), "2026-10-01", false)
        val output = StringWriter().also { CsvExporter.write(it, listOf(item)) }.toString()
        assertTrue(output.contains("\"'=1+1\""))
        assertTrue(output.contains(",-1234,"))
        val parsed = TransactionCsvValidator().validate(output,
            listOf(AccountBalanceRow("account", "Main", "CHECKING", "EUR", 0)),
            listOf(CategoryEntity("food", "Food", "EXPENSE", 0)))
        assertEquals(0, parsed.errors)
        assertEquals("=1+1", parsed.transactions.single().payee)
        assertEquals(-1234L, parsed.transactions.single().amountMinor)
    }
    @Test fun whitespaceAndControlCharactersDoNotBypassNeutralization() {
        assertEquals("\"'  @SUM(A1)\"", CsvExporter.textCell("  @SUM(A1)"))
        assertTrue(CsvExporter.textCell("\t=1").startsWith("\"'"))
    }
}
