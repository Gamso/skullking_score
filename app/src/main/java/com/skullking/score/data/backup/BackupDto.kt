package com.skullking.score.data.backup

import kotlinx.serialization.Serializable

/**
 * JSON backup format. References use stable identifiers (uuid) rather than the local database
 * identifiers, so that the history can be restored on another phone.
 */
@Serializable
data class BackupDto(
    val formatVersion: Int,
    val exportedAt: Long,
    val players: List<PlayerDto>,
    val games: List<GameDto>,
)

@Serializable
data class PlayerDto(
    val uuid: String,
    val name: String,
    val color: Long,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Serializable
data class GameDto(
    val uuid: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val mode: String,
    val expansionCards: List<String> = emptyList(),
    val status: String,
    val davyJonesRequiresSuccess: Boolean = true,
    val firstDealerSeat: Int = 0,
    /** Players in table order. */
    val playerUuids: List<String>,
    val rounds: List<RoundDto>,
)

@Serializable
data class RoundDto(
    val number: Int,
    val cardsDealt: Int,
    val isExtra: Boolean = false,
    val discardedTricks: Int = 0,
    val phase: String,
    val entries: List<EntryDto>,
    val loots: List<LootDto> = emptyList(),
    val davyJones: List<DavyJonesDto> = emptyList(),
    val tricks: List<TrickDto> = emptyList(),
)

@Serializable
data class EntryDto(
    val playerUuid: String,
    val bid: Int,
    val finalBid: Int? = null,
    val tricks: Int,
    val cannonball: Boolean = false,
    val rascalWager: Int = 0,
    val bonus: BonusDto = BonusDto(),
    val score: Int = 0,
)

@Serializable
data class BonusDto(
    val colored14: Int = 0,
    val black14: Int = 0,
    val mermaidsByPirate: Int = 0,
    val piratesBySkullKing: Int = 0,
    val skullKingByMermaid: Int = 0,
    val conCaptured: Int = 0,
    val eights: Int = 0,
    val sevens: Int = 0,
)

@Serializable
data class LootDto(
    val lootPlayerUuid: String,
    val trickWinnerUuid: String? = null,
    val createdAt: Long,
    val cancelledAt: Long? = null,
    val trickNumber: Int? = null,
)

@Serializable
data class DavyJonesDto(
    val playerUuid: String,
    val creatures: List<String>,
    val createdAt: Long,
    val cancelledAt: Long? = null,
    val trickNumber: Int? = null,
)

@Serializable
data class TrickDto(
    val number: Int,
    /** `null`: trick destroyed (Kraken). */
    val winnerUuid: String? = null,
    val bonus: BonusDto = BonusDto(),
    val createdAt: Long = 0,
    val rascalWager: Int = 0,
    val bidAdjustment: Int = 0,
    val winningCard: String? = null,
    val winningPirate: String? = null,
)
