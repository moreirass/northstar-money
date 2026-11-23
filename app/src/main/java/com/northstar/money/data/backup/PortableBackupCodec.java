package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
import com.northstar.money.data.importing.ImportLimits;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Envelope portátil NSMB v2, compatível com a implementação Kotlin original. */
public final class PortableBackupCodec {
    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 128;
    private static final byte[] MAGIC = {'N', 'S', 'M', 'B'};
    private static final int HEADER_SIZE = 12;
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12;
    private static final int ITERATIONS = 600_000;
    private final SecureRandom random;

    public PortableBackupCodec() { this(new SecureRandom()); }

    public PortableBackupCodec(@NonNull SecureRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    @NonNull
    public byte[] encrypt(@NonNull String plaintext, @NonNull char[] password)
            throws GeneralSecurityException {
        validatePassword(password);
        require(plaintext.length() <= ImportLimits.BACKUP_BYTES - 64, "Backup is too large");
        byte[] salt = randomBytes(SALT_SIZE);
        byte[] iv = randomBytes(IV_SIZE);
        byte[] header = header();
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv, ITERATIONS, header);
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= ImportLimits.BACKUP_BYTES - 64, "Backup is too large");
        try {
            byte[] encrypted = cipher.doFinal(bytes);
            return ByteBuffer.allocate(header.length + salt.length + iv.length + encrypted.length)
                    .put(header).put(salt).put(iv).put(encrypted).array();
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @NonNull
    public String decrypt(@NonNull byte[] payload, @NonNull char[] password)
            throws GeneralSecurityException {
        validatePassword(password);
        require(payload.length <= ImportLimits.BACKUP_BYTES, "Backup is too large");
        require(payload.length >= HEADER_SIZE + SALT_SIZE + IV_SIZE + 16, "Backup is truncated");
        require(isPortable(payload), "Unsupported backup format");
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.position(MAGIC.length);
        int version = Byte.toUnsignedInt(buffer.get());
        int kdf = Byte.toUnsignedInt(buffer.get());
        int iterations = buffer.getInt();
        int saltSize = Byte.toUnsignedInt(buffer.get());
        int ivSize = Byte.toUnsignedInt(buffer.get());
        require(version == 2, "Unsupported backup format");
        require(kdf == 1, "Unsupported password derivation algorithm");
        require(iterations >= 100_000 && iterations <= 2_000_000, "Invalid password work factor");
        require(saltSize >= 16 && saltSize <= 32 && ivSize == IV_SIZE, "Invalid backup encryption parameters");
        require(payload.length >= HEADER_SIZE + saltSize + ivSize + 16, "Backup is truncated");
        byte[] salt = new byte[saltSize];
        byte[] iv = new byte[ivSize];
        buffer.get(salt).get(iv);
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations,
                Arrays.copyOf(payload, HEADER_SIZE));
        try {
            byte[] plaintext = cipher.doFinal(payload, buffer.position(), buffer.remaining());
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (AEADBadTagException error) {
            throw new IllegalArgumentException("Incorrect password or corrupted backup", error);
        }
    }

    public boolean isPortable(@NonNull byte[] payload) {
        if (payload.length < HEADER_SIZE) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[i] != MAGIC[i]) return false;
        }
        return true;
    }

    @FunctionalInterface
    public interface PlaintextWriter {
        void write(@NonNull OutputStream output) throws IOException;
    }

    /** O limite inclui cabeçalho, salt, IV e etiqueta de autenticação. */
    public void encryptTo(@NonNull OutputStream output, @NonNull char[] password,
            @NonNull PlaintextWriter writer) throws GeneralSecurityException, IOException {
        validatePassword(password);
        byte[] salt = randomBytes(SALT_SIZE);
        byte[] iv = randomBytes(IV_SIZE);
        byte[] header = header();
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv, ITERATIONS, header);
        OutputStream limited = new FilterOutputStream(output) {
            private long count;
            @Override public void write(int value) throws IOException {
                require(++count <= ImportLimits.BACKUP_BYTES, "Backup is too large");
                out.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                count += length;
                require(count <= ImportLimits.BACKUP_BYTES, "Backup is too large");
                out.write(bytes, offset, length);
            }
        };
        limited.write(header);
        limited.write(salt);
        limited.write(iv);
        try (CipherOutputStream encrypted = new CipherOutputStream(limited, cipher)) {
            writer.write(encrypted);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] header() {
        return ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put((byte) 2).put((byte) 1)
                .putInt(ITERATIONS).put((byte) SALT_SIZE).put((byte) IV_SIZE).array();
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] iv,
            int iterations, byte[] header) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(mode, new SecretKeySpec(encoded, "AES"), new GCMParameterSpec(128, iv));
                cipher.updateAAD(header);
                return cipher;
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static void validatePassword(char[] password) {
        require(password.length >= MIN_PASSWORD_LENGTH && password.length <= MAX_PASSWORD_LENGTH,
                "Password must contain between 12 and 128 characters");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
