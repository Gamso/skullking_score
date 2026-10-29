package com.skullking.score.ui

import java.io.File
import java.util.Locale
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Scores can hold a half point (Rascal scoring), so strings that receive a score must take it as text (`%s`):
 * with `%d`, a game holding a half point crashed the score curve.
 */
class StringFormatsTest {
    private val resDir = File("src/main/res")

    private fun curveSummaryLines(): Map<String, String> = resDir
        .listFiles { f -> f.name.startsWith("values") }
        .orEmpty()
        .mapNotNull { dir ->
            val file = File(dir, "strings.xml")
            val line = file.takeIf { it.exists() }?.readLines()?.firstOrNull { "name=\"curve_summary_line\"" in it }
            line?.let { dir.name to it.substringAfter(">").substringBeforeLast("</string>") }
        }.toMap()

    @Test
    fun curveSummaryAcceptsAHalfPointScore() {
        val lines = curveSummaryLines()
        assertTrue(lines.size >= 2, "the curve summary should exist in several languages: ${lines.keys}")
        lines.forEach { (language, format) ->
            // Throws IllegalFormatConversionException when the score placeholder is `%d`.
            String.format(Locale.ROOT, format, "Alice", "7.5", 3)
            assertTrue("%2\$d" !in format, "$language: the score must not be formatted with %d")
        }
    }
}
