package com.northstar.money.data.importing

import com.northstar.money.data.exporting.CsvExporter
import com.northstar.money.data.exporting.reference.KotlinCsvExporter
import com.northstar.money.domain.model.Money
import com.northstar.money.domain.model.TransactionItem
import com.northstar.money.domain.model.TransactionKind
import java.io.ByteArrayInputStream
import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class JavaFileCompatibilityTest {
    @Test fun exportedCsvHasIdenticalBytesIncludingUnicodeWhitespaceAndSignedIntegers() {
        for (value in listOf("", "=SUM(A1)", "  @cmd", "\u00a0+1", "\u2003-1", "\tcell", "a\"b,\nc", "ação")) {
            assertEquals(KotlinCsvExporter.textCell(value), CsvExporter.textCell(value))
        }
        val rows = listOf(
            TransactionItem("a", "=1+1", null, "Ação", TransactionKind.EXPENSE, Money(-12345), "2026-11-01", false),
            TransactionItem("b", "收入", "Other", "Main", TransactionKind.INCOME, Money(Long.MAX_VALUE, "JPY"), "2026-11-02", true),
        )
        val old = StringWriter().also { KotlinCsvExporter.write(it, rows) }.toString()
        val migrated = StringWriter().also { CsvExporter.write(it, rows) }.toString()
        assertArrayEquals(old.toByteArray(), migrated.toByteArray())
    }

    @Test fun boundedReaderMatchesAtAndAcrossTheLimitAndDoesNotCloseItsInput() {
        for (size in listOf(0, 1, 8191, 8192, 8193)) {
            val bytes = ByteArray(size) { (it % 256).toByte() }
            val limit = maxOf(size, 1)
            assertArrayEquals(ByteArrayInputStream(bytes).readBounded(limit),
                BoundedStreams.read(ByteArrayInputStream(bytes), limit))
            if (size > 1) assertThrows(IllegalArgumentException::class.java) {
                BoundedStreams.read(ByteArrayInputStream(bytes), size - 1)
            }
        }
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed = true }
        }
        BoundedStreams.read(input, 1)
        assertFalse(closed)
    }
}
