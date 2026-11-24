package com.northstar.money.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;


/** Montante imutável em unidades mínimas; não utiliza vírgula flutuante. */
public final class Money {
    private final long minor;
    private final String currencyCode;

    public Money(long minor) { this(minor, "EUR"); }

    public Money(long minor, @NonNull String currencyCode) {
        if (!CurrencyMath.validCurrency(currencyCode)) {
            throw new IllegalArgumentException("Unsupported monetary currency: " + currencyCode);
        }
        this.minor = minor;
        this.currencyCode = currencyCode;
    }

    public long getMinor() { return minor; }
    @NonNull public String getCurrencyCode() { return currencyCode; }

    @NonNull
    public Money plus(@NonNull Money other) {
        if (!currencyCode.equals(other.currencyCode)) throw new IllegalArgumentException("Failed requirement.");
        return new Money(Math.addExact(minor, other.minor), currencyCode);
    }

    @NonNull public String formatted() { return formatted(Locale.getDefault()); }

    @NonNull
    public String formatted(@NonNull Locale locale) {
        Currency currency = Currency.getInstance(currencyCode);
        BigDecimal divisor = BigDecimal.TEN.pow(currency.getDefaultFractionDigits());
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        return format.format(BigDecimal.valueOf(minor).divide(divisor));
    }

    @NonNull
    public static Money parseMajor(@NonNull String text) { return parseMajor(text, "EUR"); }

    @NonNull
    public static Money parseMajor(@NonNull String text, @NonNull String currencyCode) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        String normalized = CurrencyMath.trimWhitespace(text).replace(',', '.');
        long minor = new BigDecimal(normalized).movePointRight(digits)
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        return new Money(minor, currencyCode);
    }

    @Override public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof Money)) return false;
        Money amount = (Money) other;
        return minor == amount.minor && currencyCode.equals(amount.currencyCode);
    }

    @Override public int hashCode() { return 31 * Long.hashCode(minor) + currencyCode.hashCode(); }

    @Override @NonNull public String toString() {
        return "Money(minor=" + minor + ", currencyCode=" + currencyCode + ")";
    }
}
