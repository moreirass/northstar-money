package com.northstar.money.domain.model;

import androidx.annotation.NonNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.Locale;

/** Regras monetárias e de calendário independentes da interface Android. */
public final class CurrencyMath {
    private CurrencyMath() { }

    public static boolean validCurrency(@NonNull String code) {
        if (code.length() != 3 || !code.equals(code.toUpperCase(Locale.ROOT))) return false;
        try {
            int digits = Currency.getInstance(code).getDefaultFractionDigits();
            return digits >= 0 && digits <= 4;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public static long convertCurrencyMinor(long amountMinor, @NonNull String base,
            @NonNull String quote, long rateMicros) {
        if (!validCurrency(base) || !validCurrency(quote) || rateMicros <= 0) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        return BigDecimal.valueOf(amountMinor)
                .movePointLeft(Currency.getInstance(base).getDefaultFractionDigits())
                .multiply(BigDecimal.valueOf(rateMicros, 6))
                .movePointRight(Currency.getInstance(quote).getDefaultFractionDigits())
                .setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }

    @NonNull
    public static LocalDate nextOccurrence(@NonNull LocalDate date, @NonNull String frequency,
            int interval, int anchorDay, int anchorMonth) {
        if (interval <= 0 || anchorDay < 1 || anchorDay > 31 || anchorMonth < 1 || anchorMonth > 12) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        YearMonth month;
        switch (frequency) {
            case "WEEKLY": return date.plusWeeks(interval);
            case "MONTHLY": month = YearMonth.from(date).plusMonths(interval); break;
            case "YEARLY": month = YearMonth.of(Math.addExact(date.getYear(), interval), anchorMonth); break;
            default: throw new IllegalStateException("Unsupported recurring frequency");
        }
        return month.atDay(Math.min(anchorDay, month.lengthOfMonth()));
    }

    /** Kotlin trim inclui espaços Unicode que String.trim não remove. */
    @NonNull
    static String trimWhitespace(@NonNull String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isWhitespace(text.charAt(start))) start++;
        while (end > start && isWhitespace(text.charAt(end - 1))) end--;
        return text.substring(start, end);
    }

    private static boolean isWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
