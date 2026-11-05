package com.skullking.score.ui

import com.skullking.score.core.Bonus
import com.skullking.score.core.CaptureRules
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.WinningCard
import com.skullking.score.ui.components.keepOnly
import kotlin.test.assertEquals
import org.junit.Test

class BonusCountersTest {
    private val everything = Bonus(
        colored14 = 1,
        black14 = 1,
        mermaidsByPirate = 1,
        piratesBySkullKing = 2,
        skullKingByMermaid = 1,
        conCaptured = 1,
        eights = 1,
        sevens = 1,
    )
    private val expansion = ExpansionCard.entries.toSet()

    @Test
    fun aMermaidKeepsTheSkullKingAndTheNumberedCards() {
        val kept = everything.keepOnly(CaptureRules.allowed(WinningCard.MERMAID), expansion)
        assertEquals(
            Bonus(colored14 = 1, black14 = 1, skullKingByMermaid = 1, conCaptured = 1, eights = 1, sevens = 1),
            kept,
        )
    }

    @Test
    fun aNumberedCardKeepsOnlyNumberedCards() {
        val kept = everything.keepOnly(CaptureRules.allowed(WinningCard.OTHER), expansion)
        assertEquals(Bonus(colored14 = 1, black14 = 1, eights = 1, sevens = 1), kept)
    }

    @Test
    fun withoutAWinningCardNothingIsRemoved() {
        assertEquals(everything, everything.keepOnly(CaptureRules.allowed(null), expansion))
    }
}
