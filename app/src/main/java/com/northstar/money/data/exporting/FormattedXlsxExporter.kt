package com.northstar.money.data.exporting

import com.northstar.money.domain.model.Account
import com.northstar.money.domain.model.BudgetProgress
import com.northstar.money.domain.model.FinanceSummary
import com.northstar.money.domain.model.HistoricalExchangeRate
import com.northstar.money.domain.model.TransactionItem
import java.io.ByteArrayOutputStream
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
        val sheets = listOf(
            Sheet("Transactions", listOf("Date", "Type", "Merchant", "Category", "Account", "Amount", "Currency"),
                transactions.map { transaction ->
                    listOf(
                        Cell.Text(transaction.localDate), Cell.Text(transaction.kind.name), Cell.Text(transaction.payee),
                        Cell.Text(transaction.categoryName.orEmpty()), Cell.Text(transaction.accountName),
                        Cell.Number(major(transaction.amount.minor, transaction.amount.currencyCode)),
                        Cell.Text(transaction.amount.currencyCode),
                    )
                }),
            Sheet("Accounts", listOf("Account", "Type", "Balance", "Cleared balance", "Currency"),
                accounts.map { account ->
                    listOf(
                        Cell.Text(account.name), Cell.Text(account.type.name),
                        Cell.Number(major(account.balance.minor, account.currencyCode)),
                        Cell.Number(major(account.clearedBalance.minor, account.currencyCode)), Cell.Text(account.currencyCode),
                    )
                }),
            Sheet("Budgets", listOf("Category", "Allocated", "Rollover", "Planned", "Spent", "Currency"),
                budgets.map { budget ->
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
            )),
            Sheet("Exchange rates", listOf("Date", "Base", "Quote", "Rate", "Source", "Status", "Transaction ID"),
                rates.map { rate -> listOf(
                    Cell.Text(rate.rateLocalDate), Cell.Text(rate.baseCurrencyCode), Cell.Text(rate.quoteCurrencyCode),
                    rate.rateMicros?.let { Cell.Number(it.toBigDecimal().movePointLeft(6).toPlainString()) } ?: Cell.Text(""),
                    Cell.Text(rate.source), Cell.Text(rate.status), Cell.Text(rate.transactionId),
                ) }),
        )
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                write(zip, "[Content_Types].xml", contentTypes(sheets.size))
                write(zip, "_rels/.rels", ROOT_RELS)
                write(zip, "xl/workbook.xml", workbook(sheets))
                write(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
                write(zip, "xl/styles.xml", STYLES)
                sheets.forEachIndexed { index, sheet -> write(zip, "xl/worksheets/sheet${index + 1}.xml", worksheet(sheet)) }
            }
            bytes.toByteArray()
        }
    }

    private fun worksheet(sheet: Sheet): String {
        val rows = buildList {
            add(sheet.headers.map { Cell.Header(it) })
            addAll(sheet.rows)
        }
        val lastColumn = columnName(sheet.headers.size)
        val xmlRows = rows.mapIndexed { rowIndex, row ->
            val number = rowIndex + 1
            row.mapIndexed { columnIndex, cell -> cellXml("${columnName(columnIndex + 1)}$number", cell) }
                .joinToString("", "<row r=\"$number\">", "</row>")
        }.joinToString("")
        val widths = sheet.headers.indices.joinToString("") { index ->
            val width = when (index) { 0 -> 18; 2 -> 26; else -> 16 }
            "<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>"
        }
        return XML + """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
            <cols>$widths</cols><sheetData>$xmlRows</sheetData>
            <autoFilter ref="A1:$lastColumn${rows.size.coerceAtLeast(1)}"/>
        </worksheet>""".trimIndent()
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

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Cell>>)
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
