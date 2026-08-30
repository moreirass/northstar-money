package com.northstar.money.core.navigation

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetsScreenTest {
    @Test
    fun statusColorChangesAtWarningAndOverspentThresholds() {
        assertEquals(Color(0xFF10B981), budgetStatusColor(69))
        assertEquals(Color(0xFFF59E0B), budgetStatusColor(70))
        assertEquals(Color(0xFFF59E0B), budgetStatusColor(99))
        assertEquals(Color(0xFFF43F5E), budgetStatusColor(100))
    }

    @Test
    fun usagePercentageHandlesZeroAndRoundsNormally() {
        assertEquals(0, budgetUsagePercent(spent = 2_500, planned = 0))
        assertEquals(75, budgetUsagePercent(spent = 7_500, planned = 10_000))
        assertEquals(125, budgetUsagePercent(spent = 12_500, planned = 10_000))
    }
}
