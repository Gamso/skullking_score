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
    /** Nom de [com.skullking.score.core.ScoringMode]. */
    val mode: String,
    /** Noms de [com.skullking.score.core.ExpansionCard], séparés par des virgules. */
    val expansionCards: String,
    /** Nom de [com.skullking.score.domain.model.GameStatus]. */
    val status: String,
    val davyJonesRequiresSuccess: Boolean,
    /** Place (0 = premier joueur de la table) du donneur de la première manche. */
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
    /** Place autour de la table. */
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
    /** Nom de [com.skullking.score.domain.model.RoundPhase]. */
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
    /** Cache du score calculé. */
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
    /** 0 tant que le pli n'est pas terminé : le gagnant sera renseigné à la saisie du pli. */
    val trickWinnerId: Long,
    val createdAt: Long,
    val cancelledAt: Long?,
    /** Pli pendant lequel l'événement a eu lieu (saisie en direct) ; `null` si inconnu. */
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
    /** Noms de [com.skullking.score.core.SeaMonster], séparés par des virgules. */
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
    /** `null` : pli détruit (Kraken), personne ne le gagne. */
    val winnerId: Long?,
    /** Cartes capturées dans ce pli par son gagnant. */
    @Embedded(prefix = "bonus_") val bonus: BonusEntity,
    val createdAt: Long,
    /** Pari de Rascal le Flambeur (0, 10 ou 20) fait par le gagnant de ce pli. */
    val rascalWager: Int = 0,
    /** Annonce du gagnant ajustée par Harry le Géant joué dans ce pli : -1, 0 ou +1. */
    val bidAdjustment: Int = 0,
    /** Nom de [com.skullking.score.core.WinningCard], facultatif. */
    val winningCard: String? = null,
    /** Nom de [com.skullking.score.core.Pirate], facultatif. */
    val winningPirate: String? = null,
)

/** Ligne du total par partie et par joueur, calculée sur les scores en cache des manches validées. */
data class TotalRow(val gameId: Long, val playerId: Long, val total: Int)
