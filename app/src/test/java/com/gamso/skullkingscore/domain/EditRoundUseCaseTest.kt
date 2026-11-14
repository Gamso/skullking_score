package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The events and entries of a round: each change is one transaction that also recomputes the scores. */
class EditRoundUseCaseTest {

    private val repo = FakeGameRepository()
    private val edit = EditRoundUseCase(repo, RecalculateScoresUseCase(repo))
    private val ann = repo.addPlayer("Ann")
    private val bob = repo.addPlayer("Bob")
    private val gameId = runBlocking { repo.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet()) }
    private val roundId = runBlocking { repo.addRound(gameId, 1, 2, false) }

    private fun round(): RoundDetails = assertNotNull(runBlocking { repo.getGame(gameId) }).rounds.single()

    /** Runs [block] and checks that it made exactly one transaction and one score update. */
    private fun <R> oneWrite(block: suspend () -> R): R = runBlocking {
        val transactions = repo.transactions
        val scoreWrites = repo.scoreWrites
        val result = block()
        assertEquals(transactions + 1, repo.transactions, "one transaction")
        assertEquals(scoreWrites + 1, repo.scoreWrites, "the scores are recomputed in it")
        result
    }

    @Test
    fun `a Loot is added with its winner and trick, then cancelled`() {
        val lootId = oneWrite { edit.addLoot(gameId, roundId, bob, ann, trickNumber = 1) }

        val loot = round().activeLoots.single()
        assertEquals(lootId, loot.id)
        assertEquals(bob, loot.lootPlayerId)
        assertEquals(ann, loot.trickWinnerId)
        assertEquals(1, loot.trickNumber)

        oneWrite { edit.cancelLoot(gameId, lootId) }

        assertTrue(round().activeLoots.isEmpty())
        // Cancelled, not deleted: the history keeps it.
        assertNotNull(round().loots.single().cancelledAt)
    }

    @Test
    fun `a Loot played during a trick waits for its winner`() {
        oneWrite { edit.addLoot(gameId, roundId, bob, trickWinnerId = null) }

        assertNull(round().activeLoots.single().trickWinnerId)
    }

    @Test
    fun `a Davy Jones capture is added with its creatures, then cancelled`() {
        val creatures = setOf(SeaMonster.KRAKEN, SeaMonster.WHITE_WHALE)
        val captureId = oneWrite { edit.addDavyJones(gameId, roundId, ann, creatures, trickNumber = 2) }

        val capture = round().activeCaptures.single()
        assertEquals(captureId, capture.id)
        assertEquals(ann, capture.playerId)
        assertEquals(creatures, capture.creatures)
        assertEquals(2, capture.trickNumber)

        oneWrite { edit.cancelDavyJones(gameId, captureId) }

        assertTrue(round().activeCaptures.isEmpty())
        assertNotNull(round().captures.single().cancelledAt)
    }

    @Test
    fun `an entry change returns the entry before and after it and updates the score`() {
        // Ann bids 1 of the 2 cards and wins one trick: 20 points. Bob bids 0 and wins one trick: -20.
        oneWrite { edit.updateEntry(gameId, roundId, ann) { it.copy(bid = 1, tricks = 1) } }
        val change = oneWrite { edit.updateEntry(gameId, roundId, bob) { it.copy(tricks = 1) } }

        assertNotNull(change)
        assertEquals(0, change.before.tricks)
        assertEquals(1, change.after.tricks)
        assertEquals(20.0, round().entry(ann)?.score)
        assertEquals(-20.0, round().entry(bob)?.score)
    }

    @Test
    fun `the change of an entry that does not exist returns null`() {
        val unknownPlayer = ann + bob + 100

        assertNull(oneWrite { edit.updateEntry(gameId, roundId, unknownPlayer) { it.copy(bid = 1) } })
    }

    @Test
    fun `a round change is written with the scores`() {
        oneWrite { edit.updateRound(gameId, roundId) { it.copy(phase = RoundPhase.SCORED, discardedTricks = 1) } }

        val round = round().round
        assertEquals(RoundPhase.SCORED, round.phase)
        assertEquals(1, round.discardedTricks)
    }
}
