package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.usecase.StartRoundUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class StartRoundUseCaseTest {

    private val players = listOf(Player(1, "u1", "Ann", 0, 0), Player(2, "u2", "Bob", 0, 0))

    @Test
    fun aSecondStartWhileTheLastRoundIsNotScoredReturnsThatRound() = runBlocking {
        val games = FakeGameRepository(players)
        val start = StartRoundUseCase(games)
        val gameId = games.createGame(players.map { it.id }, ScoringMode.CLASSIC, emptySet())

        val first = start(gameId, 1)
        val second = start(gameId, 2)

        assertEquals(first, second)
        val rounds = checkNotNull(games.getGame(gameId)).rounds
        assertEquals(1, rounds.size)
        assertEquals(1, rounds.single().round.cardsDealt)
    }

    @Test
    fun theNextRoundIsCreatedOnceTheLastOneIsScored() = runBlocking {
        val games = FakeGameRepository(players)
        val start = StartRoundUseCase(games)
        val gameId = games.createGame(players.map { it.id }, ScoringMode.CLASSIC, emptySet())

        val first = start(gameId, 1)
        games.updateRound(first) { it.copy(phase = RoundPhase.SCORED) }
        val second = start(gameId, 2)

        assertNotEquals(first, second)
        val rounds = checkNotNull(games.getGame(gameId)).rounds
        assertEquals(listOf(1, 2), rounds.map { it.round.number })
        assertEquals(RoundPhase.BIDDING, rounds.last().round.phase)
    }
}
