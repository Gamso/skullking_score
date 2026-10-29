package com.skullking.score.ui

import com.skullking.score.domain.export.csvNumber
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.components.formatScore
import java.util.Locale
import kotlin.test.assertEquals
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
}
