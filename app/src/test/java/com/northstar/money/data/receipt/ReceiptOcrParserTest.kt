package com.northstar.money.data.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptOcrParserTest {
    @org.junit.Test
    fun invalidCalendarDateDoesNotAbortAmountExtraction() {
        val parsed = ReceiptOcrParser.parse("Shop\n31/02/2026\nTOTAL 10,00", "EUR")
        org.junit.Assert.assertNull(parsed.localDate)
        org.junit.Assert.assertEquals(1000L, parsed.amount?.minor)
    }
    @Test
    fun `extracts merchant date and prioritized total`() {
        val parsed = ReceiptOcrParser.parse(
            """Mercado da Praça
               |26/08/2026
               |Subtotal 10,00
               |TOTAL EUR 12,34
            """.trimMargin(),
            "EUR",
        )

        assertEquals("Mercado da Praça", parsed.merchant)
        assertEquals("2026-08-26", parsed.localDate)
        assertEquals(1234L, parsed.amount?.minor)
    }
}
