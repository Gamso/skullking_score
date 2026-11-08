package com.gamso.skullkingscore.domain.repository

import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameSummary
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.TrickDraft
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    /** All profiles, archived ones included (filtering is a display concern). */
    fun observePlayers(): Flow<List<Player>>

    suspend fun getAllPlayers(): List<Player>

    suspend fun createPlayer(name: String): Long

    suspend fun updatePlayer(player: Player)
}

data class ScoreUpdate(val roundId: Long, val playerId: Long, val score: Double)

interface GameRepository {
    fun observeGame(gameId: Long): Flow<GameDetails?>

    /** The most recent game in progress, if any. */
    fun observeInProgressGame(): Flow<GameDetails?>

    fun observeSummaries(): Flow<List<GameSummary>>

    /** All games with their detail, for the statistics. */
    fun observeAllGames(): Flow<List<GameDetails>>

    suspend fun getGame(gameId: Long): GameDetails?

    suspend fun getAllGames(): List<GameDetails>

    suspend fun createGame(
        playerIds: List<Long>,
        mode: ScoringMode,
        expansionCards: Set<ExpansionCard>,
        firstDealerSeat: Int = 0,
    ): Long

    /** Creates the round and one entry row per player. */
    suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long

    /** Atomic read-modify-write, to avoid losing a quick entry. */
    suspend fun updateRound(roundId: Long, transform: (Round) -> Round)

    suspend fun updateEntry(roundId: Long, playerId: Long, transform: (PlayerRound) -> PlayerRound)

    suspend fun addLoot(roundId: Long, lootPlayerId: Long, trickWinnerId: Long?, trickNumber: Int? = null)

    /** Creates or replaces trick [number] of the round. */
    suspend fun upsertTrick(roundId: Long, number: Int, draft: TrickDraft)

    suspend fun deleteTrick(roundId: Long, number: Int)

    /** Sets (or clears, with `null`) the winner of the Loots played during this trick. */
    suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long?)

    suspend fun addDavyJones(roundId: Long, playerId: Long, creatures: Set<SeaMonster>, trickNumber: Int? = null)

    suspend fun cancelLoot(lootId: Long)

    suspend fun cancelDavyJones(captureId: Long)

    suspend fun updateScores(scores: List<ScoreUpdate>)

    suspend fun setFirstDealerSeat(gameId: Long, seat: Int)

    suspend fun finishGame(gameId: Long)

    suspend fun deleteGame(gameId: Long)
}
