package com.skullking.score.data.backup

import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.repository.InvalidBackupException
import com.skullking.score.domain.repository.NewerBackupException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Encoding and decoding of the JSON backup format, independent of the database (so usable from tests). */
object BackupCodec {
    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(games: List<GameDetails>, exportedAt: Long = System.currentTimeMillis()): String {
        val players = games.flatMap { it.game.players }.distinctBy { it.uuid }
        val dto = BackupDto(
            formatVersion = FORMAT_VERSION,
            exportedAt = exportedAt,
            players = players.map { PlayerDto(it.uuid, it.name, it.color, it.createdAt, it.archived) },
            games = games.map { it.toDto() },
        )
        return json.encodeToString(BackupDto.serializer(), dto)
    }

    fun decode(text: String): BackupDto {
        val dto = try {
            json.decodeFromString(BackupDto.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Unreadable file", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Unreadable file", e)
        }
        checkSupported(dto.formatVersion)
        return dto
    }

    private fun checkSupported(version: Int) {
        if (version > FORMAT_VERSION) throw NewerBackupException(version)
        if (version < 1) throw InvalidBackupException("Unsupported format version: $version")
    }

    private fun GameDetails.toDto(): GameDto {
        val uuidById = game.players.associate { it.id to it.uuid }
        fun uuid(id: Long) = uuidById[id] ?: throw IllegalStateException("Player $id is not in the game")
        return GameDto(
            uuid = game.uuid,
            startedAt = game.startedAt,
            endedAt = game.endedAt,
            mode = game.mode.name,
            expansionCards = game.expansionCards.sortedBy { it.ordinal }.map { it.name },
            status = game.status.name,
            davyJonesRequiresSuccess = game.davyJonesRequiresSuccess,
            firstDealerSeat = game.firstDealerSeat,
            playerUuids = game.players.map { it.uuid },
            rounds = rounds.map { r ->
                RoundDto(
                    number = r.round.number,
                    cardsDealt = r.round.cardsDealt,
                    isExtra = r.round.isExtra,
                    discardedTricks = r.round.discardedTricks,
                    phase = r.round.phase.name,
                    entries = r.entries.map { e ->
                        EntryDto(
                            playerUuid = uuid(e.playerId),
                            bid = e.bid,
                            finalBid = e.finalBid,
                            tricks = e.tricks,
                            cannonball = e.cannonball,
                            rascalWager = e.rascalWager,
                            bonus = BonusDto(
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
                    loots = r.loots.map {
                        LootDto(
                            lootPlayerUuid = uuid(it.lootPlayerId),
                            trickWinnerUuid = it.trickWinnerId?.let(::uuid),
                            createdAt = it.createdAt,
                            cancelledAt = it.cancelledAt,
                            trickNumber = it.trickNumber,
                        )
                    },
                    davyJones = r.captures.map {
                        DavyJonesDto(
                            playerUuid = uuid(it.playerId),
                            creatures = it.creatures.map { c -> c.name },
                            createdAt = it.createdAt,
                            cancelledAt = it.cancelledAt,
                            trickNumber = it.trickNumber,
                        )
                    },
                    tricks = r.tricks.map {
                        TrickDto(
                            number = it.number,
                            winnerUuid = it.winnerId?.let(::uuid),
                            bonus = BonusDto(
                                colored14 = it.bonus.colored14,
                                black14 = it.bonus.black14,
                                mermaidsByPirate = it.bonus.mermaidsByPirate,
                                piratesBySkullKing = it.bonus.piratesBySkullKing,
                                skullKingByMermaid = it.bonus.skullKingByMermaid,
                                conCaptured = it.bonus.conCaptured,
                                eights = it.bonus.eights,
                                sevens = it.bonus.sevens,
                            ),
                            createdAt = it.createdAt,
                            rascalWager = it.rascalWager,
                            bidAdjustment = it.bidAdjustment,
                            winningCard = it.winningCard?.name,
                            winningPirate = it.winningPirate?.name,
                        )
                    },
                )
            },
        )
    }
}
