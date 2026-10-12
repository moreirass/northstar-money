package com.northstar.money.data.exporting

import com.northstar.money.domain.model.TransactionItem
import java.io.Writer

object CsvExporter {
    // Only textual columns are neutralized; signed numeric amounts stay numeric.
    fun textCell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    fun write(writer: Writer, transactions: List<TransactionItem>) {
        writer.write("date,type,payee,category,account,amount,currency\n")
        transactions.forEach { item ->
            writer.write(listOf(
                textCell(item.localDate), textCell(item.kind.name), textCell(item.payee),
                textCell(item.categoryName.orEmpty()), textCell(item.accountName),
                item.amount.minor.toString(), textCell(item.amount.currencyCode),
            ).joinToString(",") + "\n")
        }
    }
}
