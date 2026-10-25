package com.skullking.score.domain

import com.skullking.score.core.Bonus
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.WinningCard
import com.skullking.score.data.backup.BackupCodec
import com.skullking.score.data.local.BonusEntity
import com.skullking.score.data.local.TrickEntity
import com.skullking.score.data.local.toDomain
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.repository.NewerBackupException
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.stats.DuelCalculator
import com.skullking.score.domain.stats.ExpansionFilter
import com.skullking.score.domain.stats.PlayerCountFilter
import com.skullking.score.domain.stats.RoundSize
import com.skullking.score.domain.stats.StatsCalculator
import com.skullking.score.domain.stats.StatsFilter
import com.skullking.score.domain.undo.UndoAction
import com.skullking.score.domain.undo.UndoHistory
import com.skullking.score.domain.undo.UndoKind
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Tests des fonctions de la V2 : donneur, annuler / rétablir, statistiques enrichies, face-à-face, import. */
class V2Test {

    private val ann = Player(1, "u1", "Ann", 0xFFE57373, 0)
    private val bob = Player(2, "u2", "Bob", 0xFF4FC3F7, 0)
    private val cat = Player(3, "u3", "Cat", 0xFFAED581, 0)

    /** Chaque manche : une paire (annonce, plis gagnés) par joueur, dans l'ordre de [players]. */
    private fun played(
        id: Long,
        startedAt: Long,
        players: List<Player>,
        rounds: List<List<Pair<Int, Int>>>,
        expansion: Set<ExpansionCard> = emptySet(),
        mode: ScoringMode = ScoringMode.CLASSIC,
        firstDealerSeat: Int = 0,
    ): GameDetails {
        val game = Game(
            id, "g-$id", startedAt, null, mode, expansion, GameStatus.FINISHED, true, players,
            firstDealerSeat = firstDealerSeat,
        )
        val details = rounds.mapIndexed { index, picks ->
            val roundId = id * 100 + index
            val entries = players.zip(picks).map { (p, pick) ->
                PlayerRound(roundId, p.id, pick.first, null, pick.second, false, 0, Bonus.NONE, 0)
            }
            RoundDetails(
                Round(roundId, id, index + 1, index + 1, false, 0, RoundPhase.SCORED),
                entries,
                emptyList(),
                emptyList(),
            )
        }
        return GameDetails(game, details)
    }

    private fun three(vararg rounds: List<Pair<Int, Int>>) = rounds.toList()

    // --- Donneur ---

    @Test
    fun dealerAdvancesOneSeatPerRoundAndWraps() {
        val game = played(1, 0, listOf(ann, bob, cat), emptyList(), firstDealerSeat = 1).game
        assertEquals(bob, game.dealerFor(1))
        assertEquals(cat, game.dealerFor(2))
        assertEquals(ann, game.dealerFor(3))
        assertEquals(bob, game.dealerFor(4))
    }

    // --- Annuler / rétablir ---

    @Test
    fun undoAndRedoReplayActionsInOrder() = runBlocking {
        val history = UndoHistory()
        var value = 0
        fun action(to: Int, from: Int) = UndoAction(UndoKind.BID, "x", { value = from }, { value = to })
        value = 1
        history.record(7, action(1, 0))
        value = 2
        history.record(7, action(2, 1))

        history.undo(7)
        assertEquals(1, value)
        history.undo(7)
        assertEquals(0, value)
        assertNull(history.undo(7))
        history.redo(7)
        assertEquals(1, value)
        history.redo(7)
        assertEquals(2, value)
        assertNull(history.redo(7))
    }

    @Test
    fun recordingClearsRedoAndKeepsGamesApart() = runBlocking {
        val history = UndoHistory()
        val noop = suspend {}
        history.record(1, UndoAction(UndoKind.TRICK, "1", noop, noop))
        history.record(2, UndoAction(UndoKind.TRICK, "2", noop, noop))
        history.undo(1)
        history.record(1, UndoAction(UndoKind.TRICK, "3", noop, noop))
        assertNull(history.redo(1))
        assertNotNull(history.undo(2))
        history.clear(1)
        assertNull(history.undo(1))
    }

    @Test
    fun undoStackIsLimitedToThirtyActions() = runBlocking {
        val history = UndoHistory()
        val noop = suspend {}
        repeat(40) { history.record(1, UndoAction(UndoKind.BID, "$it", noop, noop)) }
        var undone = 0
        while (history.undo(1) != null) undone++
        assertEquals(30, undone)
    }

    // --- Statistiques ---

    @Test
    fun filtersKeepGamesByPlayerCountExpansionAndPresence() {
        val small = played(1, 1_000, listOf(ann, bob), three(listOf(1 to 1, 0 to 0)))
        val big = played(
            2,
            2_000,
            listOf(ann, bob, cat, Player(4, "u4", "Dan", 0, 0)),
            three(listOf(1 to 1, 0 to 0, 0 to 0, 0 to 0)),
            expansion = setOf(ExpansionCard.SEVENS),
        )
        val all = listOf(small, big)
        assertEquals(2, StatsCalculator.select(all, StatsFilter()).size)
        assertEquals(
            listOf(1L),
            StatsCalculator.select(all, StatsFilter(playerCount = PlayerCountFilter.TWO_THREE)).map { it.game.id },
        )
        assertEquals(
            listOf(2L),
            StatsCalculator.select(all, StatsFilter(playerCount = PlayerCountFilter.FOUR_FIVE)).map { it.game.id },
        )
        assertEquals(
            listOf(2L),
            StatsCalculator.select(all, StatsFilter(expansion = ExpansionFilter.WITH)).map { it.game.id },
        )
        assertEquals(
            listOf(1L),
            StatsCalculator.select(all, StatsFilter(expansion = ExpansionFilter.WITHOUT)).map { it.game.id },
        )
        assertEquals(1, StatsCalculator.select(all, StatsFilter(presentPlayerIds = setOf(cat.id))).size)
        assertEquals(2, StatsCalculator.select(all, StatsFilter(presentPlayerIds = setOf(ann.id, bob.id))).size)
    }

    @Test
    fun recordsAndStreaksAreComputedPerPlayer() {
        // Ann : manche 1 exacte (+20), manche 2 exacte (+40), manche 3 ratée (-10 par pli d'écart)
        val first = played(
            1,
            1_000,
            listOf(ann, bob),
            three(listOf(1 to 1, 0 to 1), listOf(2 to 2, 0 to 0), listOf(3 to 1, 3 to 3)),
        )
        // Nouvelle partie : la série repart de zéro ; Ann gagne encore la manche 1
        val second = played(2, 2_000, listOf(ann, bob), three(listOf(1 to 1, 1 to 0)))
        val stats = StatsCalculator.compute(listOf(first, second), StatsFilter())
        val annStats = stats.first { it.player.id == ann.id }
        assertEquals(2, annStats.longestExactStreak)
        assertEquals(40, annStats.bestRound?.points)
        assertEquals(2, annStats.bestRound?.roundNumber)
        assertEquals(-20, annStats.worstRound?.points)
        assertEquals(listOf(2, 1).size, annStats.gameTotals.size)
        // Taille des manches : manche de 1 carte (petite), de 2 (petite), de 3 (petite)
        assertEquals(4, annStats.successBySize[RoundSize.SMALL]?.attempts)
        assertEquals(3, annStats.successBySize[RoundSize.SMALL]?.successes)
    }

    @Test
    fun rollingAverageUsesTheLastFiveGames() {
        val games = (1..7).map { i ->
            played(i.toLong(), i * 1_000L, listOf(ann, bob), three(listOf(i % 3 to i % 3, 0 to 0)))
        }
        val annStats = StatsCalculator.compute(games, StatsFilter()).first { it.player.id == ann.id }
        val rolling = annStats.rollingAverage(window = 5)
        assertEquals(annStats.gameTotals.size, rolling.size)
        assertEquals(annStats.gameTotals.first().toDouble(), rolling.first())
        assertEquals(annStats.gameTotals.takeLast(5).average(), rolling.last())
    }

    @Test
    fun roundSizeBucketsCoverEveryCardCount() {
        assertEquals(RoundSize.SMALL, RoundSize.of(1))
        assertEquals(RoundSize.SMALL, RoundSize.of(3))
        assertEquals(RoundSize.MEDIUM, RoundSize.of(4))
        assertEquals(RoundSize.LARGE, RoundSize.of(10))
        assertEquals(RoundSize.HUGE, RoundSize.of(11))
        assertEquals(RoundSize.HUGE, RoundSize.of(14))
    }

    // --- Face-à-face ---

    @Test
    fun duelCountsWinsTiesAndGaps() {
        val g1 = played(1, 1_000, listOf(ann, bob, cat), three(listOf(1 to 1, 0 to 1, 0 to 0)))
        val g2 = played(2, 2_000, listOf(ann, bob), three(listOf(0 to 1, 1 to 1)))
        val g3 = played(3, 3_000, listOf(ann, bob), three(listOf(1 to 1, 1 to 1)))
        val scored = StatsCalculator.select(listOf(g1, g2, g3), StatsFilter())
        val duel = DuelCalculator.compare(scored, ann, bob)
        assertEquals(3, duel.commonGames)
        assertEquals(1, duel.firstWins)
        assertEquals(1, duel.secondWins)
        assertEquals(1, duel.ties)
        assertTrue(duel.hasEnoughGames)
        val alone = DuelCalculator.compare(scored, ann, cat)
        assertEquals(1, alone.commonGames)
        assertTrue(!alone.hasEnoughGames)
    }

    // --- Sauvegarde ---

    @Test
    fun aBackupFromAFutureVersionIsRefusedExplicitly() {
        val json = BackupCodec.encode(listOf(played(1, 1_000, listOf(ann, bob), three(listOf(1 to 1, 0 to 0)))))
        val future = json.replace("\"formatVersion\":${BackupCodec.FORMAT_VERSION}", "\"formatVersion\":99")
        assertFailsWith<NewerBackupException> { BackupCodec.decode(future) }
    }

    @Test
    fun theDealerSeatSurvivesAnExportImportRoundTrip() {
        val details =
            played(1, 1_000, listOf(ann, bob, cat), three(listOf(1 to 1, 0 to 0, 0 to 0)), firstDealerSeat = 2)
        val dto = BackupCodec.decode(BackupCodec.encode(listOf(details)))
        assertEquals(2, dto.games.single().firstDealerSeat)
        // Le score recalculé ne dépend pas du donneur
        assertEquals(
            GameScoreCalculator.calculate(details).totals,
            GameScoreCalculator.calculate(details.copy(game = details.game.copy(firstDealerSeat = 0))).totals,
        )
    }

    @Test
    fun aStoredTrickKeepsItsWinningCardAndPirate() {
        val entity = TrickEntity(
            roundId = 1,
            number = 1,
            winnerId = 2,
            bonus = BonusEntity(0, 0, 0, 0, 0, 0, 0, 0),
            createdAt = 0,
            winningCard = "PIRATE",
            winningPirate = "RASCAL",
        )
        val trick = entity.toDomain()
        assertEquals(WinningCard.PIRATE, trick.winningCard)
        assertEquals(Pirate.RASCAL, trick.winningPirate)
        assertNull(entity.copy(winningCard = null, winningPirate = null).toDomain().winningCard)
    }
}
