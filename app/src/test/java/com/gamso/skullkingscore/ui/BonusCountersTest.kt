package com.gamso.skullkingscore.ui

import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.CaptureRules
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.WinningCard
import com.gamso.skullkingscore.ui.components.keepOnly
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
