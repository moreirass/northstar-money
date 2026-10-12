package com.northstar.money.data.exporting

import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.domain.model.TransactionItem
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Currency
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FormattedXlsxExporter {
    fun export(
        transactions: List<TransactionItem>,
        accounts: List<Account>,
        budgets: List<BudgetProgress>,
        summary: FinanceSummary,
        rates: List<HistoricalExchangeRate>,
    ): ByteArray {
        return ByteArrayOutputStream().use { output ->
            write(output, transactions, accounts, budgets, summary, rates)
            output.toByteArray()
        }
    }

    fun write(output: OutputStream, transactions: List<TransactionItem>, accounts: List<Account>,
        budgets: List<BudgetProgress>, summary: FinanceSummary, rates: List<HistoricalExchangeRate>) {
        val sheets = listOf(
            Sheet("Transactions", listOf("Date", "Type", "Merchant", "Category", "Account", "Amount", "Currency"),
                transactions.asSequence().map { transaction ->
                    listOf(
                        Cell.Text(transaction.localDate), Cell.Text(transaction.kind.name), Cell.Text(transaction.payee),
                        Cell.Text(transaction.categoryName.orEmpty()), Cell.Text(transaction.accountName),
                        Cell.Number(major(transaction.amount.minor, transaction.amount.currencyCode)),
                        Cell.Text(transaction.amount.currencyCode),
                    )
                }),
            Sheet("Accounts", listOf("Account", "Type", "Balance", "Cleared balance", "Currency"),
                accounts.asSequence().map { account ->
                    listOf(
                        Cell.Text(account.name), Cell.Text(account.type.name),
                        Cell.Number(major(account.balance.minor, account.currencyCode)),
                        Cell.Number(major(account.clearedBalance.minor, account.currencyCode)), Cell.Text(account.currencyCode),
                    )
                }),
            Sheet("Budgets", listOf("Category", "Allocated", "Rollover", "Planned", "Spent", "Currency"),
                budgets.asSequence().map { budget ->
                    listOf(
                        Cell.Text(budget.categoryName), Cell.Number(major(budget.allocated.minor, budget.allocated.currencyCode)),
                        Cell.Number(major(budget.rollover.minor, budget.rollover.currencyCode)),
                        Cell.Number(major(budget.planned.minor, budget.planned.currencyCode)),
                        Cell.Number(major(budget.spent.minor, budget.spent.currencyCode)), Cell.Text(budget.planned.currencyCode),
                    )
                }),
            Sheet("Summary", listOf("Metric", "Amount", "Currency"), listOf(
                listOf(Cell.Text("Current balance"), Cell.Number(major(summary.balance.minor, summary.balance.currencyCode)), Cell.Text(summary.balance.currencyCode)),
                listOf(Cell.Text("Income this month"), Cell.Number(major(summary.incomeThisMonth.minor, summary.incomeThisMonth.currencyCode)), Cell.Text(summary.incomeThisMonth.currencyCode)),
                listOf(Cell.Text("Expenses this month"), Cell.Number(major(summary.expensesThisMonth.minor, summary.expensesThisMonth.currencyCode)), Cell.Text(summary.expensesThisMonth.currencyCode)),
            ).asSequence()),
            Sheet("Exchange rates", listOf("Date", "Base", "Quote", "Rate", "Source", "Status", "Transaction ID"),
                rates.asSequence().map { rate -> listOf(
                    Cell.Text(rate.rateLocalDate), Cell.Text(rate.baseCurrencyCode), Cell.Text(rate.quoteCurrencyCode),
                    rate.rateMicros?.let { Cell.Number(it.toBigDecimal().movePointLeft(6).toPlainString()) } ?: Cell.Text(""),
                    Cell.Text(rate.source), Cell.Text(rate.status), Cell.Text(rate.transactionId),
                ) }),
        )
        ZipOutputStream(output).use { zip ->
            write(zip, "[Content_Types].xml", contentTypes(sheets.size))
            write(zip, "_rels/.rels", ROOT_RELS)
            write(zip, "xl/workbook.xml", workbook(sheets))
            write(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            write(zip, "xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
                val writer = zip.writer(Charsets.UTF_8)
                worksheet(sheet, writer)
                writer.flush()
                zip.closeEntry()
            }
        }
    }

    private fun worksheet(sheet: Sheet, writer: java.io.Writer) {
        val lastColumn = columnName(sheet.headers.size)
        writer.write(XML + """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>""")
        sheet.headers.indices.forEach { index ->
            val width = when (index) { 0 -> 18; 2 -> 26; else -> 16 }
            writer.write("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        writer.write("</cols><sheetData>")
        fun row(number: Int, cells: List<Cell>) {
            writer.write("<row r=\"$number\">")
            cells.forEachIndexed { index, cell -> writer.write(cellXml("${columnName(index + 1)}$number", cell)) }
            writer.write("</row>")
        }
        row(1, sheet.headers.map { Cell.Header(it) })
        var lastRow = 1
        sheet.rows.forEach { cells -> lastRow++; row(lastRow, cells) }
        writer.write("</sheetData><autoFilter ref=\"A1:$lastColumn$lastRow\"/></worksheet>")
    }

    private fun cellXml(reference: String, cell: Cell): String = when (cell) {
        is Cell.Header -> "<c r=\"$reference\" t=\"inlineStr\" s=\"1\"><is><t>${escape(cell.value)}</t></is></c>"
        is Cell.Text -> "<c r=\"$reference\" t=\"inlineStr\"><is><t>${escape(cell.value)}</t></is></c>"
        is Cell.Number -> "<c r=\"$reference\" s=\"2\"><v>${cell.value}</v></c>"
    }

    private fun workbook(sheets: List<Sheet>) = XML + sheets.mapIndexed { index, sheet ->
        "<sheet name=\"${escape(sheet.name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>"
    }.joinToString("", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>", "</sheets></workbook>")

    private fun workbookRels(count: Int) = XML + (1..count).joinToString("", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">", "<Relationship Id=\"rId${count + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>") { index ->
        "<Relationship Id=\"rId$index\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$index.xml\"/>"
    }

    private fun contentTypes(count: Int) = XML + (1..count).joinToString("", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>", "</Types>") { index ->
        "<Override PartName=\"/xl/worksheets/sheet$index.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
    }

    private fun write(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun major(minor: Long, currencyCode: String): String = minor.toBigDecimal()
        .movePointLeft(Currency.getInstance(currencyCode).defaultFractionDigits).toPlainString()

    private fun columnName(number: Int): String {
        var value = number
        var result = ""
        while (value > 0) { value--; result = ('A'.code + value % 26).toChar() + result; value /= 26 }
        return result
    }

    private fun escape(value: String) = value.filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private data class Sheet(val name: String, val headers: List<String>, val rows: Sequence<List<Cell>>)
    private sealed interface Cell {
        data class Header(val value: String) : Cell
        data class Text(val value: String) : Cell
        data class Number(val value: String) : Cell
    }

    companion object {
        private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00;[Red]-#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>"""
    }
}
