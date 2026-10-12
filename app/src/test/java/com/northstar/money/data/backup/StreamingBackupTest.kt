package com.northstar.money.data.backup

import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StreamingBackupTest {
    @Test fun streamedEncryptionIsCompatibleWithPortableRestoreAndAuthenticated() {
        val codec = PortableBackupCodec()
        val password = "a-long-test-password".toCharArray()
        val output = ByteArrayOutputStream()
        codec.encryptTo(output, password) { it.write("{\"hello\":\"world\"}".toByteArray()) }
        val encrypted = output.toByteArray()
        assertEquals("{\"hello\":\"world\"}", codec.decrypt(encrypted, password))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { codec.decrypt(encrypted, password) }
    }

    @Test fun compactReceiptEncodingAcceptsLegacyNumericArrays() {
        val bytes = byteArrayOf(0, 1, -1)
        val encoded = Json.encodeToString(ReceiptBytesSerializer, bytes)
        assertEquals("\"AAH/\"", encoded)
        assertArrayEquals(bytes, Json.decodeFromString(ReceiptBytesSerializer, encoded))
        assertArrayEquals(bytes, Json.decodeFromString(ReceiptBytesSerializer, "[0,1,-1]"))
        assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString(ReceiptBytesSerializer, "[256]")
        }
    }
}
