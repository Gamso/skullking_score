package com.gamso.skullkingscore.fakes

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
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.AppSettings
import com.gamso.skullkingscore.domain.repository.EntryChange
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.PlayerRepository
import com.gamso.skullkingscore.domain.repository.ScoreUpdate
import com.gamso.skullkingscore.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [GameRepository] that behaves like the Room one: the rows are kept as entities and assembled into
 * games by the app's own mapping, and the transactions work like Room's. The writes made inside [inTransaction]
 * are read back by the calls made inside it, but are only published to the observers when the outermost
 * transaction ends, and are rolled back if it throws.
 *
 * [players] are the profiles known when the repository is created. [createGameGate] and [getAllGamesGate], when
 * set, suspend [createGame] and [getAllGames] until they are completed, to simulate a slow database.
 */
class FakeGameRepository(players: List<Player> = emptyList()) : GameRepository {
    var data = RawData(
        games = emptyList(),
        gamePlayers = emptyList(),
        players = players.map { it.toEntity() },
        rounds = emptyList(),
        playerRounds = emptyList(),
        loots = emptyList(),
        captures = emptyList(),
    )
        private set
    private val published = MutableStateFlow(data)
    private val writer = Mutex()
    private var depth = 0
    private var nextId = (players.maxOfOrNull { it.id } ?: 0) + 1

    var createGameGate: CompletableDeferred<Unit>? = null
    var getAllGamesGate: CompletableDeferred<Unit>? = null

    /** Number of [getAllGames] calls (read from any thread). */
    @Volatile
    var allGamesReads = 0
        private set

    /** Number of games created. */
    var createdGames = 0
        private set

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

    override fun observeInProgressGame(): Flow<GameDetails?> =
        published.map { raw -> raw.assemble().lastOrNull { it.game.status == GameStatus.IN_PROGRESS } }

    override fun observeSummaries(): Flow<List<GameSummary>> = published.map { raw ->
        raw.assemble().map { details ->
            val totals = details.rounds.flatMap { it.entries }.groupBy { it.playerId }
                .mapValues { (_, entries) -> entries.sumOf { it.score } }
            GameSummary(details.game, totals)
        }
    }

    override fun observeAllGames(): Flow<List<GameDetails>> = published.map { it.assemble() }

    /** Reads the current state, pending writes of the transaction included. */
    override suspend fun getGame(gameId: Long): GameDetails? = data.assemble().firstOrNull { it.game.id == gameId }

    override suspend fun getAllGames(): List<GameDetails> {
        allGamesReads++
        getAllGamesGate?.await()
        return data.assemble()
    }

    /** Adds a profile (not published: call it before observing). */
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
    ): Long {
        createGameGate?.await()
        return write { raw ->
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
            createdGames++
            raw.copy(games = raw.games + game, gamePlayers = raw.gamePlayers + seats) to id
        }
    }

    /** Like Room: the round starts with its bids, with one entry per player. */
    override suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long = write { raw ->
        val id = newId()
        val round = RoundEntity(id, gameId, number, cardsDealt, isExtra, 0, RoundPhase.BIDDING.name)
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

    override suspend fun setFirstDealerSeat(gameId: Long, seat: Int) = write { raw ->
        raw.copy(games = raw.games.map { if (it.id == gameId) it.copy(firstDealerSeat = seat) else it }) to Unit
    }

    override suspend fun finishGame(gameId: Long) = write { raw ->
        raw.copy(
            games = raw.games.map {
                if (it.id == gameId) it.copy(status = GameStatus.FINISHED.name, endedAt = 1) else it
            },
        ) to Unit
    }

    /** Like the cascade of the database: the rounds and their rows go with the game, the players stay. */
    override suspend fun deleteGame(gameId: Long) = write { raw ->
        val roundIds = raw.rounds.filter { it.gameId == gameId }.map { it.id }.toSet()
        raw.copy(
            games = raw.games.filterNot { it.id == gameId },
            gamePlayers = raw.gamePlayers.filterNot { it.gameId == gameId },
            rounds = raw.rounds.filterNot { it.id in roundIds },
            playerRounds = raw.playerRounds.filterNot { it.roundId in roundIds },
            loots = raw.loots.filterNot { it.roundId in roundIds },
            captures = raw.captures.filterNot { it.roundId in roundIds },
            tricks = raw.tricks.filterNot { it.roundId in roundIds },
        ) to Unit
    }
}

class FakePlayerRepository(initial: List<Player>) : PlayerRepository {
    private val players = MutableStateFlow(initial)

    override fun observePlayers(): Flow<List<Player>> = players

    override suspend fun getAllPlayers(): List<Player> = players.value

    override suspend fun createPlayer(name: String): Long {
        val id = (players.value.maxOfOrNull { it.id } ?: 0) + 1
        players.update { it + Player(id, "p-$id", name, 0, 0) }
        return id
    }

    override suspend fun updatePlayer(player: Player) {
        players.update { all -> all.map { if (it.id == player.id) player else it } }
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state

    override fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}
