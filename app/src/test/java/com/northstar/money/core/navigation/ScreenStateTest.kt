package com.northstar.money.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenStateTest {
    @Test
    fun loadingTakesPrecedenceAndErrorCanRecoverToContent() {
        assertEquals(FinanceContentState.LOADING, financeContentState(isLoading = true, loadFailed = true))
        assertEquals(FinanceContentState.ERROR, financeContentState(isLoading = false, loadFailed = true))
        assertEquals(FinanceContentState.CONTENT, financeContentState(isLoading = false, loadFailed = false))
    }
}
