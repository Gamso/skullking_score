package com.gamso.skullkingscore.data.backup

import android.database.SQLException
import androidx.room3.withWriteTransaction
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.Pirate
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.core.WinningCard
import com.gamso.skullkingscore.data.local.BonusEntity
import com.gamso.skullkingscore.data.local.DavyJonesCaptureEntity
import com.gamso.skullkingscore.data.local.GameDao
import com.gamso.skullkingscore.data.local.GameEntity
import com.gamso.skullkingscore.data.local.GamePlayerEntity
import com.gamso.skullkingscore.data.local.LootLinkEntity
import com.gamso.skullkingscore.data.local.PENDING_WINNER
import com.gamso.skullkingscore.data.local.PlayerDao
import com.gamso.skullkingscore.data.local.PlayerEntity
import com.gamso.skullkingscore.data.local.PlayerRoundEntity
import com.gamso.skullkingscore.data.local.RoundEntity
import com.gamso.skullkingscore.data.local.SkullKingDatabase
import com.gamso.skullkingscore.data.local.TrickEntity
import com.gamso.skullkingscore.data.local.encodeCreatures
import com.gamso.skullkingscore.data.local.encodeExpansion
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.repository.BackupRepository
import com.gamso.skullkingscore.domain.repository.ImportOptions
import com.gamso.skullkingscore.domain.repository.ImportPreview
import com.gamso.skullkingscore.domain.repository.ImportResult
import com.gamso.skullkingscore.domain.repository.InvalidBackupException
import com.gamso.skullkingscore.domain.repository.NameConflict
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: SkullKingDatabase,
    private val gameDao: GameDao,
    private val playerDao: PlayerDao,
    private val recalculateScores: RecalculateScoresUseCase,
) : BackupRepository {

    override suspend fun exportJson(games: List<GameDetails>): String = BackupCodec.encode(games)

    /** Games of the file not yet on the device, the only ones imported. */
    private suspend fun newGames(dto: BackupDto): List<GameDto> {
        val knownGames = gameDao.getAllGames().map { it.uuid }.toSet()
        return dto.games.filter { it.uuid !in knownGames }
    }

    /** Players seated in [games]: only they are resolved, so no profile is created without a game. */
    private fun playersOf(dto: BackupDto, games: List<GameDto>): List<PlayerDto> {
        val needed = games.flatMap { it.playerUuids }.toSet()
        return dto.players.filter { it.uuid in needed }
    }

    override suspend fun previewImport(json: String): ImportPreview {
        val dto = BackupCodec.decode(json)
        val newGames = newGames(dto)
        val local = playerDao.getAll()
        val knownPlayers = local.map { it.uuid }.toSet()
        val existingNames = local.map { it.name.lowercase() }.toSet()
        val unknown = playersOf(dto, newGames).filter { it.uuid !in knownPlayers }
        return ImportPreview(
            formatVersion = dto.formatVersion,
            newGames = newGames.size,
            alreadyPresentGames = dto.games.size - newGames.size,
            newPlayers = unknown.size,
            nameConflicts = unknown
                .filter { it.name.lowercase() in existingNames }
                .map { NameConflict(it.uuid, it.name) },
        )
    }

    private class ResolvedPlayers(val ids: Map<String, Long>, val created: Int, val merged: Int)

    /** Maps each player of the file to a profile: the same identifier, a merged namesake, or a new one. */
    private suspend fun resolvePlayers(players: List<PlayerDto>, options: ImportOptions): ResolvedPlayers {
        val ids = mutableMapOf<String, Long>()
        var created = 0
        var merged = 0
        for (p in players) {
            val existing = playerDao.findByUuid(p.uuid)
            val sameName = if (existing == null && p.uuid in options.mergePlayerUuids) {
                playerDao.getAll().firstOrNull { it.name.equals(p.name, ignoreCase = true) }
            } else {
                null
            }
            when {
                existing != null -> ids[p.uuid] = existing.id

                sameName != null -> {
                    ids[p.uuid] = sameName.id
                    merged++
                }

                else -> {
                    ids[p.uuid] = playerDao.insert(
                        PlayerEntity(
                            uuid = p.uuid,
                            name = p.name,
                            color = p.color,
                            createdAt = p.createdAt,
                            archived = p.archived,
                        ),
                    )
                    created++
                }
            }
        }
        return ResolvedPlayers(ids, created, merged)
    }

    override suspend fun importJson(json: String, options: ImportOptions): ImportResult {
        val dto = BackupCodec.decode(json)
        val importedGameIds = mutableListOf<Long>()
        val result: ImportResult = try {
            database.withWriteTransaction {
                // Games already on the device are skipped, and only the players of the others are resolved:
                // no orphan profiles.
                val chosen = newGames(dto)
                val resolved = resolvePlayers(playersOf(dto, chosen), options)
                val playerIds = resolved.ids
                fun playerId(uuid: String): Long =
                    playerIds[uuid] ?: throw InvalidBackupException("Unknown player in the backup: $uuid")

                for (g in chosen) {
                    checkDistinctProfiles(g, ::playerId)
                    val gameId = gameDao.insertGame(
                        GameEntity(
                            uuid = g.uuid,
                            startedAt = g.startedAt,
                            endedAt = g.endedAt,
                            mode = ScoringMode.valueOf(g.mode).name,
                            expansionCards = encodeExpansion(
                                g.expansionCards.map {
                                    ExpansionCard.valueOf(it)
                                }.toSet(),
                            ),
                            status = GameStatus.valueOf(g.status).name,
                            firstDealerSeat = g.firstDealerSeat,
                        ),
                    )
                    gameDao.insertGamePlayers(
                        g.playerUuids.mapIndexed { seat, uuid -> GamePlayerEntity(gameId, playerId(uuid), seat) },
                    )
                    for (r in g.rounds) {
                        val roundId = gameDao.insertRound(
                            RoundEntity(
                                gameId = gameId,
                                number = r.number,
                                cardsDealt = r.cardsDealt,
                                isExtra = r.isExtra,
                                discardedTricks = r.discardedTricks,
                                phase = RoundPhase.valueOf(r.phase).name,
                            ),
                        )
                        gameDao.insertPlayerRounds(
                            r.entries.map { e ->
                                PlayerRoundEntity(
                                    roundId = roundId,
                                    playerId = playerId(e.playerUuid),
                                    bid = e.bid,
                                    finalBid = e.finalBid,
                                    tricks = e.tricks,
                                    cannonball = e.cannonball,
                                    rascalWager = e.rascalWager,
                                    bonus = BonusEntity(
                                        colored14 = e.bonus.colored14,
                                        black14 = e.bonus.black14,
                                        mermaidsByPirate = e.bonus.mermaidsByPirate,
                                        piratesBySkullKing = e.bonus.piratesBySkullKing,
                                        skullKingByMermaid = e.bonus.skullKingByMermaid,
                                        conCaptured = e.bonus.conCaptured,
                                        eights = e.bonus.eights,
                                        sevens = e.bonus.sevens,
                                    ),
                                    score = e.score,
                                )
                            },
                        )
                        r.loots.forEach { l ->
                            gameDao.insertLoot(
                                LootLinkEntity(
                                    roundId = roundId,
                                    lootPlayerId = playerId(l.lootPlayerUuid),
                                    trickWinnerId = l.trickWinnerUuid?.let(::playerId) ?: PENDING_WINNER,
                                    createdAt = l.createdAt,
                                    cancelledAt = l.cancelledAt,
                                    trickNumber = l.trickNumber,
                                ),
                            )
                        }
                        r.tricks.forEach { t ->
                            gameDao.upsertTrick(
                                TrickEntity(
                                    roundId = roundId,
                                    number = t.number,
                                    winnerId = t.winnerUuid?.let(::playerId),
                                    bonus = BonusEntity(
                                        colored14 = t.bonus.colored14,
                                        black14 = t.bonus.black14,
                                        mermaidsByPirate = t.bonus.mermaidsByPirate,
                                        piratesBySkullKing = t.bonus.piratesBySkullKing,
                                        skullKingByMermaid = t.bonus.skullKingByMermaid,
                                        conCaptured = t.bonus.conCaptured,
                                        eights = t.bonus.eights,
                                        sevens = t.bonus.sevens,
                                    ),
                                    createdAt = t.createdAt,
                                    rascalWager = t.rascalWager,
                                    bidAdjustment = t.bidAdjustment,
                                    winningCard = t.winningCard?.let { name -> WinningCard.valueOf(name).name },
                                    winningPirate = t.winningPirate?.let { name -> Pirate.valueOf(name).name },
                                ),
                            )
                        }
                        r.davyJones.forEach { c ->
                            gameDao.insertCapture(
                                DavyJonesCaptureEntity(
                                    roundId = roundId,
                                    playerId = playerId(c.playerUuid),
                                    creatures = encodeCreatures(c.creatures.map { SeaMonster.valueOf(it) }.toSet()),
                                    createdAt = c.createdAt,
                                    cancelledAt = c.cancelledAt,
                                    trickNumber = c.trickNumber,
                                ),
                            )
                        }
                    }
                    importedGameIds += gameId
                }
                ImportResult(
                    importedGames = chosen.size,
                    skippedGames = dto.games.size - chosen.size,
                    createdPlayers = resolved.created,
                    mergedPlayers = resolved.merged,
                )
            }
        } catch (e: IllegalArgumentException) {
            // Unknown enum name: the transaction is rolled back, nothing was imported.
            throw InvalidBackupException("Invalid backup: ${e.message}", e)
        } catch (e: SQLException) {
            // Constraint not caught by the checks above: rolled back as well.
            throw InvalidBackupException("Invalid backup: ${e.message}", e)
        }
        // The cached scores of the file are not trusted: recomputed with the rules of this version.
        importedGameIds.forEach { recalculateScores(it) }
        return result
    }

    /**
     * Two players of the file merged into the same profile cannot sit at the same table, nor have two entries in
     * the same round: the database would refuse the game.
     */
    private fun checkDistinctProfiles(game: GameDto, playerId: (String) -> Long) {
        val seats = game.playerUuids.map(playerId)
        val entries = game.rounds.map { r -> r.entries.map { playerId(it.playerUuid) } }
        if (seats.toSet().size != seats.size || entries.any { it.toSet().size != it.size }) {
            throw InvalidBackupException("Two players of game ${game.uuid} are the same profile")
        }
    }
}
