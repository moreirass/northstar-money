package com.northstar.money.data.exporting

import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.Money
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedXlsxExporterTest {
    @Test
    fun `creates a formatted open xml workbook`() {
        val bytes = FormattedXlsxExporter().export(
            transactions = emptyList(), accounts = emptyList(), budgets = emptyList(),
            summary = FinanceSummary(Money(12345), Money(5000), Money(2000)), rates = emptyList(),
        )
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.getValue("xl/styles.xml").contains("numFmtId=\"164\""))
        assertTrue(entries.getValue("xl/worksheets/sheet4.xml").contains("123.45"))
    }
}
