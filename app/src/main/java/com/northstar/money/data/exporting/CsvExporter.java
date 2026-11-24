package com.northstar.money.data.exporting;

import androidx.annotation.NonNull;
import com.northstar.money.domain.model.TransactionItem;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Mantém o cabeçalho, os sinais monetários e a neutralização de fórmulas existentes. */
public final class CsvExporter {
    private CsvExporter() { }

    @NonNull
    public static String textCell(@NonNull String value) {
        int start = 0;
        while (start < value.length() && (Character.isWhitespace(value.charAt(start))
                || Character.isSpaceChar(value.charAt(start)))) start++;
        boolean formula = start < value.length() && "=+-@".indexOf(value.charAt(start)) >= 0;
        boolean control = !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0;
        String safe = formula || control ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public static void write(@NonNull Writer writer, @NonNull List<TransactionItem> transactions)
            throws IOException {
        writer.write("date,type,payee,category,account,amount,currency\n");
        for (TransactionItem item : transactions) {
            writer.write(textCell(item.getLocalDate()) + "," + textCell(item.getKind().name()) + ","
                    + textCell(item.getPayee()) + ","
                    + textCell(item.getCategoryName() == null ? "" : item.getCategoryName()) + ","
                    + textCell(item.getAccountName()) + "," + item.getAmount().getMinor() + ","
                    + textCell(item.getAmount().getCurrencyCode()) + "\n");
        }
    }
}
