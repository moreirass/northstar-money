package com.northstar.money.core.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals

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

    @Test
    fun appDoesNotGloballyBlockScreenshots() {
        val activity = File(
            findAppDirectory(),
            "src/main/java/com/northstar/money/MainActivity.kt",
        ).readText()

        assertFalse("FLAG_SECURE would block every screenshot", activity.contains("FLAG_SECURE"))
    }

    @Test
    fun portugueseCatalogHasTheSameKeysAndFormatArguments() {
        val appDir = findAppDirectory()
        val base = readStrings(File(appDir, "src/main/res/values/strings.xml"))
        val portuguese = readStrings(File(appDir, "src/main/res/values-pt/strings.xml"))

        assertEquals(base.keys, portuguese.keys)
        base.forEach { (key, value) ->
            assertEquals("Format arguments differ for $key", formatArguments(value), formatArguments(portuguese.getValue(key)))
        }
    }

    private fun readStrings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun formatArguments(value: String): List<String> =
        Regex("%\\d+\\$[a-zA-Z]").findAll(value).map { it.value }.sorted().toList()

    private fun findAppDirectory(): File {
        val current = File(System.getProperty("user.dir"))
        return if (current.name == "app") current else File(current, "app")
    }
}
