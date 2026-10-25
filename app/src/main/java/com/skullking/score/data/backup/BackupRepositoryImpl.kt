package com.skullking.score.data.backup

import androidx.room3.withWriteTransaction
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard
import com.skullking.score.data.local.BonusEntity
import com.skullking.score.data.local.DavyJonesCaptureEntity
import com.skullking.score.data.local.GameDao
import com.skullking.score.data.local.GameEntity
import com.skullking.score.data.local.GamePlayerEntity
import com.skullking.score.data.local.LootLinkEntity
import com.skullking.score.data.local.PENDING_WINNER
import com.skullking.score.data.local.PlayerDao
import com.skullking.score.data.local.PlayerEntity
import com.skullking.score.data.local.PlayerRoundEntity
import com.skullking.score.data.local.RoundEntity
import com.skullking.score.data.local.SkullKingDatabase
import com.skullking.score.data.local.TrickEntity
import com.skullking.score.data.local.encodeCreatures
import com.skullking.score.data.local.encodeExpansion
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.repository.BackupRepository
import com.skullking.score.domain.repository.ImportOptions
import com.skullking.score.domain.repository.ImportPreview
import com.skullking.score.domain.repository.ImportResult
import com.skullking.score.domain.repository.InvalidBackupException
import com.skullking.score.domain.repository.NameConflict
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: SkullKingDatabase,
    private val gameDao: GameDao,
    private val playerDao: PlayerDao,
) : BackupRepository {

    override suspend fun exportJson(games: List<GameDetails>): String = BackupCodec.encode(games)

    override suspend fun previewImport(json: String): ImportPreview {
        val dto = BackupCodec.decode(json)
        val knownGames = gameDao.getAllGames().map { it.uuid }.toSet()
        val knownPlayers = playerDao.getAll().map { it.uuid }.toSet()
        val newGames = dto.games.count { it.uuid !in knownGames }
        val namesByUuid = dto.players.associate { it.uuid to it.name }
        val existingNames = playerDao.getAll().map { it.name.lowercase() }.toSet()
        return ImportPreview(
            formatVersion = dto.formatVersion,
            newGames = newGames,
            alreadyPresentGames = dto.games.size - newGames,
            newPlayers = dto.players.count { it.uuid !in knownPlayers },
            nameConflicts = dto.players
                .filter { it.uuid !in knownPlayers && it.name.lowercase() in existingNames }
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
        val chosen = dto.games
        // Only players who played a game in the file are created: no orphan profiles.
        val neededPlayers = chosen.flatMap { it.playerUuids }.toSet()
        try {
            return database.withWriteTransaction {
                val resolved = resolvePlayers(dto.players.filter { it.uuid in neededPlayers }, options)
                val playerIds = resolved.ids
                val createdPlayers = resolved.created
                val mergedPlayers = resolved.merged
                fun playerId(uuid: String): Long =
                    playerIds[uuid] ?: throw InvalidBackupException("Unknown player in the backup: $uuid")

                var imported = 0
                var skipped = 0
                for (g in chosen) {
                    if (gameDao.findGameByUuid(g.uuid) != null) {
                        skipped++
                        continue
                    }
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
                            davyJonesRequiresSuccess = g.davyJonesRequiresSuccess,
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
                    imported++
                }
                ImportResult(
                    importedGames = imported,
                    skippedGames = skipped,
                    createdPlayers = createdPlayers,
                    mergedPlayers = mergedPlayers,
                )
            }
        } catch (e: IllegalArgumentException) {
            // Unknown enum name: the transaction is rolled back, nothing was imported.
            throw InvalidBackupException("Invalid backup: ${e.message}", e)
        }
    }
}
