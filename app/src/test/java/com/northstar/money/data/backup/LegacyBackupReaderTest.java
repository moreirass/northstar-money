package com.northstar.money.data.backup;

import static org.junit.Assert.*;
import com.northstar.money.data.importing.ImportLimits;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class LegacyBackupReaderTest {
    private final SecretKey key = new SecretKeySpec(new byte[32], "AES");

    private byte[] legacyPayload() throws Exception {
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal("synthetic data".getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(2 + iv.length + encrypted.length)
                .put((byte) 1).put((byte) iv.length).put(iv).put(encrypted).array();
    }

    @Test public void readsExistingDeviceBackup() throws Exception {
        assertEquals("synthetic data", new LegacyBackupReader(() -> key).decrypt(legacyPayload()));
    }

    @Test public void missingKeyHasExplicitError() throws Exception {
        byte[] payload = legacyPayload();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new LegacyBackupReader(() -> null).decrypt(payload));
        assertTrue(error.getMessage().contains("original device key is unavailable"));
    }

    @Test public void rejectsOversizedAndTruncatedPayloadsBeforeKeyAccess() {
        AtomicInteger loads = new AtomicInteger();
        LegacyBackupReader reader = new LegacyBackupReader(() -> { loads.incrementAndGet(); return key; });
        assertThrows(IllegalArgumentException.class, () -> reader.decrypt(new byte[ImportLimits.BACKUP_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> reader.decrypt(new byte[] {1, 12}));
        assertEquals(0, loads.get());
    }

    @Test public void rejectsTampering() throws Exception {
        byte[] payload = legacyPayload();
        payload[payload.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> new LegacyBackupReader(() -> key).decrypt(payload));
    }

    @Test public void facadeRoutesBothFormatsWithoutLoadingLegacyKeyForPortableBackups() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        SecureBackupCodec codec = new SecureBackupCodec(new PortableBackupCodec(),
                new LegacyBackupReader(() -> { loads.incrementAndGet(); return key; }));
        char[] password = "synthetic-password".toCharArray();
        assertEquals("portable", codec.decrypt(codec.encrypt("portable", password), password));
        assertEquals(0, loads.get());
        assertEquals("synthetic data", codec.decrypt(legacyPayload(), password));
        assertEquals(1, loads.get());
    }
}
