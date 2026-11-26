package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.scoring.GameScoreCalculator
import com.gamso.skullkingscore.domain.usecase.ChangeModeUseCase
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The scoring mode can be changed during a game: the rounds already played are scored again with it. */
class ChangeModeUseCaseTest {

    private val repo = FakeGameRepository()
    private val recalculate = RecalculateScoresUseCase(repo)
    private val edit = EditRoundUseCase(repo, recalculate)
    private val changeMode = ChangeModeUseCase(repo, recalculate)
    private val ann = repo.addPlayer("Ann")
    private val bob = repo.addPlayer("Bob")
    private val gameId = runBlocking { repo.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet()) }

    private fun cachedScore(playerId: Long): Double =
        assertNotNull(runBlocking { repo.getGame(gameId) }).rounds.single().entry(playerId)!!.score

    @Test
    fun `the rounds already played are scored again with the new mode`() = runBlocking {
        val roundId = repo.addRound(gameId, 1, 2, false)
        // Ann bids 1 and wins no trick: a missed bid, scored differently in each mode.
        edit.updateEntry(gameId, roundId, ann) { it.copy(bid = 1, tricks = 0) }
        edit.updateEntry(gameId, roundId, bob) { it.copy(bid = 0, tricks = 2) }
        edit.setPhase(gameId, roundId, RoundPhase.SCORED)
        val classic = cachedScore(ann)

        changeMode(gameId, ScoringMode.RASCAL)

        val details = assertNotNull(repo.getGame(gameId))
        assertEquals(ScoringMode.RASCAL, details.game.mode)
        assertNotEquals(classic, cachedScore(ann))
        assertEquals(GameScoreCalculator.calculate(details).totals[ann], cachedScore(ann))
    }

    @Test
    fun `the change and the new scores are one transaction`() = runBlocking {
        val transactions = repo.transactions
        changeMode(gameId, ScoringMode.CANNONBALL)
        assertEquals(transactions + 1, repo.transactions)
    }
}
