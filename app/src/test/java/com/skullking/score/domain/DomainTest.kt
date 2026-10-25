package com.skullking.score.domain

import com.skullking.score.core.Bonus
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.export.CsvExporter
import com.skullking.score.domain.export.ExportFilter
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.GameStep
import com.skullking.score.domain.model.LootLink
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.model.nextStep
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.scoring.TrickAggregator
import com.skullking.score.domain.stats.StatsCalculator
import com.skullking.score.domain.stats.StatsFilter
import com.skullking.score.domain.stats.StatsPeriod
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DomainTest {

    private val lea = Player(1, "u1", "Léa", 0xFFE57373, 0)
    private val tom = Player(2, "u2", "Tom", 0xFF4FC3F7, 0)
    private val ines = Player(3, "u3", "Inès; \"la\" pirate", 0xFFAED581, 0)

    private fun game(
        status: GameStatus = GameStatus.FINISHED,
        mode: ScoringMode = ScoringMode.CLASSIC,
        startedAt: Long = 1_700_000_000_000,
    ) = Game(10, "g-10", startedAt, null, mode, emptySet(), status, true, listOf(lea, tom, ines))

    private fun entry(
        roundId: Long,
        player: Player,
        bid: Int,
        tricks: Int,
        bonus: Bonus = Bonus.NONE,
        finalBid: Int? = null,
    ) = PlayerRound(roundId, player.id, bid, finalBid, tricks, false, 0, bonus, 0)

    private fun round(
        id: Long,
        number: Int,
        cards: Int,
        phase: RoundPhase,
        entries: List<PlayerRound>,
        loots: List<LootLink> = emptyList(),
    ) = RoundDetails(Round(id, 10, number, cards, false, 0, phase), entries, loots, emptyList())

    /** Round 1: 1 card. Round 2: 2 cards. Round 3: in progress (not counted). */
    private fun sampleGame(status: GameStatus = GameStatus.FINISHED, mode: ScoringMode = ScoringMode.CLASSIC) =
        GameDetails(
            game(status, mode),
            listOf(
                round(
                    1,
                    1,
                    1,
                    RoundPhase.SCORED,
                    listOf(entry(1, lea, 1, 1), entry(1, tom, 0, 0), entry(1, ines, 0, 0)),
                ),
                round(
                    2,
                    2,
                    2,
                    RoundPhase.SCORED,
                    listOf(entry(2, lea, 1, 2), entry(2, tom, 2, 2, Bonus(black14 = 1)), entry(2, ines, 0, 0)),
                ),
                round(
                    3,
                    3,
                    3,
                    RoundPhase.BIDDING,
                    listOf(entry(3, lea, 3, 0), entry(3, tom, 0, 0), entry(3, ines, 0, 0)),
                ),
            ),
        )

    @Test
    fun totalsOnlyCountValidatedRoundsAndCumulateInOrder() {
        val scored = GameScoreCalculator.calculate(sampleGame())
        // Lea: +20, then -10; Tom: +10, then 40+20; Ines: +10, then +20
        assertEquals(10, scored.totals[1])
        assertEquals(70, scored.totals[2])
        assertEquals(30, scored.totals[3])
        assertEquals(20, scored.rounds[0].cumulative[1])
        assertEquals(10, scored.rounds[1].cumulative[1])
        // The round in progress keeps the previous cumulative total
        assertEquals(10, scored.rounds[2].cumulative[1])
    }

    @Test
    fun rankingSharesRankOnTies() {
        val details = GameDetails(
            game(),
            listOf(
                round(
                    1,
                    1,
                    1,
                    RoundPhase.SCORED,
                    listOf(entry(1, lea, 1, 1), entry(1, tom, 1, 1), entry(1, ines, 1, 0)),
                ),
            ),
        )
        val ranking = GameScoreCalculator.calculate(details).ranking
        assertEquals(listOf(1, 1, 3), ranking.map { it.rank })
        assertEquals("Inès; \"la\" pirate", ranking.last().player.name)
    }

    @Test
    fun harryBidCorrectionChangesTheScore() {
        val details = GameDetails(
            game(),
            listOf(
                round(
                    1,
                    1,
                    3,
                    RoundPhase.SCORED,
                    listOf(entry(1, lea, 2, 3, finalBid = 3), entry(1, tom, 1, 0), entry(1, ines, 0, 0)),
                ),
            ),
        )
        assertEquals(60, GameScoreCalculator.calculate(details).totals[1])
    }

    @Test
    fun lootLinkIsDerivedFromEventsAndIgnoresCancelledOnes() {
        val entries = listOf(entry(1, lea, 1, 1), entry(1, tom, 1, 1), entry(1, ines, 0, 0))
        val active = LootLink(1, 1, lea.id, tom.id, 0, null)
        val cancelled = active.copy(id = 2, cancelledAt = 5)
        val with = GameScoreCalculator.calculate(
            GameDetails(game(), listOf(round(1, 1, 2, RoundPhase.SCORED, entries, listOf(active)))),
        )
        val without = GameScoreCalculator.calculate(
            GameDetails(game(), listOf(round(1, 1, 2, RoundPhase.SCORED, entries, listOf(cancelled)))),
        )
        assertEquals(40, with.totals[1])
        assertEquals(40, with.totals[2])
        assertEquals(20, without.totals[1])
    }

    @Test
    fun cannonballWagerIsAllOrNothingInCannonballMode() {
        fun game(tricks: Int) = GameDetails(
            game(mode = ScoringMode.CANNONBALL),
            listOf(
                round(
                    1,
                    1,
                    4,
                    RoundPhase.SCORED,
                    listOf(
                        entry(1, lea, 2, tricks).copy(cannonball = true),
                        entry(1, tom, 2, 3), // no wager: computed as in Rascal (gap of 1 = 5 x N)
                        entry(1, ines, 0, 0),
                    ),
                ),
            ),
        )
        assertEquals(60, GameScoreCalculator.calculate(game(2)).totals[1]) // 15 × 4
        assertEquals(0, GameScoreCalculator.calculate(game(3)).totals[1])
        assertEquals(20, GameScoreCalculator.calculate(game(2)).totals[2])
    }

    @Test
    fun trickAggregatorCountsTricksCapturesAndDestroyedTricks() {
        fun trick(n: Int, winner: Long?, bonus: Bonus = Bonus.NONE) = Trick(n.toLong(), 1, n, winner, bonus, 0)
        val totals = TrickAggregator.aggregate(
            listOf(
                trick(1, lea.id, Bonus(colored14 = 1)),
                trick(2, tom.id),
                trick(3, lea.id, Bonus(colored14 = 1, black14 = 1)),
                trick(4, null),
            ),
        )
        assertEquals(2, totals.perPlayer.getValue(lea.id).tricks)
        assertEquals(Bonus(colored14 = 2, black14 = 1), totals.perPlayer.getValue(lea.id).bonus)
        assertEquals(1, totals.perPlayer.getValue(tom.id).tricks)
        assertEquals(null, totals.perPlayer[ines.id])
        assertEquals(1, totals.destroyed)
    }

    @Test
    fun lootWithoutKnownWinnerScoresNothing() {
        val entries = listOf(entry(1, lea, 1, 1), entry(1, tom, 1, 1), entry(1, ines, 0, 0))
        val pending = LootLink(1, 1, lea.id, null, 0, null, trickNumber = 2)
        val scored = GameScoreCalculator.calculate(
            GameDetails(game(), listOf(round(1, 1, 2, RoundPhase.SCORED, entries, listOf(pending)))),
        )
        assertEquals(20, scored.totals[1])
    }

    @Test
    fun statsCountTricksWonByCardAndPirate() {
        fun trick(n: Int, winner: Long, card: WinningCard?, pirate: Pirate? = null) =
            Trick(n.toLong(), 1, n, winner, Bonus.NONE, 0, winningCard = card, winningPirate = pirate)
        val tricks = listOf(
            trick(1, lea.id, WinningCard.MERMAID),
            trick(2, lea.id, WinningCard.PIRATE, Pirate.RASCAL),
            trick(3, lea.id, WinningCard.PIRATE, Pirate.RASCAL),
            trick(4, lea.id, null),
            trick(5, tom.id, WinningCard.SKULL_KING),
        )
        val entries = listOf(entry(1, lea, 0, 4), entry(1, tom, 0, 1), entry(1, ines, 0, 0))
        val details = GameDetails(
            game(),
            listOf(
                RoundDetails(
                    Round(1, 10, 1, 5, false, 0, RoundPhase.SCORED),
                    entries,
                    emptyList(),
                    emptyList(),
                    tricks,
                ),
            ),
        )
        val stats = StatsCalculator.compute(listOf(details), StatsFilter(ScoringMode.CLASSIC))
        val leaStats = stats.first { it.player.id == lea.id }
        assertEquals(1, leaStats.winsByCard[WinningCard.MERMAID])
        assertEquals(2, leaStats.winsByCard[WinningCard.PIRATE])
        assertEquals(2, leaStats.winsByPirate[Pirate.RASCAL])
        assertEquals(null, leaStats.winsByCard[WinningCard.SKULL_KING])
        assertEquals(1, stats.first { it.player.id == tom.id }.winsByCard[WinningCard.SKULL_KING])
    }

    @Test
    fun resumeStepFollowsTheLastRoundPhase() {
        assertEquals(GameStep.Prepare(1), GameDetails(game(GameStatus.IN_PROGRESS), emptyList()).nextStep())
        assertEquals(GameStep.Bids(3), sampleGame(GameStatus.IN_PROGRESS).nextStep())
        val scoredOnly = sampleGame(GameStatus.IN_PROGRESS).let { it.copy(rounds = it.rounds.take(2)) }
        assertEquals(GameStep.Scoreboard, scoredOnly.nextStep())
        assertEquals(GameStep.Finished, sampleGame(GameStatus.FINISHED).nextStep())
    }

    @Test
    fun statsCountWinsBidSuccessAndHeadToHead() {
        val stats = StatsCalculator.compute(listOf(sampleGame()), StatsFilter(ScoringMode.CLASSIC))
        val tomStats = stats.first { it.player.id == tom.id }
        assertEquals(1, tomStats.gamesPlayed)
        assertEquals(1, tomStats.wins)
        assertEquals(70.0, tomStats.averageScore)
        // 2 validated rounds, 2 successful bids; 1 successful zero bid
        assertEquals(2, tomStats.roundsPlayed)
        assertEquals(2, tomStats.bidsSucceeded)
        assertEquals(1.0, tomStats.zeroBidSuccessRate)
        assertEquals(20, tomStats.bonusPoints[com.skullking.score.core.BonusType.BLACK_14])
        val leaDuel = stats.first { it.player.id == lea.id }.headToHead.first { it.opponent.id == tom.id }
        assertEquals(0, leaDuel.wins)
        assertEquals(1, leaDuel.losses)
    }

    @Test
    fun statsIgnoreOtherModesAndUnfinishedGames() {
        val rascal = sampleGame(mode = ScoringMode.RASCAL)
        val running = sampleGame(status = GameStatus.IN_PROGRESS)
        assertTrue(
            StatsCalculator.compute(
                listOf(rascal, running),
                StatsFilter(ScoringMode.CLASSIC),
            ).isEmpty(),
        )
        assertEquals(3, StatsCalculator.compute(listOf(rascal), StatsFilter(ScoringMode.RASCAL)).size)
    }

    @Test
    fun csvHasOneLinePerPlayerAndValidatedRoundWithEscaping() {
        val csv = CsvExporter.export(listOf(sampleGame()))
        val lines = csv.trim().lines()
        assertEquals(1 + 2 * 3, lines.size) // header + 2 validated rounds x 3 players
        assertTrue(lines[0].startsWith("date;partie;mode;manche"))
        assertTrue(csv.contains("\"Inès; \"\"la\"\" pirate\""))
        val leaRound2 = lines.first { it.contains("CLASSIC;2;non;2;Léa") }
        assertTrue(leaRound2.endsWith(";-10;10"))
        assertEquals(2, CsvExporter.export(listOf(sampleGame()), playerId = tom.id).trim().lines().size - 1)
    }

    @Test
    fun exportFilterKeepsGamesByPlayerAndPeriod() {
        val old = sampleGame()
        val filter = ExportFilter(StatsPeriod.ALL, playerId = 99)
        assertTrue(filter.apply(listOf(old)).isEmpty())
        assertEquals(1, ExportFilter(StatsPeriod.ALL, tom.id).apply(listOf(old)).size)
        // A 2023 game falls outside a "this year" filter computed in 2026
        val now = 1_793_000_000_000 // fin octobre 2026
        assertTrue(ExportFilter(StatsPeriod.YEAR).apply(listOf(old), now).isEmpty())
    }
}
