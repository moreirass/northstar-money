package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.northstar.money.data.importing.ImportLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Leitura isolada de backups v1: nunca cria nem substitui chaves. */
public final class LegacyBackupReader {
    @FunctionalInterface
    public interface ExistingKeyProvider {
        @Nullable SecretKey load() throws GeneralSecurityException, IOException;
    }

    private final ExistingKeyProvider keys;

    public LegacyBackupReader(@NonNull ExistingKeyProvider keys) {
        this.keys = java.util.Objects.requireNonNull(keys);
    }

    @NonNull
    public String decrypt(@NonNull byte[] payload) throws GeneralSecurityException, IOException {
        if (payload.length > ImportLimits.BACKUP_BYTES) {
            throw new IllegalArgumentException("Backup is too large");
        }
        if (payload.length < 2 || payload[0] != 1) {
            throw new IllegalArgumentException("Unsupported backup format");
        }
        int ivSize = Byte.toUnsignedInt(payload[1]);
        if (ivSize < 12 || ivSize > 16 || payload.length < 2 + ivSize + 16) {
            throw new IllegalArgumentException("Legacy backup is truncated or has invalid encryption parameters");
        }
        SecretKey key = keys.load();
        if (key == null) {
            throw new IllegalArgumentException("The original device key is unavailable; this legacy backup cannot be restored");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128,
                Arrays.copyOfRange(payload, 2, 2 + ivSize)));
        try {
            byte[] plaintext = cipher.doFinal(payload, 2 + ivSize, payload.length - 2 - ivSize);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (AEADBadTagException error) {
            throw new IllegalArgumentException("Legacy backup is corrupted or belongs to another device key", error);
        }
    }
}
