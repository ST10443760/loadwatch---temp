package com.example.loadwatch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Every string a user can see must exist in isiZulu and Afrikaans as well as English, so switching language in S09
// never leaves a screen half translated. Unit tests run with the app module as the working directory.
class TranslationCoverageTest {

    private val resRoot = File("src/main/res")
    private val namePattern = Regex("""<(string|plurals) name="([^"]+)"([^>]*)>""")
    private val placeholderPattern = Regex("""%\d+\$[sd]""")

    @Test
    fun everyTranslatableStringIsTranslatedIntoIsiZuluAndAfrikaans() {
        val english = translatableNames(File(resRoot, "values/strings.xml"))
        assertTrue("English strings not found at ${resRoot.absolutePath}", english.isNotEmpty())

        listOf("zu", "af").forEach { language ->
            val translated = translatableNames(File(resRoot, "values-$language/strings.xml"))
            val missing = english - translated
            val extra = translated - english
            assertTrue("Missing from values-$language: $missing", missing.isEmpty())
            assertTrue("Not in English but in values-$language: $extra", extra.isEmpty())
        }
    }

    @Test
    fun translationsKeepTheSamePlaceholders() {
        val english = strings(File(resRoot, "values/strings.xml"))
        listOf("zu", "af").forEach { language ->
            strings(File(resRoot, "values-$language/strings.xml")).forEach { (name, text) ->
                val expected = english[name]?.let(::placeholders)
                assertTrue(
                    "values-$language/$name has ${placeholders(text)} but English has $expected",
                    expected == null || placeholders(text) == expected,
                )
            }
        }
    }

    private fun translatableNames(file: File): Set<String> =
        namePattern.findAll(file.readText())
            .filterNot { it.groupValues[3].contains("""translatable="false"""") }
            .map { it.groupValues[2] }
            .toSet()

    // Plain strings only; plurals vary their wording per quantity
    private fun strings(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun placeholders(text: String): Set<String> = placeholderPattern.findAll(text).map { it.value }.toSet()
}
