package com.northstar.money.data.backup

import java.io.ByteArrayOutputStream
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
}
