package com.gamso.skullkingscore.domain

import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.data.local.DavyJonesCaptureEntity
import com.gamso.skullkingscore.data.local.GameEntity
import com.gamso.skullkingscore.data.local.GamePlayerEntity
import com.gamso.skullkingscore.data.local.LootLinkEntity
import com.gamso.skullkingscore.data.local.PENDING_WINNER
import com.gamso.skullkingscore.data.local.PlayerEntity
import com.gamso.skullkingscore.data.local.PlayerRoundEntity
import com.gamso.skullkingscore.data.local.RawData
import com.gamso.skullkingscore.data.local.RoundEntity
import com.gamso.skullkingscore.data.local.TrickEntity
import com.gamso.skullkingscore.data.local.assemble
import com.gamso.skullkingscore.data.local.encodeCreatures
import com.gamso.skullkingscore.data.local.encodeExpansion
import com.gamso.skullkingscore.data.local.toDomain
import com.gamso.skullkingscore.data.local.toEntity
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.GameSummary
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.EntryChange
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.ScoreUpdate
import com.gamso.skullkingscore.domain.undo.UndoAction
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.undo.UndoKind
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.domain.usecase.TrickEventIds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Test

/**
 * In-memory [GameRepository] that behaves like the Room one for transactions: the writes made inside
 * [inTransaction] are read back by the calls made inside it, but are only published to the observers when the
 * outermost transaction ends, and are rolled back if it throws.
 */
private class FakeGameRepository : GameRepository {
    var data = RawData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        private set
    private val published = MutableStateFlow(data)
    private val writer = Mutex()
    private var depth = 0
    private var nextId = 1L

    /** Number of states seen by the observers (each one a committed write). */
    var publications = 0
        private set

    /** Number of top-level transactions. */
    var transactions = 0
        private set

    /** Number of score writes ([updateScores] calls). */
    var scoreWrites = 0
        private set

    /** Makes [updateRound] throw, to simulate an interruption in the middle of a write. */
    var failOnUpdateRound = false

    private fun newId() = nextId++

    private fun publish() {
        published.value = data
        publications++
    }

    /** One write: published right away outside a transaction, at its end inside one. */
    private suspend fun <R> write(change: (RawData) -> Pair<RawData, R>): R = inTransaction {
        val (updated, result) = change(data)
        data = updated
        result
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        if (depth > 0) {
            depth++
            try {
                return block()
            } finally {
                depth--
            }
        }
        // Like a database, one writer at a time: concurrent transactions are serialized.
        return writer.withLock {
            val snapshot = data
            depth++
            try {
                block().also {
                    transactions++
                    publish()
                }
            } catch (e: Throwable) {
                data = snapshot
                throw e
            } finally {
                depth--
            }
        }
    }

    override fun observeGame(gameId: Long): Flow<GameDetails?> =
        published.map { raw -> raw.assemble().firstOrNull { it.game.id == gameId } }

    override fun observeInProgressGame(): Flow<GameDetails?> = error("Not used")

    override fun observeSummaries(): Flow<List<GameSummary>> = error("Not used")

    override fun observeAllGames(): Flow<List<GameDetails>> = error("Not used")

    /** Reads the current state, pending writes of the transaction included. */
    override suspend fun getGame(gameId: Long): GameDetails? = data.assemble().firstOrNull { it.game.id == gameId }

    override suspend fun getAllGames(): List<GameDetails> = data.assemble()

    fun addPlayer(name: String): Long {
        val id = newId()
        data = data.copy(players = data.players + PlayerEntity(id, "u$id", name, 0, 0, false))
        return id
    }

    override suspend fun createGame(
        playerIds: List<Long>,
        mode: ScoringMode,
        expansionCards: Set<ExpansionCard>,
        firstDealerSeat: Int,
    ): Long = write { raw ->
        val id = newId()
        val game = GameEntity(
            id,
            "g$id",
            0,
            null,
            mode.name,
            encodeExpansion(expansionCards),
            GameStatus.IN_PROGRESS.name,
            firstDealerSeat,
        )
        val seats = playerIds.mapIndexed { seat, playerId -> GamePlayerEntity(id, playerId, seat) }
        raw.copy(games = raw.games + game, gamePlayers = raw.gamePlayers + seats) to id
    }

    override suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long = write { raw ->
        val id = newId()
        val round = RoundEntity(id, gameId, number, cardsDealt, isExtra, 0, RoundPhase.PLAYING.name)
        val entries = raw.gamePlayers.filter { it.gameId == gameId }.map {
            PlayerRound(id, it.playerId, 0, null, 0, false, 0, Bonus.NONE, 0.0).toEntity()
        }
        raw.copy(rounds = raw.rounds + round, playerRounds = raw.playerRounds + entries) to id
    }

    override suspend fun updateRound(roundId: Long, transform: (Round) -> Round) {
        check(!failOnUpdateRound) { "Interrupted" }
        write { raw ->
            raw.copy(
                rounds = raw.rounds.map { if (it.id == roundId) transform(it.toDomain()).toEntity() else it },
            ) to Unit
        }
    }

    override suspend fun updateEntry(
        roundId: Long,
        playerId: Long,
        transform: (PlayerRound) -> PlayerRound,
    ): EntryChange? = write { raw ->
        val current = raw.playerRounds.firstOrNull { it.roundId == roundId && it.playerId == playerId }
            ?: return@write raw to null
        val before = current.toDomain()
        val after = transform(before).copy(roundId = roundId, playerId = playerId)
        raw.copy(playerRounds = raw.playerRounds.map { if (it === current) after.toEntity() else it }) to
            EntryChange(before, after)
    }

    override suspend fun addLoot(roundId: Long, lootPlayerId: Long, trickWinnerId: Long?, trickNumber: Int?): Long =
        write { raw ->
            val id = newId()
            val loot = LootLinkEntity(id, roundId, lootPlayerId, trickWinnerId ?: PENDING_WINNER, 0, null, trickNumber)
            raw.copy(loots = raw.loots + loot) to id
        }

    override suspend fun upsertTrick(roundId: Long, number: Int, draft: TrickDraft) = write { raw ->
        val others = raw.tricks.filterNot { it.roundId == roundId && it.number == number }
        val trick = TrickEntity(
            id = newId(),
            roundId = roundId,
            number = number,
            winnerId = draft.winnerId,
            bonus = draft.bonus.toEntity(),
            createdAt = 0,
            rascalWager = draft.rascalWager,
            bidAdjustment = draft.bidAdjustment,
        )
        raw.copy(tricks = others + trick) to Unit
    }

    override suspend fun deleteTrick(roundId: Long, number: Int) = write { raw ->
        raw.copy(tricks = raw.tricks.filterNot { it.roundId == roundId && it.number == number }) to Unit
    }

    override suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long?) = write { raw ->
        raw.copy(
            loots = raw.loots.map {
                if (it.roundId == roundId && it.trickNumber == trickNumber && it.cancelledAt == null) {
                    it.copy(trickWinnerId = winnerId ?: PENDING_WINNER)
                } else {
                    it
                }
            },
        ) to Unit
    }

    override suspend fun addDavyJones(
        roundId: Long,
        playerId: Long,
        creatures: Set<SeaMonster>,
        trickNumber: Int?,
    ): Long = write { raw ->
        val id = newId()
        val capture = DavyJonesCaptureEntity(id, roundId, playerId, encodeCreatures(creatures), 0, null, trickNumber)
        raw.copy(captures = raw.captures + capture) to id
    }

    override suspend fun cancelLoot(lootId: Long) = write { raw ->
        raw.copy(loots = raw.loots.map { if (it.id == lootId) it.copy(cancelledAt = 1) else it }) to Unit
    }

    override suspend fun cancelDavyJones(captureId: Long) = write { raw ->
        raw.copy(captures = raw.captures.map { if (it.id == captureId) it.copy(cancelledAt = 1) else it }) to Unit
    }

    override suspend fun updateScores(scores: List<ScoreUpdate>) = write { raw ->
        scoreWrites++
        raw.copy(
            playerRounds = raw.playerRounds.map { entry ->
                scores.firstOrNull { it.roundId == entry.roundId && it.playerId == entry.playerId }
                    ?.let { entry.copy(score = it.score) } ?: entry
            },
        ) to Unit
    }

    override suspend fun setFirstDealerSeat(gameId: Long, seat: Int) = error("Not used")

    override suspend fun finishGame(gameId: Long) = error("Not used")

    override suspend fun deleteGame(gameId: Long) = error("Not used")
}

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
