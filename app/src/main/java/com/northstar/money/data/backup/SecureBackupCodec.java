package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Objects;
import javax.crypto.SecretKey;

/** Escreve apenas NSMB v2 e preserva a leitura de backups v1 do dispositivo original. */
public final class SecureBackupCodec {
    private final PortableBackupCodec portableCodec;
    private final LegacyBackupReader legacyReader;

    public SecureBackupCodec() { this(new PortableBackupCodec()); }

    public SecureBackupCodec(@NonNull PortableBackupCodec portableCodec) {
        this(portableCodec, new LegacyBackupReader(() -> {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            Key key = store.getKey("northstar-backup-key-v1", null);
            return key instanceof SecretKey ? (SecretKey) key : null;
        }));
    }

    SecureBackupCodec(PortableBackupCodec portableCodec, LegacyBackupReader legacyReader) {
        this.portableCodec = Objects.requireNonNull(portableCodec);
        this.legacyReader = Objects.requireNonNull(legacyReader);
    }

    @NonNull
    public byte[] encrypt(@NonNull String plaintext, @NonNull char[] password) throws GeneralSecurityException {
        return portableCodec.encrypt(plaintext, password);
    }

    @NonNull
    public String decrypt(@NonNull byte[] payload, @NonNull char[] password)
            throws GeneralSecurityException, IOException {
        return portableCodec.isPortable(payload)
                ? portableCodec.decrypt(payload, password) : legacyReader.decrypt(payload);
    }
}
