package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.EntryChange
import com.gamso.skullkingscore.domain.undo.UndoAction
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.undo.UndoKind
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.domain.usecase.TrickEventIds
import com.gamso.skullkingscore.fakes.FakeGameRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Live trick entry: one transaction per trick, undo of the events entered with it, steppers sending steps. */
class TrickEntryTest {

    private val repo = FakeGameRepository()
    private val edit = EditRoundUseCase(repo, RecalculateScoresUseCase(repo))
    private val ann = repo.addPlayer("Ann")
    private val bob = repo.addPlayer("Bob")

    private fun newRound(cards: Int = 2): Pair<Long, Long> = runBlocking {
        val gameId = repo.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet())
        gameId to repo.addRound(gameId, 1, cards, false)
    }

    private fun round(gameId: Long): RoundDetails = assertNotNull(runBlocking { repo.getGame(gameId) }).rounds.single()

    @Test
    fun `recording a trick with its events is one transaction and one score update`() = runBlocking {
        val (gameId, roundId) = newRound()
        val transactions = repo.transactions
        val publications = repo.publications
        val scoreWrites = repo.scoreWrites

        edit.recordTrick(
            gameId,
            roundId,
            1,
            TrickDraft(winnerId = ann, bonus = Bonus(colored14 = 1)),
            lootPlayerIds = listOf(bob),
            chests = listOf(bob to setOf(SeaMonster.KRAKEN)),
        )

        assertEquals(transactions + 1, repo.transactions)
        // The observers see a single new state: no trick without its entries, no entry without its score.
        assertEquals(publications + 1, repo.publications)
        assertEquals(scoreWrites + 1, repo.scoreWrites)
        val round = round(gameId)
        assertEquals(1, round.tricks.size)
        assertEquals(1, round.entry(ann)?.tricks)
        assertEquals(1, round.entry(ann)?.bonus?.colored14)
        assertEquals(ann, round.activeLoots.single().trickWinnerId)
        assertEquals(1, round.activeCaptures.size)
        assertTrue(round.entries.any { it.score != 0.0 })
    }

    @Test
    fun `an interrupted trick entry writes nothing`() = runBlocking {
        val (gameId, roundId) = newRound()
        val before = repo.data
        repo.failOnUpdateRound = true

        assertFailsWith<IllegalStateException> {
            edit.recordTrick(gameId, roundId, 1, TrickDraft(winnerId = ann), lootPlayerIds = listOf(bob))
        }

        assertEquals(before, repo.data)
        assertTrue(round(gameId).tricks.isEmpty())
        assertTrue(round(gameId).loots.isEmpty())
    }

    @Test
    fun `deleting the last trick is one transaction`() = runBlocking {
        val (gameId, roundId) = newRound()
        edit.recordTrick(gameId, roundId, 1, TrickDraft(winnerId = ann))
        val transactions = repo.transactions
        val publications = repo.publications

        edit.deleteLastTrick(gameId, roundId)

        assertEquals(transactions + 1, repo.transactions)
        assertEquals(publications + 1, repo.publications)
        assertTrue(round(gameId).tricks.isEmpty())
        assertEquals(0, round(gameId).entry(ann)?.tricks)
    }

    /** Same undo entry as the play screen: undo cancels the events of the trick, redo records them again. */
    @Test
    fun `undoing a trick cancels its Loot and Davy Jones, redo restores them`() = runBlocking {
        val (gameId, roundId) = newRound()
        val history = UndoHistory()
        val draft = TrickDraft(winnerId = ann)
        val record: suspend () -> TrickEventIds = {
            edit.recordTrick(gameId, roundId, 1, draft, listOf(bob), listOf(bob to setOf(SeaMonster.KRAKEN)))
        }
        var created = record()
        history.record(
            gameId,
            UndoAction(
                UndoKind.TRICK,
                "1",
                undo = { edit.undoTrick(gameId, roundId, 1, null, created) },
                redo = { created = record() },
            ),
        )
        assertEquals(1, round(gameId).activeLoots.size)

        history.undo(gameId)
        round(gameId).let {
            assertTrue(it.tricks.isEmpty())
            assertTrue(it.activeLoots.isEmpty(), "the Loot no longer counts towards the limit")
            assertTrue(it.activeCaptures.isEmpty(), "the Davy Jones capture no longer scores")
            assertEquals(0, it.entry(ann)?.tricks)
        }

        history.redo(gameId)
        round(gameId).let {
            assertEquals(1, it.tricks.size)
            assertEquals(ann, it.activeLoots.single().trickWinnerId)
            assertEquals(1, it.activeCaptures.size)
        }

        // A second undo cancels the events recreated by the redo.
        history.undo(gameId)
        assertTrue(round(gameId).activeLoots.isEmpty())
        assertTrue(round(gameId).activeCaptures.isEmpty())
    }

    @Test
    fun `undoing a corrected trick restores it and keeps its earlier events`() = runBlocking {
        val (gameId, roundId) = newRound()
        val first = TrickDraft(winnerId = ann)
        edit.recordTrick(gameId, roundId, 1, first, lootPlayerIds = listOf(bob))
        val created = edit.recordTrick(
            gameId,
            roundId,
            1,
            TrickDraft(winnerId = bob),
            chests = listOf(ann to setOf(SeaMonster.KRAKEN)),
        )

        edit.undoTrick(gameId, roundId, 1, first, created)

        val round = round(gameId)
        assertEquals(ann, round.tricks.single().winnerId)
        assertEquals(ann, round.activeLoots.single().trickWinnerId)
        assertTrue(round.activeCaptures.isEmpty())
    }

    /** Two taps sent before the screen is refreshed: both are applied to the stored value. */
    @Test
    fun `two steps sent from the same screen state give plus two`() = runBlocking {
        val (gameId, roundId) = newRound(cards = 5)
        val step: suspend () -> EntryChange? = {
            edit.updateEntry(gameId, roundId, ann) { it.copy(bid = (it.bid + 1).coerceIn(0, 5), finalBid = null) }
        }

        val changes = listOf(async { step() }, async { step() }).awaitAll()

        assertEquals(2, round(gameId).entry(ann)?.bid)
        // Each effective tap has its own before / after, hence its own undo entry.
        assertEquals(setOf(0 to 1, 1 to 2), changes.map { assertNotNull(it).before.bid to it.after.bid }.toSet())
    }

    @Test
    fun `a step past the limit changes nothing`() = runBlocking {
        val (gameId, roundId) = newRound(cards = 1)
        val step: suspend () -> EntryChange? = {
            edit.updateEntry(gameId, roundId, ann) { it.copy(bid = (it.bid + 1).coerceIn(0, 1), finalBid = null) }
        }
        step()

        val change = assertNotNull(step())

        assertEquals(1, round(gameId).entry(ann)?.bid)
        assertEquals(change.before, change.after, "no change, so no undo entry")
    }
}
