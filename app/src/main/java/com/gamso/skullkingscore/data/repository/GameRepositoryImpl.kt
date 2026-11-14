package com.gamso.skullkingscore.data.repository

import androidx.room3.withWriteTransaction
import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.data.local.DavyJonesCaptureEntity
import com.gamso.skullkingscore.data.local.GameDao
import com.gamso.skullkingscore.data.local.GameEntity
import com.gamso.skullkingscore.data.local.GamePlayerEntity
import com.gamso.skullkingscore.data.local.LootLinkEntity
import com.gamso.skullkingscore.data.local.PENDING_WINNER
import com.gamso.skullkingscore.data.local.PlayerDao
import com.gamso.skullkingscore.data.local.PlayerRoundEntity
import com.gamso.skullkingscore.data.local.RawData
import com.gamso.skullkingscore.data.local.RoundEntity
import com.gamso.skullkingscore.data.local.SkullKingDatabase
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
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.EntryChange
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.ScoreUpdate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@Singleton
class GameRepositoryImpl @Inject constructor(
    private val database: SkullKingDatabase,
    private val gameDao: GameDao,
    private val playerDao: PlayerDao,
) : GameRepository {

    private data class RoundData(
        val rounds: List<RoundEntity>,
        val playerRounds: List<PlayerRoundEntity>,
        val loots: List<LootLinkEntity>,
        val captures: List<DavyJonesCaptureEntity>,
        val tricks: List<TrickEntity>,
    )

    // Nested calls reuse the transaction's connection (a savepoint), so they see its pending writes.
    override suspend fun <R> inTransaction(block: suspend () -> R): R = database.withWriteTransaction { block() }

    override fun observeGame(gameId: Long): Flow<GameDetails?> {
        val header = combine(
            gameDao.observeGame(gameId),
            gameDao.observeGamePlayers(gameId),
            playerDao.observeAll(),
        ) { game, seats, players -> Triple(game, seats, players) }
        val body = combine(
            gameDao.observeRounds(gameId),
            gameDao.observePlayerRounds(gameId),
            gameDao.observeLoots(gameId),
            gameDao.observeCaptures(gameId),
            gameDao.observeTricks(gameId),
        ) { rounds, entries, loots, captures, tricks -> RoundData(rounds, entries, loots, captures, tricks) }
        return combine(header, body) { (game, seats, players), data ->
            RawData(
                games = listOfNotNull(game),
                gamePlayers = seats,
                players = players,
                rounds = data.rounds,
                playerRounds = data.playerRounds,
                loots = data.loots,
                captures = data.captures,
                tricks = data.tricks,
            ).assemble().firstOrNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInProgressGame(): Flow<GameDetails?> = gameDao.observeInProgress().flatMapLatest { game ->
        if (game == null) flowOf(null) else observeGame(game.id)
    }

    override fun observeSummaries(): Flow<List<GameSummary>> = combine(
        gameDao.observeGames(),
        gameDao.observeAllGamePlayers(),
        playerDao.observeAll(),
        gameDao.observeTotals(),
    ) { games, seats, players, totals ->
        val playersById = players.associate { it.id to it.toDomain() }
        val seatsByGame = seats.groupBy { it.gameId }
        val totalsByGame = totals.groupBy { it.gameId }
        games.map { game ->
            val ordered = seatsByGame[game.id].orEmpty().sortedBy { it.seat }.mapNotNull { playersById[it.playerId] }
            GameSummary(
                game = game.toDomain(ordered),
                totals = totalsByGame[game.id].orEmpty().associate { it.playerId to it.total },
            )
        }
    }

    override fun observeAllGames(): Flow<List<GameDetails>> {
        val header = combine(
            gameDao.observeGames(),
            gameDao.observeAllGamePlayers(),
            playerDao.observeAll(),
        ) { games, seats, players -> Triple(games, seats, players) }
        val body = combine(
            gameDao.observeAllRounds(),
            gameDao.observeAllPlayerRounds(),
            gameDao.observeAllLoots(),
            gameDao.observeAllCaptures(),
            gameDao.observeAllTricks(),
        ) { rounds, entries, loots, captures, tricks -> RoundData(rounds, entries, loots, captures, tricks) }
        return combine(header, body) { (games, seats, players), data ->
            RawData(
                games,
                seats,
                players,
                data.rounds,
                data.playerRounds,
                data.loots,
                data.captures,
                data.tricks,
            ).assemble()
        }
    }

    override suspend fun getGame(gameId: Long): GameDetails? = observeGame(gameId).first()

    override suspend fun getAllGames(): List<GameDetails> = observeAllGames().first()

    override suspend fun createGame(
        playerIds: List<Long>,
        mode: ScoringMode,
        expansionCards: Set<ExpansionCard>,
        firstDealerSeat: Int,
    ): Long = database.withWriteTransaction {
        val gameId = gameDao.insertGame(
            GameEntity(
                uuid = UUID.randomUUID().toString(),
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                mode = mode.name,
                expansionCards = encodeExpansion(expansionCards),
                status = GameStatus.IN_PROGRESS.name,
                firstDealerSeat = firstDealerSeat,
            ),
        )
        gameDao.insertGamePlayers(playerIds.mapIndexed { seat, playerId -> GamePlayerEntity(gameId, playerId, seat) })
        gameId
    }

    override suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long =
        database.withWriteTransaction {
            val roundId = gameDao.insertRound(
                RoundEntity(
                    gameId = gameId,
                    number = number,
                    cardsDealt = cardsDealt,
                    isExtra = isExtra,
                    discardedTricks = 0,
                    phase = RoundPhase.BIDDING.name,
                ),
            )
            val seats = gameDao.getGamePlayers(gameId)
            gameDao.insertPlayerRounds(
                seats.map { seat ->
                    PlayerRound(
                        roundId = roundId,
                        playerId = seat.playerId,
                        bid = 0,
                        finalBid = null,
                        tricks = 0,
                        cannonball = false,
                        rascalWager = 0,
                        bonus = Bonus.NONE,
                        score = 0.0,
                    ).toEntity()
                },
            )
            roundId
        }

    override suspend fun updateRound(roundId: Long, transform: (Round) -> Round) {
        database.withWriteTransaction {
            val current = gameDao.getRound(roundId) ?: return@withWriteTransaction
            gameDao.updateRound(transform(current.toDomain()).copy(id = roundId).toEntity())
        }
    }

    override suspend fun updateEntry(
        roundId: Long,
        playerId: Long,
        transform: (PlayerRound) -> PlayerRound,
    ): EntryChange? = database.withWriteTransaction {
        val current = gameDao.getPlayerRound(roundId, playerId)?.toDomain() ?: return@withWriteTransaction null
        val updated = transform(current).copy(roundId = roundId, playerId = playerId)
        gameDao.upsertPlayerRound(updated.toEntity())
        EntryChange(current, updated)
    }

    override suspend fun addLoot(roundId: Long, lootPlayerId: Long, trickWinnerId: Long?, trickNumber: Int?): Long =
        gameDao.insertLoot(
            LootLinkEntity(
                roundId = roundId,
                lootPlayerId = lootPlayerId,
                trickWinnerId = trickWinnerId ?: PENDING_WINNER,
                createdAt = System.currentTimeMillis(),
                cancelledAt = null,
                trickNumber = trickNumber,
            ),
        )

    override suspend fun addDavyJones(
        roundId: Long,
        playerId: Long,
        creatures: Set<SeaMonster>,
        trickNumber: Int?,
    ): Long = gameDao.insertCapture(
        DavyJonesCaptureEntity(
            roundId = roundId,
            playerId = playerId,
            creatures = encodeCreatures(creatures),
            createdAt = System.currentTimeMillis(),
            cancelledAt = null,
            trickNumber = trickNumber,
        ),
    )

    override suspend fun upsertTrick(roundId: Long, number: Int, draft: TrickDraft) {
        database.withWriteTransaction {
            val existing = gameDao.getTrick(roundId, number)
            gameDao.upsertTrick(
                TrickEntity(
                    id = existing?.id ?: 0,
                    roundId = roundId,
                    number = number,
                    winnerId = draft.winnerId,
                    bonus = draft.bonus.toEntity(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    rascalWager = draft.rascalWager,
                    bidAdjustment = draft.bidAdjustment,
                    winningCard = draft.winningCard?.name,
                    winningPirate = draft.winningPirate?.name,
                ),
            )
        }
    }

    override suspend fun deleteTrick(roundId: Long, number: Int) = gameDao.deleteTrick(roundId, number)

    override suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long?) =
        gameDao.setLootWinner(roundId, trickNumber, winnerId ?: PENDING_WINNER)

    override suspend fun cancelLoot(lootId: Long) = gameDao.cancelLoot(lootId, System.currentTimeMillis())

    override suspend fun cancelDavyJones(captureId: Long) = gameDao.cancelCapture(captureId, System.currentTimeMillis())

    override suspend fun updateScores(scores: List<ScoreUpdate>) {
        database.withWriteTransaction {
            scores.forEach { gameDao.updateScore(it.roundId, it.playerId, it.score) }
        }
    }

    override suspend fun setFirstDealerSeat(gameId: Long, seat: Int) {
        val game = gameDao.getGame(gameId) ?: return
        gameDao.updateGame(game.copy(firstDealerSeat = seat))
    }

    override suspend fun finishGame(gameId: Long) {
        val game = gameDao.getGame(gameId) ?: return
        gameDao.updateGame(
            game.copy(status = GameStatus.FINISHED.name, endedAt = System.currentTimeMillis()),
        )
    }

    override suspend fun deleteGame(gameId: Long) = gameDao.deleteGame(gameId)
}
