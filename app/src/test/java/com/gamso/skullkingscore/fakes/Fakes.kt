package com.gamso.skullkingscore.fakes

import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.Game
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.GameSummary
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.RoundDetails
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

/**
 * In-memory [GameRepository] for the JVM tests: games, rounds and their phase only. [createGameGate], when set,
 * suspends [createGame] until it is completed, to simulate a slow database.
 */
class FakeGameRepository(private val players: List<Player> = emptyList()) : GameRepository {
    private val games = MutableStateFlow<Map<Long, GameDetails>>(emptyMap())
    private var nextId = 1L
    var createGameGate: CompletableDeferred<Unit>? = null
    var createdGames = 0
        private set

    override fun observeGame(gameId: Long): Flow<GameDetails?> = games.map { it[gameId] }

    override fun observeInProgressGame(): Flow<GameDetails?> =
        games.map { all -> all.values.lastOrNull { it.game.status == GameStatus.IN_PROGRESS } }

    override fun observeSummaries(): Flow<List<GameSummary>> =
        games.map { all -> all.values.map { GameSummary(it.game, emptyMap()) } }

    override fun observeAllGames(): Flow<List<GameDetails>> = games.map { it.values.toList() }

    override suspend fun getGame(gameId: Long): GameDetails? = games.value[gameId]

    override suspend fun getAllGames(): List<GameDetails> = games.value.values.toList()

    override suspend fun createGame(
        playerIds: List<Long>,
        mode: ScoringMode,
        expansionCards: Set<ExpansionCard>,
        firstDealerSeat: Int,
    ): Long {
        createGameGate?.await()
        val id = nextId++
        val game = Game(
            id = id,
            uuid = "g-$id",
            startedAt = 0,
            endedAt = null,
            mode = mode,
            expansionCards = expansionCards,
            status = GameStatus.IN_PROGRESS,
            players = playerIds.mapNotNull { pid -> players.firstOrNull { it.id == pid } },
            firstDealerSeat = firstDealerSeat,
        )
        games.update { it + (id to GameDetails(game, emptyList())) }
        createdGames++
        return id
    }

    override suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long {
        val id = nextId++
        val round = Round(id, gameId, number, cardsDealt, isExtra, 0, RoundPhase.BIDDING)
        val added = RoundDetails(round, emptyList(), emptyList(), emptyList())
        games.update { all ->
            val details = all.getValue(gameId)
            all + (gameId to details.copy(rounds = details.rounds + added))
        }
        return id
    }

    override suspend fun updateRound(roundId: Long, transform: (Round) -> Round) {
        games.update { all ->
            all.mapValues { (_, details) ->
                details.copy(
                    rounds = details.rounds.map {
                        if (it.round.id == roundId) it.copy(round = transform(it.round)) else it
                    },
                )
            }
        }
    }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()

    override suspend fun updateEntry(
        roundId: Long,
        playerId: Long,
        transform: (PlayerRound) -> PlayerRound,
    ): EntryChange? = null

    override suspend fun addLoot(roundId: Long, lootPlayerId: Long, trickWinnerId: Long?, trickNumber: Int?): Long =
        nextId++

    override suspend fun upsertTrick(roundId: Long, number: Int, draft: TrickDraft) = Unit

    override suspend fun deleteTrick(roundId: Long, number: Int) = Unit

    override suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long?) = Unit

    override suspend fun addDavyJones(
        roundId: Long,
        playerId: Long,
        creatures: Set<SeaMonster>,
        trickNumber: Int?,
    ): Long = nextId++

    override suspend fun cancelLoot(lootId: Long) = Unit

    override suspend fun cancelDavyJones(captureId: Long) = Unit

    override suspend fun updateScores(scores: List<ScoreUpdate>) = Unit

    override suspend fun setFirstDealerSeat(gameId: Long, seat: Int) {
        games.update { all ->
            val details = all.getValue(gameId)
            all + (gameId to details.copy(game = details.game.copy(firstDealerSeat = seat)))
        }
    }

    override suspend fun finishGame(gameId: Long) {
        games.update { all ->
            val details = all.getValue(gameId)
            all + (gameId to details.copy(game = details.game.copy(status = GameStatus.FINISHED)))
        }
    }

    override suspend fun deleteGame(gameId: Long) {
        games.update { it - gameId }
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
