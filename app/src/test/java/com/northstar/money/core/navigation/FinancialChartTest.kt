package com.northstar.money.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialChartTest {
    @Test
    fun chartFractionHandlesEmptyAndClampsOverspending() {
        assertEquals(0f, chartFraction(100, 0), 0f)
        assertEquals(0f, chartFraction(-1, 100), 0f)
        assertEquals(0.5f, chartFraction(50, 100), 0.0001f)
        assertEquals(1f, chartFraction(150, 100), 0f)
    }

    @Test
    fun budgetPercentageRetainsOverspendingInformation() {
        assertEquals(0, budgetUsagePercent(100, 0))
        assertEquals(58, budgetUsagePercent(58, 100))
        assertEquals(125, budgetUsagePercent(125, 100))
    }
}
