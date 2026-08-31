package com.northstar.money.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalRateClientTest {
    @Test
    fun `parses quote and converts currencies with different minor units`() {
        val quote = HistoricalRateClient.parseQuote(
            """{"date":"2026-08-26","base":"JPY","quote":"EUR","rate":0.0058123}""",
        )

        assertEquals("2026-08-26", quote.date)
        assertEquals(5_812L, quote.rateMicros)
        assertEquals(581L, HistoricalRateClient.convertMinor(1000, "JPY", "EUR", quote.rateMicros))
    }
}
