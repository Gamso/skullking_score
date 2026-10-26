package com.skullking.score.domain

import com.skullking.score.core.Bonus
import com.skullking.score.core.BonusType
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.model.DavyJonesCapture
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.LootLink
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.stats.CardStatsCalculator
import com.skullking.score.domain.stats.SuccessRate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Statistics about the cards: tricks won, captures, wagers, loots, Davy Jones, timing, Kraken, expansion. */
class CardStatsTest {

    private val ann = Player(1, "u1", "Ann", 0xFFE57373, 0)
    private val bob = Player(2, "u2", "Bob", 0xFF4FC3F7, 0)
    private val cat = Player(3, "u3", "Cat", 0xFFAED581, 0)

    private fun entry(roundId: Long, player: Player, bid: Int, tricks: Int, bonus: Bonus = Bonus.NONE, wager: Int = 0) =
        PlayerRound(roundId, player.id, bid, null, tricks, false, wager, bonus, 0)

    private fun trick(
        number: Int,
        winner: Long?,
        card: WinningCard? = null,
        pirate: Pirate? = null,
        wager: Int = 0,
        adjustment: Int = 0,
    ) = Trick(number.toLong(), 1, number, winner, Bonus.NONE, 0, wager, adjustment, card, pirate)

    /**
     * Round 1 (3 cards): Ann and Bob make their bid, Cat misses. Three tricks are entered, a Davy Jones' Locker and
     * two Loots are played. Round 2 (1 card): a trick is destroyed by the Kraken and everybody makes a bid of 0.
     */
    private fun sampleGame(
        expansion: Set<ExpansionCard> = emptySet(),
        id: Long = 1,
        mode: ScoringMode = ScoringMode.CLASSIC,
    ): GameDetails {
        val game = Game(
            id,
            "g-$id",
            1_000 * id,
            null,
            mode,
            expansion,
            GameStatus.FINISHED,
            listOf(ann, bob, cat),
        )
        val first = RoundDetails(
            Round(10 * id, id, 1, 3, false, 0, RoundPhase.SCORED),
            listOf(
                entry(10 * id, ann, 1, 1, Bonus(black14 = 1), wager = 10),
                entry(10 * id, bob, 1, 1),
                entry(10 * id, cat, 0, 1),
            ),
            listOf(
                LootLink(1, 10 * id, ann.id, bob.id, 0, null),
                LootLink(2, 10 * id, cat.id, ann.id, 0, null),
                LootLink(3, 10 * id, cat.id, ann.id, 0, 5),
            ),
            listOf(DavyJonesCapture(1, 10 * id, bob.id, setOf(SeaMonster.KRAKEN, SeaMonster.WHITE_WHALE), 0, null)),
            listOf(
                trick(1, ann.id, WinningCard.PIRATE, Pirate.ROSIE, wager = 10),
                trick(2, bob.id, WinningCard.MERMAID),
                trick(3, cat.id, WinningCard.SKULL_KING, adjustment = -1),
            ),
        )
        val second = RoundDetails(
            Round(10 * id + 1, id, 2, 1, false, 1, RoundPhase.SCORED),
            listOf(entry(10 * id + 1, ann, 0, 0), entry(10 * id + 1, bob, 0, 0), entry(10 * id + 1, cat, 0, 0)),
            emptyList(),
            emptyList(),
            listOf(trick(1, null)),
        )
        return GameDetails(game, listOf(first, second))
    }

    private fun stats(vararg games: GameDetails) =
        CardStatsCalculator.compute(games.map { GameScoreCalculator.calculate(it) })

    @Test
    fun tricksAreCountedByCardAndByPirate() {
        val s = stats(sampleGame())
        assertEquals(1, s.gamesCount)
        assertEquals(1, s.gamesWithTricks)
        assertEquals(4, s.tricksRecorded)
        assertEquals(
            mapOf(WinningCard.PIRATE to 1, WinningCard.MERMAID to 1, WinningCard.SKULL_KING to 1),
            s.winsByCard,
        )
        assertEquals(mapOf(Pirate.ROSIE to 1), s.winsByPirate)
        assertTrue(s.hasTricks)
    }

    @Test
    fun bidSuccessIsReportedForTheWinnersOfEachCard() {
        val s = stats(sampleGame())
        assertEquals(SuccessRate(1, 1), s.successWhenWonByCard[WinningCard.PIRATE])
        assertEquals(SuccessRate(1, 1), s.successWhenWonByCard[WinningCard.MERMAID])
        assertEquals(SuccessRate(1, 0), s.successWhenWonByCard[WinningCard.SKULL_KING])
        assertEquals(SuccessRate(1, 1), s.successWhenWonByPirate[Pirate.ROSIE])
    }

    @Test
    fun cardsAreSpreadOverTheThirdsOfTheRound() {
        val s = stats(sampleGame())
        assertEquals(listOf(1, 0, 0), s.timing[WinningCard.PIRATE])
        assertEquals(listOf(0, 1, 0), s.timing[WinningCard.MERMAID])
        assertEquals(listOf(0, 0, 1), s.timing[WinningCard.SKULL_KING])
    }

    @Test
    fun capturedBonusesAndTheirPointsAreSummed() {
        val s = stats(sampleGame())
        assertEquals(1, s.captures[BonusType.BLACK_14]?.count)
        assertEquals(20, s.captures[BonusType.BLACK_14]?.points)
        assertTrue(s.playerRoundsWithBonus >= 1)
        assertEquals(6, s.playerRounds)
        assertTrue(s.bonusPoints >= 20)
    }

    @Test
    fun rascalWagersAndHarryAdjustmentsReportTheirOutcome() {
        // In Rascal modes a successful wager is worth its amount.
        val s = stats(sampleGame(mode = ScoringMode.RASCAL))
        val wager = s.rascalWagers.getValue(10)
        assertEquals(1, wager.attempts)
        assertEquals(1, wager.successes)
        assertEquals(10, wager.netPoints)
        assertFalse(20 in s.rascalWagers)
        assertEquals(SuccessRate(1, 0), s.harryAdjustments[-1])
        assertNull(s.harryAdjustments[1])
    }

    @Test
    fun lootsAreValidOnlyWhenBothPlayersMadeTheirBid() {
        val loots = stats(sampleGame()).loots
        // Cancelled Loots do not count; Ann's Loot with Bob is valid, Cat's with Ann is not (Cat missed).
        assertEquals(2, loots.total)
        assertEquals(1, loots.validated)
        assertEquals(setOf("Ann" to 1, "Cat" to 1), loots.byPlayer.toSet())
    }

    @Test
    fun davyJonesCreaturesArePerType() {
        val s = stats(sampleGame())
        assertEquals(mapOf(SeaMonster.KRAKEN to 1, SeaMonster.WHITE_WHALE to 1), s.davyJonesMonsters)
        assertTrue(s.davyJonesPoints > 0)
    }

    @Test
    fun destroyedTricksAreComparedWithRoundsWithoutAny() {
        val kraken = stats(sampleGame()).kraken
        assertEquals(1, kraken.destroyedTricks)
        assertEquals(SuccessRate(3, 3), kraken.roundsWithDestroyed)
        assertEquals(SuccessRate(3, 2), kraken.roundsWithout)
    }

    @Test
    fun gamesWithoutLiveTricksAreIgnoredByTheTrickStatistics() {
        val noTricks = sampleGame().let { d -> d.copy(rounds = d.rounds.map { it.copy(tricks = emptyList()) }) }
        val s = stats(noTricks)
        assertFalse(s.hasTricks)
        assertEquals(0, s.gamesWithTricks)
        assertTrue(s.winsByCard.isEmpty())
    }

    @Test
    fun theExpansionIsComparedWithTheBaseGame() {
        val withExpansion = sampleGame(setOf(ExpansionCard.SEVENS), id = 1)
        val base = sampleGame(emptySet(), id = 2)
        val scored = listOf(withExpansion, base).map { GameScoreCalculator.calculate(it) }
        val comparison = CardStatsCalculator.expansionComparison(scored)
        assertEquals(1, comparison.with.games)
        assertEquals(1, comparison.without.games)
        assertEquals(6, comparison.with.bidSuccess.attempts)
        assertEquals(comparison.with.bidSuccess, comparison.without.bidSuccess)
        assertEquals(0, comparison.expansionCardWins)
    }

    @Test
    fun theExpansionCardsThatWonTricksAreCounted() {
        val base = sampleGame(setOf(ExpansionCard.FIRST_MATE_CON))
        val firstRound = base.rounds.first().let { r ->
            r.copy(
                tricks =
                    r.tricks + trick(4, ann.id, WinningCard.FIRST_MATE) +
                        trick(5, bob.id, WinningCard.PIRATE, Pirate.MARY_THORNE),
            )
        }
        val game = base.copy(rounds = listOf(firstRound) + base.rounds.drop(1))
        val comparison = CardStatsCalculator.expansionComparison(listOf(GameScoreCalculator.calculate(game)))
        assertEquals(2, comparison.expansionCardWins)
    }
}
