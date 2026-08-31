package com.northstar.money.data.exchange

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RateQuote(val date: String, val rateMicros: Long, val source: String)

fun interface HistoricalRateProvider {
    suspend fun getRate(base: String, quote: String, localDate: String): RateQuote
}

class HistoricalRateClient(
    private val endpoint: String = "https://api.frankfurter.dev/v2/rate",
) : HistoricalRateProvider {
    override suspend fun getRate(base: String, quote: String, localDate: String): RateQuote = withContext(Dispatchers.IO) {
        require(base.length == 3 && quote.length == 3)
        if (base == quote) return@withContext RateQuote(localDate, 1_000_000L, SOURCE)
        val encodedDate = URLEncoder.encode(localDate, StandardCharsets.UTF_8.name())
        val connection = URI("$endpoint/$base/$quote?date=$encodedDate").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            require(connection.responseCode in 200..299) { "Exchange-rate service returned HTTP ${connection.responseCode}" }
            parseQuote(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SOURCE = "Frankfurter"

        fun parseQuote(json: String): RateQuote {
            val objectValue = Json.parseToJsonElement(json).jsonObject
            val rate = objectValue.getValue("rate").jsonPrimitive.content.toBigDecimal()
            return RateQuote(
                date = objectValue.getValue("date").jsonPrimitive.content,
                rateMicros = rate.movePointRight(6).setScale(0, RoundingMode.HALF_EVEN).longValueExact(),
                source = SOURCE,
            )
        }

        fun convertMinor(amountMinor: Long, base: String, quote: String, rateMicros: Long): Long {
            val baseScale = Currency.getInstance(base).defaultFractionDigits
            val quoteScale = Currency.getInstance(quote).defaultFractionDigits
            return BigDecimal.valueOf(amountMinor)
                .movePointLeft(baseScale)
                .multiply(BigDecimal.valueOf(rateMicros, 6))
                .movePointRight(quoteScale)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact()
        }
    }
}
