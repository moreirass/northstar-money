package com.northstar.money.data.importing

import com.northstar.money.core.database.AccountBalanceRow
import com.northstar.money.core.database.CategoryEntity
import com.northstar.money.domain.model.TransactionKind
import java.time.LocalDate

data class ValidatedCsvTransaction(
    val localDate: String,
    val kind: TransactionKind,
    val payee: String,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val currencyCode: String,
)

data class CsvValidationResult(
    val transactions: List<ValidatedCsvTransaction>,
    val skippedDuplicates: Int,
    val errors: Int,
)

class TransactionCsvValidator {
    fun validate(
        csv: String,
        accounts: List<AccountBalanceRow>,
        categories: List<CategoryEntity>,
    ): CsvValidationResult {
        if (csv.length > ImportLimits.CSV_BYTES) return CsvValidationResult(emptyList(), 0, 1)
        val accountsByName = accounts.groupBy { it.name }
        val categoriesByName = categories.groupBy { it.name to it.kind }
        val rows = try {
            parseCsv(csv)
        } catch (_: IllegalArgumentException) {
            return CsvValidationResult(emptyList(), 0, 1)
        }
        if (rows.isEmpty() || rows.size - 1 > MAX_ROWS) return CsvValidationResult(emptyList(), 0, 1)
        val header = rows.first().toMutableList().also {
            if (it.isNotEmpty()) it[0] = it[0].removePrefix("\uFEFF")
        }
        if (header != HEADER) return CsvValidationResult(emptyList(), 0, 1)

        val validated = mutableListOf<ValidatedCsvTransaction>()
        val signatures = mutableSetOf<ImportSignature>()
        var duplicates = 0
        var errors = 0
        rows.drop(1).filterNot { row -> row.all(String::isBlank) }.forEach { columns ->
            val transaction = try {
                validateRow(columns, accountsByName, categoriesByName)
            } catch (_: RuntimeException) {
                null
            }
            if (transaction == null) {
                errors++
            } else {
                val signature = ImportSignature(
                    transaction.localDate,
                    transaction.payee,
                    transaction.accountId,
                    transaction.categoryId,
                    transaction.amountMinor,
                    transaction.currencyCode,
                )
                if (signatures.add(signature)) validated += transaction else duplicates++
            }
        }
        return CsvValidationResult(validated, duplicates, errors)
    }

    private fun validateRow(
        columns: List<String>,
        accounts: Map<String, List<AccountBalanceRow>>,
        categories: Map<Pair<String, String>, List<CategoryEntity>>,
    ): ValidatedCsvTransaction {
        require(columns.size == HEADER.size && columns.all { it.length <= ImportLimits.FIELD_CHARS })
        val date = LocalDate.parse(columns[0].trim()).toString()
        val kind = TransactionKind.valueOf(columns[1].trim())
        require(kind != TransactionKind.TRANSFER)
        val payee = columns[2].trim().removePrefix("'").takeIf { columns[2].trimStart().startsWith("'") && columns[2].trimStart().drop(1).trimStart().firstOrNull() in listOf('=', '+', '-', '@') } ?: columns[2].trim()
        val categoryName = decodedText(columns[3])
        val accountName = decodedText(columns[4])
        val matchingAccounts = accounts[accountName].orEmpty()
        require(matchingAccounts.size == 1)
        val account = matchingAccounts.single()
        val matchingCategories = categories[categoryName to kind.name].orEmpty()
        require(matchingCategories.size == 1)
        val category = matchingCategories.single()
        val amountMinor = columns[5].trim().toLong()
        require(
            (kind == TransactionKind.EXPENSE && amountMinor < 0 && amountMinor != Long.MIN_VALUE) ||
                (kind == TransactionKind.INCOME && amountMinor > 0),
        )
        val currencyCode = columns[6].trim()
        require(com.northstar.money.domain.model.validCurrency(currencyCode))
        require(currencyCode == account.currencyCode)
        return ValidatedCsvTransaction(
            localDate = date,
            kind = kind,
            payee = payee,
            accountId = account.id,
            categoryId = category.id,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
        )
    }

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var closedQuote = false
        var index = 0

        fun finishField() {
            require(row.size < HEADER.size) { "Too many CSV columns" }
            require(field.length <= ImportLimits.FIELD_CHARS) { "CSV field is too long" }
            row += field.toString()
            field.clear()
            closedQuote = false
        }

        fun finishRow() {
            finishField()
            require(rows.size <= MAX_ROWS) { "Too many CSV rows" }
            rows += row.toList()
            row.clear()
        }

        while (index < csv.length) {
            require(field.length <= ImportLimits.FIELD_CHARS) { "CSV field is too long" }
            val char = csv[index]
            when {
                inQuotes && char == '"' && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                inQuotes && char == '"' -> {
                    inQuotes = false
                    closedQuote = true
                }
                inQuotes -> field.append(char)
                char == '"' && field.isEmpty() -> inQuotes = true
                char == ',' -> finishField()
                char == '\n' -> finishRow()
                char == '\r' -> {
                    if (index + 1 < csv.length && csv[index + 1] == '\n') index++
                    finishRow()
                }
                closedQuote -> throw IllegalArgumentException("Unexpected character after closing quote")
                else -> field.append(char)
            }
            index++
        }
        require(!inQuotes) { "Unclosed quoted field" }
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
        return rows.filterNot { candidate -> candidate.size == 1 && candidate.single().isEmpty() }
    }

    private fun decodedText(value: String): String {
        val trimmed = value.trim()
        val original = trimmed.removePrefix("'")
        return if (trimmed.startsWith("'") && (original.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || original.firstOrNull() in listOf('\t', '\r', '\n'))) original else trimmed
    }

    private data class ImportSignature(
        val localDate: String,
        val payee: String,
        val accountId: String,
        val categoryId: String,
        val amountMinor: Long,
        val currencyCode: String,
    )

    companion object {
        private val HEADER = listOf("date", "type", "payee", "category", "account", "amount", "currency")
        private const val MAX_ROWS = ImportLimits.ROWS
    }
}
