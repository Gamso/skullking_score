package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.usecase.ReorderPlayersUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The order of the players can be fixed during a game, without changing who deals the round in progress. */
class ReorderPlayersUseCaseTest {

    private val repo = FakeGameRepository()
    private val reorder = ReorderPlayersUseCase(repo)
    private val ann = repo.addPlayer("Ann")
    private val bob = repo.addPlayer("Bob")
    private val cid = repo.addPlayer("Cid")
    private val gameId = runBlocking { repo.createGame(listOf(ann, bob, cid), ScoringMode.CLASSIC, emptySet()) }

    private fun game() = assertNotNull(runBlocking { repo.getGame(gameId) }).game

    @Test
    fun `the players are seated in the new order`() = runBlocking {
        reorder(gameId, listOf(cid, ann, bob))

        assertEquals(listOf(cid, ann, bob), game().players.map { it.id })
    }

    @Test
    fun `the dealer of the round in progress stays the same`() = runBlocking {
        val first = repo.addRound(gameId, 1, 1, false)
        repo.updateRound(first) { it.copy(phase = RoundPhase.SCORED) }
        repo.addRound(gameId, 2, 2, false)
        val dealer = assertNotNull(game().dealerFor(2))

        reorder(gameId, listOf(bob, cid, ann))

        assertEquals(dealer.id, game().dealerFor(2)?.id)
        // The rotation then follows the new order.
        val seat = game().players.indexOfFirst { it.id == dealer.id }
        assertEquals(game().players[(seat + 1) % 3].id, game().dealerFor(3)?.id)
    }

    @Test
    fun `a list that is not the players of the game changes nothing`() = runBlocking {
        reorder(gameId, listOf(bob, ann))

        assertEquals(listOf(ann, bob, cid), game().players.map { it.id })
    }
}
