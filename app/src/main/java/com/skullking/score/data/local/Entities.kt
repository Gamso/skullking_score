package com.skullking.score.data.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "players", indices = [Index(value = ["uuid"], unique = true)])
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val color: Long,
    val createdAt: Long,
    val archived: Boolean,
)

@Entity(tableName = "games", indices = [Index(value = ["uuid"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** Name of [com.skullking.score.core.ScoringMode]. */
    val mode: String,
    /** Names of [com.skullking.score.core.ExpansionCard], comma-separated. */
    val expansionCards: String,
    /** Name of [com.skullking.score.domain.model.GameStatus]. */
    val status: String,
    val davyJonesRequiresSuccess: Boolean,
    /** Seat (0 = first player at the table) of the dealer of the first round. */
    val firstDealerSeat: Int = 0,
)

@Entity(
    tableName = "game_players",
    primaryKeys = ["gameId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
        ),
    ],
    indices = [Index("playerId")],
)
data class GamePlayerEntity(
    val gameId: Long,
    val playerId: Long,
    /** Seat around the table. */
    val seat: Int,
)

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gameId", "number"], unique = true)],
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val number: Int,
    val cardsDealt: Int,
    val isExtra: Boolean,
    val discardedTricks: Int,
    /** Name of [com.skullking.score.domain.model.RoundPhase]. */
    val phase: String,
)

data class BonusEntity(
    val colored14: Int,
    val black14: Int,
    val mermaidsByPirate: Int,
    val piratesBySkullKing: Int,
    val skullKingByMermaid: Int,
    val conCaptured: Int,
    val eights: Int,
    val sevens: Int,
)

@Entity(
    tableName = "player_rounds",
    primaryKeys = ["roundId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
        ),
    ],
    indices = [Index("playerId")],
)
data class PlayerRoundEntity(
    val roundId: Long,
    val playerId: Long,
    val bid: Int,
    val finalBid: Int?,
    val tricks: Int,
    val cannonball: Boolean,
    val rascalWager: Int,
    @Embedded(prefix = "bonus_") val bonus: BonusEntity,
    /** Cached computed score. */
    val score: Int,
)

@Entity(
    tableName = "loot_links",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roundId")],
)
data class LootLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val lootPlayerId: Long,
    /** 0 until the trick is finished: the winner is filled in when the trick is entered. */
    val trickWinnerId: Long,
    val createdAt: Long,
    val cancelledAt: Long?,
    /** Trick during which the event happened (live entry); `null` if unknown. */
    val trickNumber: Int?,
)

@Entity(
    tableName = "davy_jones_captures",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roundId")],
)
data class DavyJonesCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val playerId: Long,
    /** Names of [com.skullking.score.core.SeaMonster], comma-separated. */
    val creatures: String,
    val createdAt: Long,
    val cancelledAt: Long?,
    val trickNumber: Int?,
)

@Entity(
    tableName = "tricks",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["roundId", "number"], unique = true)],
)
data class TrickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val number: Int,
    /** `null`: trick destroyed (Kraken), nobody wins it. */
    val winnerId: Long?,
    /** Cards captured in this trick by its winner. */
    @Embedded(prefix = "bonus_") val bonus: BonusEntity,
    val createdAt: Long,
    /** Rascal wager (0, 10 or 20) made by the winner of this trick. */
    val rascalWager: Int = 0,
    /** Winner's bid adjusted by Harry the Giant played in this trick: -1, 0 or +1. */
    val bidAdjustment: Int = 0,
    /** Name of [com.skullking.score.core.WinningCard], optional. */
    val winningCard: String? = null,
    /** Name of [com.skullking.score.core.Pirate], optional. */
    val winningPirate: String? = null,
)

/** Per-game, per-player total row, computed from the cached scores of the validated rounds. */
data class TotalRow(val gameId: Long, val playerId: Long, val total: Int)
