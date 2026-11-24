package com.northstar.money.data.importing

import java.io.InputStream

object ImportLimits {
    const val RECEIPT_BYTES = 8 * 1024 * 1024
    const val CSV_BYTES = 16 * 1024 * 1024
    const val BACKUP_BYTES = 64 * 1024 * 1024
    const val ROWS = 100_000
    const val FIELD_CHARS = 4_096
}

fun InputStream.readBounded(maxBytes: Int): ByteArray {
    return BoundedStreams.read(this, maxBytes)
}
