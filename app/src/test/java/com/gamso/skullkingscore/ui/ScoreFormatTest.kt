package com.gamso.skullkingscore.ui

import com.gamso.skullkingscore.domain.export.csvNumber
import com.gamso.skullkingscore.ui.components.formatPoints
import com.gamso.skullkingscore.ui.components.formatScore
import com.gamso.skullkingscore.ui.feature.stats.percent
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

/** Display of scores, which can hold a half point (Rascal scoring). */
class ScoreFormatTest {

    private val initial = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(initial)

    @Test
    fun wholeScoresHaveNoDecimals() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("10", formatScore(10.0))
        assertEquals("0", formatScore(0.0))
        assertEquals("+20", formatPoints(20.0))
        assertEquals("-10", formatPoints(-10.0))
    }

    @Test
    fun halfPointsFollowTheLocaleSeparator() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("47,5", formatScore(47.5))
        assertEquals("+2,5", formatPoints(2.5))
        assertEquals("-2,5", formatPoints(-2.5))

        Locale.setDefault(Locale.US)
        assertEquals("47.5", formatScore(47.5))
        assertEquals("+2.5", formatPoints(2.5))
    }

    @Test
    fun theCsvAlwaysUsesADot() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("10", csvNumber(10.0))
        assertEquals("-2.5", csvNumber(-2.5))
        assertEquals("47.5", csvNumber(47.5))
    }

    @Test
    fun percentagesFollowTheLocale() {
        // French puts a (narrow) no-break space before "%", English none.
        assertEquals("12%", 0.123.percent(Locale.US))
        assertEquals("100%", 1.0.percent(Locale.US))
        val french = 0.123.percent(Locale.FRANCE)
        assertEquals("12%", french.filterNot { Character.isSpaceChar(it) })
        assertTrue(french.length > "12%".length)
    }
}
