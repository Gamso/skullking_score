package com.skullking.score.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players")
    suspend fun getAll(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): PlayerEntity?

    @Insert
    suspend fun insert(player: PlayerEntity): Long

    @Update
    suspend fun update(player: PlayerEntity)
}

@Dao
interface GameDao {
    // --- Parties ---

    @Query("SELECT * FROM games ORDER BY startedAt DESC")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :gameId")
    fun observeGame(gameId: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<GameEntity?>

    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGame(gameId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE uuid = :uuid LIMIT 1")
    suspend fun findGameByUuid(uuid: String): GameEntity?

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGame(gameId: Long)

    // --- Joueurs de la partie ---

    @Query("SELECT * FROM game_players ORDER BY seat")
    fun observeAllGamePlayers(): Flow<List<GamePlayerEntity>>

    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY seat")
    fun observeGamePlayers(gameId: Long): Flow<List<GamePlayerEntity>>

    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY seat")
    suspend fun getGamePlayers(gameId: Long): List<GamePlayerEntity>

    @Insert
    suspend fun insertGamePlayers(items: List<GamePlayerEntity>)

    // --- Manches ---

    @Query("SELECT * FROM rounds ORDER BY gameId, number")
    fun observeAllRounds(): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY number")
    fun observeRounds(gameId: Long): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE id = :roundId")
    suspend fun getRound(roundId: Long): RoundEntity?

    @Insert
    suspend fun insertRound(round: RoundEntity): Long

    @Update
    suspend fun updateRound(round: RoundEntity)

    // --- Saisies par joueur ---

    @Query("SELECT * FROM player_rounds")
    fun observeAllPlayerRounds(): Flow<List<PlayerRoundEntity>>

    @Query(
        "SELECT pr.* FROM player_rounds pr INNER JOIN rounds r ON r.id = pr.roundId WHERE r.gameId = :gameId",
    )
    fun observePlayerRounds(gameId: Long): Flow<List<PlayerRoundEntity>>

    @Query("SELECT * FROM player_rounds WHERE roundId = :roundId AND playerId = :playerId")
    suspend fun getPlayerRound(roundId: Long, playerId: Long): PlayerRoundEntity?

    @Upsert
    suspend fun upsertPlayerRound(entry: PlayerRoundEntity)

    @Insert
    suspend fun insertPlayerRounds(entries: List<PlayerRoundEntity>)

    @Query("UPDATE player_rounds SET score = :score WHERE roundId = :roundId AND playerId = :playerId")
    suspend fun updateScore(roundId: Long, playerId: Long, score: Int)

    // --- Événements en direct ---

    @Query("SELECT * FROM loot_links ORDER BY createdAt")
    fun observeAllLoots(): Flow<List<LootLinkEntity>>

    @Query(
        "SELECT l.* FROM loot_links l INNER JOIN rounds r ON r.id = l.roundId " +
            "WHERE r.gameId = :gameId ORDER BY l.createdAt",
    )
    fun observeLoots(gameId: Long): Flow<List<LootLinkEntity>>

    @Insert
    suspend fun insertLoot(loot: LootLinkEntity): Long

    @Query("UPDATE loot_links SET cancelledAt = :cancelledAt WHERE id = :id AND cancelledAt IS NULL")
    suspend fun cancelLoot(id: Long, cancelledAt: Long)

    @Query("SELECT * FROM davy_jones_captures ORDER BY createdAt")
    fun observeAllCaptures(): Flow<List<DavyJonesCaptureEntity>>

    @Query(
        "SELECT c.* FROM davy_jones_captures c INNER JOIN rounds r ON r.id = c.roundId " +
            "WHERE r.gameId = :gameId ORDER BY c.createdAt",
    )
    fun observeCaptures(gameId: Long): Flow<List<DavyJonesCaptureEntity>>

    @Insert
    suspend fun insertCapture(capture: DavyJonesCaptureEntity): Long

    @Query("UPDATE davy_jones_captures SET cancelledAt = :cancelledAt WHERE id = :id AND cancelledAt IS NULL")
    suspend fun cancelCapture(id: Long, cancelledAt: Long)

    // --- Plis ---

    @Query("SELECT * FROM tricks ORDER BY roundId, number")
    fun observeAllTricks(): Flow<List<TrickEntity>>

    @Query(
        "SELECT t.* FROM tricks t INNER JOIN rounds r ON r.id = t.roundId " +
            "WHERE r.gameId = :gameId ORDER BY t.roundId, t.number",
    )
    fun observeTricks(gameId: Long): Flow<List<TrickEntity>>

    @Query("SELECT * FROM tricks WHERE roundId = :roundId AND number = :number")
    suspend fun getTrick(roundId: Long, number: Int): TrickEntity?

    @Upsert
    suspend fun upsertTrick(trick: TrickEntity)

    @Query("DELETE FROM tricks WHERE roundId = :roundId AND number = :number")
    suspend fun deleteTrick(roundId: Long, number: Int)

    @Query(
        "UPDATE loot_links SET trickWinnerId = :winnerId " +
            "WHERE roundId = :roundId AND trickNumber = :trickNumber AND cancelledAt IS NULL",
    )
    suspend fun setLootWinner(roundId: Long, trickNumber: Int, winnerId: Long)

    // --- Totaux (cache) ---

    @Query(
        "SELECT r.gameId AS gameId, pr.playerId AS playerId, SUM(pr.score) AS total " +
            "FROM player_rounds pr INNER JOIN rounds r ON r.id = pr.roundId " +
            "WHERE r.phase = 'SCORED' GROUP BY r.gameId, pr.playerId",
    )
    fun observeTotals(): Flow<List<TotalRow>>
}
