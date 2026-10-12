package com.northstar.money.data.exchange

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Currency
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.northstar.money.data.importing.readBounded
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
    private val cache = object : LinkedHashMap<String, Pair<RateQuote, Long>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<RateQuote, Long>>?): Boolean = size > 256
    }
    private val failedRequests = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 256
    }
    private val requestLocks = Array(32) { Mutex() }

    override suspend fun getRate(base: String, quote: String, localDate: String): RateQuote {
        require(com.northstar.money.domain.model.validCurrency(base) && com.northstar.money.domain.model.validCurrency(quote))
        java.time.LocalDate.parse(localDate)
        val key = "$base/$quote/$localDate"
        return requestLocks[(key.hashCode() and Int.MAX_VALUE) % requestLocks.size].withLock {
            synchronized(cache) { cache[key] }?.takeIf {
                java.time.LocalDate.parse(localDate).isBefore(java.time.LocalDate.now()) || System.currentTimeMillis() - it.second < 300_000
            }?.let { return@withLock it.first }
            val failedAt = synchronized(failedRequests) { failedRequests[key] }
            if (failedAt != null && System.currentTimeMillis() - failedAt < 30_000) throw java.io.IOException("Exchange-rate service temporarily unavailable")
            val fetched = try { fetchRate(base, quote, localDate) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { synchronized(failedRequests) { failedRequests[key] = System.currentTimeMillis() }; throw error }
            synchronized(failedRequests) { failedRequests.remove(key) }
            // Today's quotes expire after five minutes; historical days remain cached.
            synchronized(cache) { cache[key] = fetched to System.currentTimeMillis() }
            fetched
        }
    }

    private suspend fun fetchRate(base: String, quote: String, localDate: String): RateQuote = withContext(Dispatchers.IO) {
        require(base.length == 3 && quote.length == 3)
        if (base == quote) return@withContext RateQuote(localDate, 1_000_000L, SOURCE)
        val encodedDate = URLEncoder.encode(localDate, StandardCharsets.UTF_8.name())
        val connection = URI("$endpoint/$base/$quote?date=$encodedDate").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            require(connection.responseCode in 200..299) { "Exchange-rate service returned HTTP ${connection.responseCode}" }
            val result = parseQuote(connection.inputStream.use { it.readBounded(4096).toString(Charsets.UTF_8) })
            currentCoroutineContext().ensureActive()
            require(!java.time.LocalDate.parse(result.date).isAfter(java.time.LocalDate.parse(localDate))) { "Exchange rate date is in the future" }
            result
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val SOURCE = "Frankfurter"

        fun parseQuote(json: String): RateQuote {
            val objectValue = Json.parseToJsonElement(json).jsonObject
            val rate = objectValue.getValue("rate").jsonPrimitive.content.toBigDecimal()
            require(rate.signum() > 0 && rate.movePointRight(6).setScale(0, RoundingMode.HALF_EVEN).longValueExact() > 0) { "Invalid exchange rate" }
            java.time.LocalDate.parse(objectValue.getValue("date").jsonPrimitive.content)
            return RateQuote(
                date = objectValue.getValue("date").jsonPrimitive.content,
                rateMicros = rate.movePointRight(6).setScale(0, RoundingMode.HALF_EVEN).longValueExact(),
                source = SOURCE,
            )
        }

        fun convertMinor(amountMinor: Long, base: String, quote: String, rateMicros: Long): Long =
            com.northstar.money.domain.model.convertCurrencyMinor(amountMinor, base, quote, rateMicros)

    }
}
