package com.skullking.score.core

/** Les trois modes de calcul officiels, choisis à la création de la partie. */
enum class ScoringMode { CLASSIC, RASCAL, CANNONBALL }

/** Monstres marins pouvant être détruits par le Coffre de Davy Jones. */
enum class SeaMonster { KRAKEN, WHITE_WHALE, SPOTTED_RAY }

/**
 * Cartes de l'extension 2026, activables une à une.
 *
 * @property deckCount nombre d'exemplaires ajoutés au paquet
 * @property removedAtTwoPlayers retirée automatiquement à 2 joueurs
 */
enum class ExpansionCard(val deckCount: Int, val removedAtTwoPlayers: Boolean = false) {
    SEVENS(4),
    EIGHTS(4),
    ZERO_FOURTEEN(4, removedAtTwoPlayers = true),
    WILD_15(1, removedAtTwoPlayers = true),
    MARY_THORNE(1),
    FIRST_MATE_CON(1),
    DAVY_JONES_CHEST(1),
    PLANK(1, removedAtTwoPlayers = true),
    SPOTTED_RAY(1),
    LAST_SALVO(1),
    ;

    fun availableFor(playerCount: Int): Boolean = !(removedAtTwoPlayers && playerCount <= 2)
}

/**
 * Carte avec laquelle un pli a été gagné (information facultative, utilisée par les statistiques).
 * [OTHER] : carte de couleur, atout noir ou fuite, sans plus de détail.
 */
enum class WinningCard { MERMAID, SKULL_KING, FIRST_MATE, PIRATE, OTHER }

/** Pirate gagnant un pli, quand la carte gagnante est un pirate. */
enum class Pirate(val requires: ExpansionCard? = null) {
    ROSIE,
    RASCAL,
    WILL,
    JUANITA,
    HARRY,
    TIGRESS,
    MARY_THORNE(ExpansionCard.MARY_THORNE),
}

/** Compteurs de cartes capturées saisis à la main (les Butins et Davy Jones sont dérivés d'événements). */
data class Bonus(
    val colored14: Int = 0,
    val black14: Int = 0,
    val mermaidsByPirate: Int = 0,
    val piratesBySkullKing: Int = 0,
    val skullKingByMermaid: Int = 0,
    val conCaptured: Int = 0,
    val eights: Int = 0,
    val sevens: Int = 0,
) {
    operator fun plus(other: Bonus) = Bonus(
        colored14 = colored14 + other.colored14,
        black14 = black14 + other.black14,
        mermaidsByPirate = mermaidsByPirate + other.mermaidsByPirate,
        piratesBySkullKing = piratesBySkullKing + other.piratesBySkullKing,
        skullKingByMermaid = skullKingByMermaid + other.skullKingByMermaid,
        conCaptured = conCaptured + other.conCaptured,
        eights = eights + other.eights,
        sevens = sevens + other.sevens,
    )

    companion object {
        val NONE = Bonus()
    }
}

enum class BonusType {
    COLORED_14,
    BLACK_14,
    MERMAID_BY_PIRATE,
    PIRATE_BY_SKULL_KING,
    SKULL_KING_BY_MERMAID,
    CON_CAPTURED,
    LOOT,
    DAVY_JONES,
    EIGHT,
    SEVEN,
}

/**
 * Table des valeurs de points. Tout est ici plutôt qu'en dur dans le calcul, pour suivre un
 * éventuel erratum sans réécrire l'algorithme.
 */
data class RuleSet(
    val exactPerTrick: Int = 20,
    val zeroBidPerCard: Int = 10,
    val missPerTrick: Int = 10,
    val rascalExactPerCard: Int = 10,
    val rascalOffByOnePerCard: Int = 5,
    val rascalOffByOneBonusPercent: Int = 50,
    val cannonballPerCard: Int = 15,
    val bonusValues: Map<BonusType, Int> = DEFAULT_BONUS_VALUES,
) {
    fun valueOf(type: BonusType): Int = bonusValues.getValue(type)

    companion object {
        val DEFAULT_BONUS_VALUES: Map<BonusType, Int> = mapOf(
            BonusType.COLORED_14 to 10,
            BonusType.BLACK_14 to 20,
            BonusType.MERMAID_BY_PIRATE to 20,
            BonusType.PIRATE_BY_SKULL_KING to 30,
            BonusType.SKULL_KING_BY_MERMAID to 40,
            BonusType.CON_CAPTURED to 30,
            BonusType.LOOT to 20,
            BonusType.DAVY_JONES to 20,
            BonusType.EIGHT to 5,
            BonusType.SEVEN to -5,
        )
    }
}

/** Constantes de paquet utilisées pour proposer le nombre de cartes et contrôler la cohérence. */
object DeckRules {
    const val BASE_DECK_SIZE = 74
    const val MAX_CARDS_PER_ROUND = 10
    const val PLANNED_ROUNDS = 10
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS_BASE = 8
    const val MAX_PLAYERS_EXPANSION = 9

    fun deckSize(expansion: Set<ExpansionCard>): Int = BASE_DECK_SIZE + expansion.sumOf { it.deckCount }

    /** Plus grand nombre de cartes distribuables à chaque joueur (1 à 10). */
    fun maxCards(playerCount: Int, expansion: Set<ExpansionCard>): Int =
        (deckSize(expansion) / playerCount.coerceAtLeast(1)).coerceIn(1, MAX_CARDS_PER_ROUND)

    /**
     * Nombre de cartes proposé pour la manche [roundNumber] : manche précédente + 1, plafonné.
     * Au-delà des manches prévues, on propose 10 cartes (modifiable).
     */
    fun suggestedCards(roundNumber: Int, previousCards: Int?, playerCount: Int, expansion: Set<ExpansionCard>): Int {
        val cap = maxCards(playerCount, expansion)
        return if (roundNumber > PLANNED_ROUNDS) {
            MAX_CARDS_PER_ROUND.coerceAtMost(cap)
        } else {
            ((previousCards ?: 0) + 1).coerceIn(1, cap)
        }
    }

    /** Pirates pouvant exister : 5 + Tigresse + Mary Thorne (extension). */
    fun maxPirates(expansion: Set<ExpansionCard>): Int = 5 + 1 + if (ExpansionCard.MARY_THORNE in expansion) 1 else 0

    const val MAX_MERMAIDS = 2
    const val MAX_COLORED_14 = 3
    const val MAX_BLACK_14 = 1
    const val MAX_SKULL_KING = 1
    const val MAX_CON = 1
    const val MAX_SEVENS = 4
    const val MAX_EIGHTS = 4
    const val MAX_LOOT = 2
}
