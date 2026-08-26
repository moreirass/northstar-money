package com.northstar.money.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun widthBreakpointsCoverPhonesTabletsAndExpandedFoldables() {
        assertEquals(WindowWidthClass.COMPACT, classifyWindowWidth(599))
        assertEquals(WindowWidthClass.MEDIUM, classifyWindowWidth(600))
        assertEquals(WindowWidthClass.MEDIUM, classifyWindowWidth(839))
        assertEquals(WindowWidthClass.EXPANDED, classifyWindowWidth(840))
        assertEquals(WindowWidthClass.EXPANDED, classifyWindowWidth(1_400))
    }
}
