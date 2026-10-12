package com.northstar.money.data.importing

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsAnExactLimitAndStopsBeforeLoadingOversizedInput() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBounded(3))
        val input = ByteArrayInputStream(ByteArray(100_000))
        assertThrows(IllegalArgumentException::class.java) { input.readBounded(10) }
        assertEquals(99_989, input.available())
    }
}
