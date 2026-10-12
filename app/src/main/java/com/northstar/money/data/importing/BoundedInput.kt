package com.northstar.money.data.importing

import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImportLimits {
    const val RECEIPT_BYTES = 8 * 1024 * 1024
    const val CSV_BYTES = 16 * 1024 * 1024
    const val BACKUP_BYTES = 64 * 1024 * 1024
    const val ROWS = 100_000
    const val FIELD_CHARS = 4_096
}

fun InputStream.readBounded(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    return ByteArrayOutputStream().use { output ->
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= maxBytes) { "The selected file is too large" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
