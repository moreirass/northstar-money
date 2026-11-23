package com.northstar.money.data.importing;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Leitura com limite antes de devolver o conteúdo ao importador. */
public final class BoundedStreams {
    private BoundedStreams() { }

    @NonNull
    public static byte[] read(@NonNull InputStream input, int maxBytes) throws IOException {
        if (maxBytes <= 0) throw new IllegalArgumentException("Failed requirement.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        while (true) {
            int count = input.read(buffer, 0, (int) Math.min(buffer.length, (long) maxBytes - total + 1));
            if (count < 0) break;
            if (count == 0) continue;
            total += count;
            if (total > maxBytes) throw new IllegalArgumentException("The selected file is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
