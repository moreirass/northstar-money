package com.northstar.money.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PortableBackupCodec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(plainText: String, password: CharArray): ByteArray {
        validatePassword(password)
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val header = header(PBKDF2_ITERATIONS, salt.size, iv.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        cipher.updateAAD(header)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return header + salt + iv + encrypted
    }

    fun decrypt(payload: ByteArray, password: CharArray): String {
        validatePassword(password)
        require(payload.size >= HEADER_SIZE + SALT_SIZE + IV_SIZE + TAG_SIZE_BYTES) {
            "Backup is truncated"
        }
        require(isPortable(payload)) { "Unsupported backup format" }

        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        val version = buffer.get().toInt() and 0xff
        val kdf = buffer.get().toInt() and 0xff
        val iterations = buffer.int
        val saltSize = buffer.get().toInt() and 0xff
        val ivSize = buffer.get().toInt() and 0xff
        require(magic.contentEquals(MAGIC) && version == ENVELOPE_VERSION) { "Unsupported backup format" }
        require(kdf == KDF_PBKDF2_SHA256) { "Unsupported password derivation algorithm" }
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "Invalid password work factor" }
        require(saltSize in 16..32 && ivSize == IV_SIZE) { "Invalid backup encryption parameters" }
        require(payload.size >= HEADER_SIZE + saltSize + ivSize + TAG_SIZE_BYTES) { "Backup is truncated" }

        val salt = ByteArray(saltSize).also(buffer::get)
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val key = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
            cipher.updateAAD(payload.copyOfRange(0, HEADER_SIZE))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect password or corrupted backup", error)
        }
    }

    fun isPortable(payload: ByteArray): Boolean =
        payload.size >= HEADER_SIZE && payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_SIZE_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun validatePassword(password: CharArray) {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must contain between $MIN_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters"
        }
    }

    private fun header(iterations: Int, saltSize: Int, ivSize: Int): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .put(ENVELOPE_VERSION.toByte())
            .put(KDF_PBKDF2_SHA256.toByte())
            .putInt(iterations)
            .put(saltSize.toByte())
            .put(ivSize.toByte())
            .array()

    companion object {
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte())
        private const val ENVELOPE_VERSION = 2
        private const val KDF_PBKDF2_SHA256 = 1
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ITERATIONS = 600_000
        private const val MIN_ACCEPTED_ITERATIONS = 100_000
        private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
        private const val KEY_SIZE_BITS = 256
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
        private const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        private const val HEADER_SIZE = 12
        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_PASSWORD_LENGTH = 128
    }
}
