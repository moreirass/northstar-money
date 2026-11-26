package com.northstar.money.data.repository;

import androidx.annotation.Nullable;
import com.northstar.money.core.database.FinanceDao;

final class RepositoryValues {
    private RepositoryValues() {}

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    static <T> T present(@Nullable T value, String message) {
        require(value != null, message);
        return value;
    }

    static String trimmed(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean whitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    static void requireCurrency(FinanceDao dao, String id, String currency) {
        String stored = present(dao.getActiveAccountCurrency(id), "Account is missing or archived");
        require(stored.equals(currency), "Account uses " + stored + "; " + currency + " operations are not supported for it");
    }
}
