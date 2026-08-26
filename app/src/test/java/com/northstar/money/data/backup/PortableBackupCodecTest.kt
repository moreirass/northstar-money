package com.northstar.money.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupCodecTest {
    private val codec = PortableBackupCodec()

    @Test
    fun encryptAndDecrypt_roundTripsAndUsesFreshRandomness() {
        val password = "correct horse battery staple".toCharArray()
        try {
            val first = codec.encrypt(SENSITIVE_DATA, password)
            val second = codec.encrypt(SENSITIVE_DATA, password)

            assertNotEquals(first.toList(), second.toList())
            assertTrue(codec.isPortable(first))
            assertFalse(first.toString(Charsets.ISO_8859_1).contains(SENSITIVE_DATA))
            assertTrue(codec.decrypt(first, password) == SENSITIVE_DATA)
            assertTrue(codec.decrypt(second, password) == SENSITIVE_DATA)
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun decrypt_rejectsWrongPassword() {
        val correct = "correct horse battery staple".toCharArray()
        val wrong = "incorrect password phrase".toCharArray()
        try {
            val encrypted = codec.encrypt(SENSITIVE_DATA, correct)

            assertThrows(IllegalArgumentException::class.java) { codec.decrypt(encrypted, wrong) }
        } finally {
            correct.fill('\u0000')
            wrong.fill('\u0000')
        }
    }

    @Test
    fun decrypt_rejectsTamperedCiphertext() {
        val password = "correct horse battery staple".toCharArray()
        try {
            val encrypted = codec.encrypt(SENSITIVE_DATA, password).also { it[it.lastIndex] = (it.last() + 1).toByte() }

            assertThrows(IllegalArgumentException::class.java) { codec.decrypt(encrypted, password) }
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun encrypt_rejectsShortPassword() {
        val password = "too short".toCharArray()
        try {
            assertThrows(IllegalArgumentException::class.java) { codec.encrypt(SENSITIVE_DATA, password) }
        } finally {
            password.fill('\u0000')
        }
    }

    companion object {
        private const val SENSITIVE_DATA = "financial data that must remain private"
    }
}
