package com.gamso.skullkingscore.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureRulesTest {
    @Test
    fun aMermaidCapturesTheSkullKingButNoPirate() {
        val allowed = CaptureRules.allowed(WinningCard.MERMAID)
        assertTrue(BonusType.SKULL_KING_BY_MERMAID in allowed)
        assertTrue(BonusType.CON_CAPTURED in allowed)
        assertFalse(BonusType.PIRATE_BY_SKULL_KING in allowed)
        assertFalse(BonusType.MERMAID_BY_PIRATE in allowed)
    }

    @Test
    fun aPirateCapturesMermaidsOnly() {
        val allowed = CaptureRules.allowed(WinningCard.PIRATE)
        assertTrue(BonusType.MERMAID_BY_PIRATE in allowed)
        assertFalse(BonusType.SKULL_KING_BY_MERMAID in allowed)
        assertFalse(BonusType.PIRATE_BY_SKULL_KING in allowed)
        assertFalse(BonusType.CON_CAPTURED in allowed)
    }

    @Test
    fun theSkullKingCapturesPiratesAndTheFirstMate() {
        val allowed = CaptureRules.allowed(WinningCard.SKULL_KING)
        assertTrue(BonusType.PIRATE_BY_SKULL_KING in allowed)
        assertTrue(BonusType.CON_CAPTURED in allowed)
        assertFalse(BonusType.MERMAID_BY_PIRATE in allowed)
        assertFalse(BonusType.SKULL_KING_BY_MERMAID in allowed)
    }

    @Test
    fun numberedCardsAndTheFirstMateCaptureNoCharacter() {
        val numbered = setOf(BonusType.COLORED_14, BonusType.BLACK_14, BonusType.EIGHT, BonusType.SEVEN)
        assertEquals(numbered, CaptureRules.allowed(WinningCard.OTHER))
        assertEquals(numbered, CaptureRules.allowed(WinningCard.FIRST_MATE))
    }

    @Test
    fun theDeckCapsWhatCanBeCaptured() {
        assertEquals(2, DeckRules.maxCaptured(BonusType.MERMAID_BY_PIRATE, emptySet()))
        assertEquals(1, DeckRules.maxCaptured(BonusType.SKULL_KING_BY_MERMAID, emptySet()))
        assertEquals(6, DeckRules.maxCaptured(BonusType.PIRATE_BY_SKULL_KING, emptySet()))
        assertEquals(7, DeckRules.maxCaptured(BonusType.PIRATE_BY_SKULL_KING, setOf(ExpansionCard.MARY_THORNE)))
    }
}
