package com.skullking.score.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundScorerTest {

    private fun player(
        id: Long,
        bid: Int,
        tricks: Int,
        bonus: Bonus = Bonus.NONE,
        wager: Int = 0,
        cannonball: Boolean = false,
        davyJones: Int = 0,
    ) = PlayerRoundInput(id, bid, tricks, bonus, wager, cannonball, davyJones)

    private fun classic(n: Int, p: PlayerRoundInput) =
        RoundScorer.scorePlayer(ScoringMode.CLASSIC, n, p, lootCount = 0).total

    // --- Verification example from the specification (round 5, classic mode) ---

    @Test
    fun specExampleRound5() {
        val scores = RoundScorer.scoreRound(
            ScoringMode.CLASSIC,
            5,
            listOf(
                player(1, 2, 2, Bonus(conCaptured = 1, eights = 1)), // Lea
                player(2, 0, 0), // Tom
                player(3, 3, 2, Bonus(black14 = 1)), // Ines
                player(4, 1, 1, Bonus(sevens = 1)), // Noe
            ),
        )
        assertEquals(75, scores.getValue(1).total)
        assertEquals(50, scores.getValue(2).total)
        assertEquals(-10, scores.getValue(3).total)
        assertEquals(15, scores.getValue(4).total)
        assertTrue(scores.getValue(3).bonusDiscarded)
    }

    // --- Classic mode: each line of the table ---

    @Test
    fun classicExactBid() = assertEquals(60, classic(7, player(1, 3, 3)))

    @Test
    fun classicMissedBidCostsTenPerTrick() {
        assertEquals(-10, classic(7, player(1, 3, 2)))
        assertEquals(-30, classic(7, player(1, 2, 5)))
    }

    @Test
    fun classicZeroBidSuccessScoresTenPerCard() = assertEquals(70, classic(7, player(1, 0, 0)))

    @Test
    fun classicZeroBidMissedLosesTenPerCard() = assertEquals(-70, classic(7, player(1, 0, 2)))

    @Test
    fun classicBonusesAreWorthTheirValueWhenBidSucceeds() {
        val bonus = Bonus(
            colored14 = 2,
            black14 = 1,
            mermaidsByPirate = 1,
            piratesBySkullKing = 2,
            skullKingByMermaid = 1,
            conCaptured = 1,
            eights = 2,
            sevens = 1,
        )
        // 20 + 20 + 20 + 60 + 40 + 30 + 10 - 5 = 195 de bonus, 20 de base
        assertEquals(20 + 195, classic(4, player(1, 1, 1, bonus)))
    }

    @Test
    fun bonusIsLostWhenBidIsMissed() {
        val s = RoundScorer.scorePlayer(
            ScoringMode.CLASSIC,
            5,
            player(1, 2, 1, Bonus(black14 = 1)),
            lootCount = 0,
        )
        assertEquals(-10, s.total)
        assertEquals(0, s.bonusApplied)
        assertEquals(20, s.bonusEntered)
    }

    @Test
    fun zeroBidKeepsBonusWhenSuccessful() {
        assertEquals(50 + 10, classic(5, player(1, 0, 0, Bonus(colored14 = 1))))
    }

    // --- Pari de Rascal ---

    @Test
    fun rascalWagerWonAndLost() {
        assertEquals(20 + 10, classic(5, player(1, 1, 1, wager = 10)))
        // Only term applied even when the bid is missed
        assertEquals(-10 - 20, classic(5, player(1, 1, 2, wager = 20)))
    }

    // --- Butins ---

    @Test
    fun lootRequiresBothPlayersToSucceed() {
        val both = RoundScorer.scoreRound(
            ScoringMode.CLASSIC,
            5,
            listOf(player(1, 1, 1), player(2, 2, 2)),
            loots = listOf(LootPair(1, 2)),
        )
        assertEquals(40, both.getValue(1).total)
        assertEquals(60, both.getValue(2).total)

        val oneFails = RoundScorer.scoreRound(
            ScoringMode.CLASSIC,
            5,
            listOf(player(1, 1, 1), player(2, 2, 3)),
            loots = listOf(LootPair(1, 2)),
        )
        assertEquals(20, oneFails.getValue(1).total)
        assertEquals(-10, oneFails.getValue(2).total)
    }

    @Test
    fun lootBetweenSamePlayerCountsOnce() {
        val s = RoundScorer.scoreRound(
            ScoringMode.CLASSIC,
            5,
            listOf(player(1, 1, 1)),
            loots = listOf(LootPair(1, 1)),
        )
        assertEquals(40, s.getValue(1).total)
    }

    // --- Coffre de Davy Jones ---

    @Test
    fun davyJonesCountsOnlyWithSuccessByDefault() {
        val ok = RoundScorer.scorePlayer(ScoringMode.CLASSIC, 5, player(1, 1, 1, davyJones = 2), 0)
        assertEquals(20 + 40, ok.total)
        val ko = RoundScorer.scorePlayer(ScoringMode.CLASSIC, 5, player(1, 1, 2, davyJones = 2), 0)
        assertEquals(-10, ko.total)
    }

    @Test
    fun davyJonesOptionCountsEvenWhenBidMissed() {
        val ko = RoundScorer.scorePlayer(
            ScoringMode.CLASSIC,
            5,
            player(1, 1, 2, davyJones = 2),
            0,
            davyJonesRequiresSuccess = false,
        )
        assertEquals(-10 + 40, ko.total)
    }

    // --- Mode Rascal ---

    private fun rascal(n: Int, p: PlayerRoundInput, loot: Int = 0) =
        RoundScorer.scorePlayer(ScoringMode.RASCAL, n, p, loot).total

    @Test
    fun rascalGapZeroOneTwo() {
        assertEquals(60, rascal(6, player(1, 3, 3)))
        assertEquals(30, rascal(6, player(1, 3, 4)))
        assertEquals(30, rascal(6, player(1, 3, 2)))
        assertEquals(0, rascal(6, player(1, 3, 5)))
    }

    @Test
    fun rascalGapOneKeepsHalfOfBonusesButNoLoot() {
        // 5*6 + 40/2 = 50; the Loot (exact only) is excluded
        assertEquals(30 + 20, rascal(6, player(1, 3, 4, Bonus(piratesBySkullKing = 1, colored14 = 1)), loot = 1))
    }

    @Test
    fun rascalExactKeepsAllBonuses() {
        assertEquals(60 + 40 + 20, rascal(6, player(1, 3, 3, Bonus(piratesBySkullKing = 1, colored14 = 1)), loot = 1))
    }

    @Test
    fun rascalWagerNeedsExactBid() {
        assertEquals(30 - 10, rascal(6, player(1, 3, 4, wager = 10)))
    }

    @Test
    fun rascalLootRequiresExactBidEvenOffByOne() {
        val s = RoundScorer.scoreRound(
            ScoringMode.RASCAL,
            4,
            listOf(player(1, 2, 2), player(2, 2, 3)),
            loots = listOf(LootPair(1, 2)),
        )
        assertEquals(40, s.getValue(1).total)
        assertEquals(20, s.getValue(2).total)
    }

    // --- Boulet de canon ---

    @Test
    fun cannonballIsAllOrNothing() {
        val exact = RoundScorer.scorePlayer(ScoringMode.CANNONBALL, 4, player(1, 2, 2, cannonball = true), 0)
        assertEquals(60, exact.total)
        val off = RoundScorer.scorePlayer(ScoringMode.CANNONBALL, 4, player(1, 2, 3, cannonball = true), 0)
        assertEquals(0, off.total)
    }

    @Test
    fun cannonballOffFallsBackToRascal() {
        val s = RoundScorer.scorePlayer(ScoringMode.CANNONBALL, 4, player(1, 2, 3), 0)
        assertEquals(20, s.total)
    }

    // --- Properties ---

    @Test
    fun resultDoesNotDependOnPlayerOrder() {
        val players = listOf(
            player(1, 1, 1, Bonus(black14 = 1)),
            player(2, 2, 2, Bonus(eights = 1)),
            player(3, 0, 1),
            player(4, 3, 3, wager = 10),
        )
        val loots = listOf(LootPair(1, 4), LootPair(2, 4))
        val reference = RoundScorer.scoreRound(ScoringMode.CLASSIC, 6, players, loots)
            .mapValues { it.value.total }
        repeat(20) { seed ->
            val shuffled = players.shuffled(java.util.Random(seed.toLong()))
            val result = RoundScorer.scoreRound(
                ScoringMode.CLASSIC,
                6,
                shuffled,
                loots.shuffled(java.util.Random(seed.toLong())),
            )
                .mapValues { it.value.total }
            assertEquals(reference, result)
        }
    }

    @Test
    fun successFlagFollowsExactness() {
        assertTrue(RoundScorer.scorePlayer(ScoringMode.CLASSIC, 3, player(1, 1, 1), 0).success)
        assertFalse(RoundScorer.scorePlayer(ScoringMode.CLASSIC, 3, player(1, 1, 2), 0).success)
    }

    // --- Validation ---

    @Test
    fun validatorFlagsTrickMismatchAndImpossibleCaptures() {
        val inputs = listOf(
            player(1, 1, 3, Bonus(colored14 = 2, skullKingByMermaid = 1)),
            player(2, 1, 3, Bonus(colored14 = 2, skullKingByMermaid = 1)),
        )
        val w = RoundValidator.validate(
            cardsDealt = 5,
            playerCount = 2,
            discardedTricks = 0,
            inputs = inputs,
            lootCount = 3,
            monsters = listOf(SeaMonster.KRAKEN, SeaMonster.KRAKEN),
            expansion = emptySet(),
        ).map { it.code }
        assertTrue(WarningCode.TRICKS_OVER in w)
        assertTrue(WarningCode.TOO_MANY_COLORED_14 in w)
        assertTrue(WarningCode.TOO_MANY_SKULL_KING in w)
        assertTrue(WarningCode.TOO_MANY_LOOT in w)
        assertTrue(WarningCode.DUPLICATE_MONSTER in w)
    }

    @Test
    fun validatorAsksForDiscardedTricksWhenSumIsLower() {
        val w = RoundValidator.validate(
            5,
            2,
            0,
            listOf(player(1, 1, 1), player(2, 1, 1)),
            0,
            emptyList(),
            emptySet(),
        )
        assertEquals(listOf(WarningCode.TRICKS_UNDER), w.map { it.code })
        val ok = RoundValidator.validate(
            5,
            2,
            3,
            listOf(player(1, 1, 1), player(2, 1, 1)),
            0,
            emptyList(),
            emptySet(),
        )
        assertTrue(ok.isEmpty())
    }

    @Test
    fun deckRulesSuggestCardsAndCapAtDeck() {
        assertEquals(1, DeckRules.suggestedCards(1, null, 4, emptySet()))
        assertEquals(6, DeckRules.suggestedCards(6, 5, 4, emptySet()))
        assertEquals(10, DeckRules.suggestedCards(11, 10, 4, emptySet()))
        // 9 players: the base deck only allows 8 cards
        assertEquals(8, DeckRules.maxCards(9, emptySet()))
    }

    @Test
    fun twoPlayersRemoveSomeExpansionCards() {
        assertFalse(ExpansionCard.WILD_15.availableFor(2))
        assertFalse(ExpansionCard.PLANK.availableFor(2))
        assertFalse(ExpansionCard.ZERO_FOURTEEN.availableFor(2))
        assertTrue(ExpansionCard.SEVENS.availableFor(2))
    }
}
