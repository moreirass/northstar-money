package com.northstar.money.release

import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.northstar.money.MainActivity
import com.northstar.money.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Executa a atividade de produção; não substitui o conteúdo por uma composição de teste. */
class ReleaseSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int) = compose.activity.getString(id)
    private fun visible(id: Int) = compose.onAllNodesWithText(label(id)).fetchSemanticsNodes().isNotEmpty()
    private fun click(id: Int) {
        compose.waitUntil(15_000) { visible(id) }
        compose.onAllNodesWithText(label(id)).onFirst().performClick()
    }

    @Test fun minifiedApplicationOpensCompletesOnboardingAndNavigatesPrimaryScreens() {
        assertEquals(0, compose.activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        compose.waitUntil(15_000) { visible(R.string.onboarding_start) || visible(R.string.nav_home) }
        if (visible(R.string.onboarding_start)) {
            click(R.string.onboarding_start)
            click(R.string.onboarding_continue)
            compose.waitUntil(15_000) { visible(R.string.onboarding_balance_title) }
            click(R.string.onboarding_continue)
            compose.waitUntil(15_000) { visible(R.string.onboarding_budget_title) }
            click(R.string.onboarding_budget_finish)
        }
        click(R.string.nav_activity)
        compose.onAllNodesWithText(label(R.string.transactions_expenses)).onFirst().assertIsDisplayed()
        click(R.string.nav_plan)
        click(R.string.nav_more)
        click(R.string.nav_home)
        compose.onAllNodesWithText(label(R.string.nav_home)).onFirst().assertIsDisplayed()
    }
}
