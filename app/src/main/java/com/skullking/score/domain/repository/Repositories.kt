package com.skullking.score.domain.repository

import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameSummary
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.TrickDraft
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    /** Tous les profils, archivés compris (le filtrage est une affaire d'affichage). */
    fun observePlayers(): Flow<List<Player>>

    suspend fun getAllPlayers(): List<Player>

    suspend fun createPlayer(name: String): Long

    suspend fun updatePlayer(player: Player)
}

data class ScoreUpdate(val roundId: Long, val playerId: Long, val score: Int)

interface GameRepository {
    fun observeGame(gameId: Long): Flow<GameDetails?>

    /** La partie en cours la plus récente, s'il y en a une. */
    fun observeInProgressGame(): Flow<GameDetails?>

    fun observeSummaries(): Flow<List<GameSummary>>

    /** Toutes les parties avec leur détail, pour les statistiques. */
    fun observeAllGames(): Flow<List<GameDetails>>

    suspend fun getGame(gameId: Long): GameDetails?

    suspend fun getAllGames(): List<GameDetails>

    suspend fun createGame(
        playerIds: List<Long>,
        mode: ScoringMode,
        expansionCards: Set<ExpansionCard>,
        davyJonesRequiresSuccess: Boolean,
        firstDealerSeat: Int = 0,
    ): Long

    /** Crée la manche et une ligne de saisie par joueur. */
    suspend fun addRound(gameId: Long, number: Int, cardsDealt: Int, isExtra: Boolean): Long

    /** Lecture-modification-écriture atomique, pour éviter de perdre une saisie rapide. */
    suspend fun updateRound(roundId: Long, transform: (Round) -> Round)

    suspend fun updateEntry(roundId: Long, playerId: Long, transform: (PlayerRound) -> PlayerRound)

    suspend fun addLoot(roundId: Long, lootPlayerId: Long, trickWinnerId: Long?, trickNumber: Int? = null)

    /** Crée ou remplace le pli [number] de la manche. */
    suspend fun upsertTrick(roundId: Long, number: Int, draft: TrickDraft)

    suspend fun deleteTrick(roundId: Long, number: Int)

    /** Renseigne (ou efface, avec `null`) le gagnant des Butins joués pendant ce pli. */
    suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long?)

    suspend fun addDavyJones(roundId: Long, playerId: Long, creatures: Set<SeaMonster>, trickNumber: Int? = null)

    suspend fun cancelLoot(lootId: Long)

    suspend fun cancelDavyJones(captureId: Long)

    suspend fun updateScores(scores: List<ScoreUpdate>)

    suspend fun setFirstDealerSeat(gameId: Long, seat: Int)

    suspend fun finishGame(gameId: Long)

    suspend fun deleteGame(gameId: Long)
}
