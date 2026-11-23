package com.northstar.money.data.backup

import com.northstar.money.data.backup.reference.KotlinPortableBackupCodec
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.*
import org.junit.Test

class BackupJavaCompatibilityTest {
    private val password = "synthetic-test-password".toCharArray()
    private val plaintext = "{\"note\":\"Ação € 日本語\\n\",\"amountMinor\":-9223372036854775808}"

    private fun fixedRandom() = object : SecureRandom() {
        private var next = 0
        override fun nextBytes(bytes: ByteArray) {
            for (i in bytes.indices) bytes[i] = next++.toByte()
        }
    }

    @Test fun javaAndKotlinProduceIdenticalEnvelopesAndReadEachOther() {
        val kotlin = KotlinPortableBackupCodec(fixedRandom())
        val java = PortableBackupCodec(fixedRandom())
        val old = kotlin.encrypt(plaintext, password)
        val migrated = java.encrypt(plaintext, password)
        assertArrayEquals(old, migrated)
        assertEquals(plaintext, java.decrypt(old, password))
        assertEquals(plaintext, kotlin.decrypt(migrated, password))
    }

    @Test fun streamedJavaEnvelopeMatchesKotlinBytes() {
        val output = ByteArrayOutputStream()
        PortableBackupCodec(fixedRandom()).encryptTo(output, password) { it.write(plaintext.toByteArray()) }
        assertArrayEquals(KotlinPortableBackupCodec(fixedRandom()).encrypt(plaintext, password), output.toByteArray())
    }

    @Test fun bothReadersRejectAuthenticatedHeaderChanges() {
        val payload = KotlinPortableBackupCodec().encrypt(plaintext, password)
        // Mantém o fator de trabalho válido, mas invalida o AAD e a chave derivada.
        payload[9] = (payload[9].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { KotlinPortableBackupCodec().decrypt(payload, password) }
        assertThrows(IllegalArgumentException::class.java) { PortableBackupCodec().decrypt(payload, password) }
    }

    @Test fun bothReadersRejectInvalidParametersBeforeDerivingKey() {
        val payload = KotlinPortableBackupCodec().encrypt(plaintext, password)
        for (index in listOf(4, 5, 6, 10, 11)) {
            val invalid = payload.copyOf().also { it[index] = 127 }
            assertThrows(IllegalArgumentException::class.java) { KotlinPortableBackupCodec().decrypt(invalid, password) }
            assertThrows(IllegalArgumentException::class.java) { PortableBackupCodec().decrypt(invalid, password) }
        }
    }
}
