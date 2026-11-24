package com.northstar.money.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.northstar.money.core.database.DatabaseSnapshot;
import com.northstar.money.data.importing.ImportLimits;
import com.northstar.money.domain.model.CurrencyMath;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class FullBackupJsonCodec {
    public static final String FORMAT = "northstar-money-database-backup";
    public static final int FORMAT_VERSION = 3;

    public void encodeTo(@NonNull OutputStream output, @NonNull DatabaseSnapshot snapshot,
            int databaseVersion, @NonNull String currency) throws IOException {
        BackupJsonWriter.write(new OutputStreamWriter(output, StandardCharsets.UTF_8),
                document(snapshot, databaseVersion, System.currentTimeMillis(), currency));
    }

    @NonNull
    public String encode(@NonNull DatabaseSnapshot snapshot, int databaseVersion) {
        return encode(snapshot, databaseVersion, System.currentTimeMillis(), null);
    }

    @NonNull
    public String encode(@NonNull DatabaseSnapshot snapshot, int databaseVersion, long createdAt,
            @Nullable String currency) {
        StringWriter output = new StringWriter();
        try {
            BackupJsonWriter.write(output, document(snapshot, databaseVersion, createdAt, currency));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return output.toString();
    }

    @NonNull
    public FullBackupDocument decode(@NonNull String encoded) {
        if (encoded.length() > ImportLimits.BACKUP_BYTES) throw new IllegalArgumentException("Backup is too large");
        FullBackupDocument document = BackupJsonReader.read(encoded);
        if (!FORMAT.equals(document.getFormat())) throw new IllegalArgumentException("Not a Northstar Money database backup");
        if (document.getFormatVersion() < 1 || document.getFormatVersion() > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported backup format version " + document.getFormatVersion());
        }
        String currency = document.getBaseCurrencyCode();
        if (currency != null && !CurrencyMath.validCurrency(currency)) throw new IllegalArgumentException("Invalid backup currency");
        if (document.getDatabaseVersion() <= 0) throw new IllegalArgumentException("Invalid database version");
        return document;
    }

    private static FullBackupDocument document(DatabaseSnapshot snapshot, int version, long createdAt, String currency) {
        return new FullBackupDocument(FORMAT, FORMAT_VERSION, version, createdAt, snapshot.getAccounts(),
                snapshot.getAccountExchangeRates(), currency, snapshot.getCategories(), snapshot.getTransactions(),
                snapshot.getTransactionEntries(), snapshot.getReconciliations(), snapshot.getBudgetAllocations(),
                snapshot.getGoals(), snapshot.getGoalContributions(), snapshot.getRecurringSchedules(),
                snapshot.getDebtProfiles(), snapshot.getReceiptAttachments(), snapshot.getTransactionExchangeRates());
    }
}
