package com.northstar.money.core.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatableUiTextTest {
    @Test
    fun composeAndSystemUiDoNotContainHardcodedUserFacingText() {
        val appDir = findAppDirectory()
        val sources = listOf(
            "src/main/java/com/northstar/money/MainActivity.kt",
            "src/main/java/com/northstar/money/core/navigation/NorthstarApp.kt",
            "src/main/java/com/northstar/money/core/navigation/FinanceScreens.kt",
            "src/main/java/com/northstar/money/core/navigation/FinanceDialogs.kt",
            "src/main/java/com/northstar/money/data/worker/ReviewReminderWorker.kt",
        ).map { File(appDir, it).readText() }

        val forbiddenPatterns = listOf(
            Regex("\\bText\\(\\s*\\\""),
            Regex("contentDescription\\s*=\\s*\\\""),
            Regex("\\.setTitle\\(\\s*\\\""),
            Regex("\\.setSubtitle\\(\\s*\\\""),
            Regex("\\.setContentTitle\\(\\s*\\\""),
            Regex("\\.setContentText\\(\\s*\\\""),
        )

        forbiddenPatterns.forEach { pattern ->
            assertFalse("Hardcoded UI text matched $pattern", sources.any(pattern::containsMatchIn))
        }
    }

    @Test
    fun baseStringCatalogContainsExtractedUiText() {
        val stringsFile = File(findAppDirectory(), "src/main/res/values/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile)
        val strings = document.getElementsByTagName("string")

        assertTrue("Expected the complete UI string catalog", strings.length >= 140)
    }

    private fun findAppDirectory(): File {
        val current = File(System.getProperty("user.dir"))
        return if (current.name == "app") current else File(current, "app")
    }
}
