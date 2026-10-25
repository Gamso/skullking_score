package com.skullking.score.domain

import com.skullking.score.core.Bonus
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.scoring.TrickAggregator
import com.skullking.score.domain.stats.StatsPeriod
import com.skullking.score.ui.normalizeForSearch
import com.skullking.score.ui.share.ShareOptions
import com.skullking.score.ui.share.StatsTrend
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Small utility functions: trick aggregation, export periods, search, share options. */
class SupportTest {

    private fun trick(number: Int, winner: Long?, bonus: Bonus = Bonus.NONE, wager: Int = 0, adjust: Int = 0) =
        Trick(number.toLong(), 1, number, winner, bonus, 0, wager, adjust)

    @Test
    fun trickAggregationCountsWinsBonusesWagersAndDestroyedTricks() {
        val totals = TrickAggregator.aggregate(
            listOf(
                trick(1, 1, Bonus(colored14 = 1), wager = 10),
                trick(2, 1, Bonus(black14 = 1), adjust = -1),
                trick(3, 2),
                trick(4, null),
            ),
        )
        val first = totals.perPlayer.getValue(1)
        assertEquals(2, first.tricks)
        assertEquals(Bonus(colored14 = 1, black14 = 1), first.bonus)
        assertEquals(10, first.rascalWager)
        assertEquals(-1, first.bidAdjustment)
        assertEquals(1, totals.perPlayer.getValue(2).tricks)
        assertEquals(1, totals.destroyed)
    }

    @Test
    fun noTricksMeansNoTotals() {
        val totals = TrickAggregator.aggregate(emptyList())
        assertTrue(totals.perPlayer.isEmpty())
        assertEquals(0, totals.destroyed)
    }

    @Test
    fun exportPeriodsStartAtTheBeginningOfTheMonthOrYear() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(
            LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            StatsPeriod.MONTH.startMillis(now, zone),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            StatsPeriod.YEAR.startMillis(now, zone),
        )
        assertNull(StatsPeriod.ALL.startMillis(now, zone))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals("ines", normalizeForSearch("Inès"))
        assertEquals("gaelle", normalizeForSearch("GAËLLE"))
        assertTrue(normalizeForSearch("Camille").contains(normalizeForSearch("MIL")))
        assertEquals("", normalizeForSearch(""))
    }

    @Test
    fun shareOptionsKnowWhenNothingIsSelected() {
        assertFalse(ShareOptions().isEmpty)
        assertTrue(ShareOptions(podium = false, table = false, curve = false, stats = false).isEmpty)
        assertFalse(ShareOptions(podium = false, table = false, curve = true, stats = false).isEmpty)
    }

    @Test
    fun aTrendKeepsTheScoresAndTheirAverage() {
        val trend = StatsTrend(listOf(10, 20, 30), listOf(10.0, 15.0, 20.0))
        assertEquals(3, trend.totals.size)
        assertEquals(trend.totals.size, trend.rolling.size)
    }
}
