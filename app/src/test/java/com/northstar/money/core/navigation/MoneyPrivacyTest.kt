package com.northstar.money.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyPrivacyTest {
    @Test
    fun visibleValueKeepsItsFormattedAmount() {
        assertEquals("€12.34", moneyDisplayValue(false, "€12.34", "••••"))
    }

    @Test
    fun hiddenValueUsesThePrivacyPlaceholder() {
        assertEquals("••••", moneyDisplayValue(true, "€12.34", "••••"))
    }
}
