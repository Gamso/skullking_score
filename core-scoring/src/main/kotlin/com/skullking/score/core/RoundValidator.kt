package com.skullking.score.core

enum class WarningCode {
    /** Somme des plis (+ plis défaussés) supérieure au nombre de cartes. */
    TRICKS_OVER,

    /** Somme des plis inférieure au nombre de cartes : il faut préciser les plis défaussés. */
    TRICKS_UNDER,
    CARDS_EXCEED_DECK,
    TOO_MANY_COLORED_14,
    TOO_MANY_BLACK_14,
    TOO_MANY_SKULL_KING,
    TOO_MANY_CON,
    TOO_MANY_SEVENS,
    TOO_MANY_EIGHTS,
    TOO_MANY_MERMAIDS,
    TOO_MANY_PIRATES,
    TOO_MANY_LOOT,
    DUPLICATE_MONSTER,
}

/** Alerte non bloquante : la table a toujours raison, l'app prévient puis laisse valider. */
data class RoundWarning(val code: WarningCode, val args: List<Int> = emptyList())

object RoundValidator {

    /**
     * @param lootCount nombre de Butins saisis dans la manche
     * @param monsters monstres marins saisis via les Coffres de Davy Jones (avec doublons éventuels)
     */
    fun validate(
        cardsDealt: Int,
        playerCount: Int,
        discardedTricks: Int,
        inputs: List<PlayerRoundInput>,
        lootCount: Int,
        monsters: List<SeaMonster>,
        expansion: Set<ExpansionCard>,
    ): List<RoundWarning> {
        val warnings = mutableListOf<RoundWarning>()

        val deck = DeckRules.deckSize(expansion)
        if (cardsDealt * playerCount > deck) {
            warnings += RoundWarning(WarningCode.CARDS_EXCEED_DECK, listOf(cardsDealt * playerCount, deck))
        }

        val tricks = inputs.sumOf { it.tricks } + discardedTricks
        if (tricks > cardsDealt) {
            warnings += RoundWarning(WarningCode.TRICKS_OVER, listOf(tricks, cardsDealt))
        } else if (tricks < cardsDealt) {
            warnings += RoundWarning(WarningCode.TRICKS_UNDER, listOf(tricks, cardsDealt))
        }

        fun check(total: Int, max: Int, code: WarningCode) {
            if (total > max) warnings += RoundWarning(code, listOf(total, max))
        }

        val bonuses = inputs.map { it.bonus }
        check(bonuses.sumOf { it.colored14 }, DeckRules.MAX_COLORED_14, WarningCode.TOO_MANY_COLORED_14)
        check(bonuses.sumOf { it.black14 }, DeckRules.MAX_BLACK_14, WarningCode.TOO_MANY_BLACK_14)
        check(bonuses.sumOf { it.skullKingByMermaid }, DeckRules.MAX_SKULL_KING, WarningCode.TOO_MANY_SKULL_KING)
        check(bonuses.sumOf { it.conCaptured }, DeckRules.MAX_CON, WarningCode.TOO_MANY_CON)
        check(bonuses.sumOf { it.sevens }, DeckRules.MAX_SEVENS, WarningCode.TOO_MANY_SEVENS)
        check(bonuses.sumOf { it.eights }, DeckRules.MAX_EIGHTS, WarningCode.TOO_MANY_EIGHTS)
        check(bonuses.sumOf { it.mermaidsByPirate }, DeckRules.MAX_MERMAIDS, WarningCode.TOO_MANY_MERMAIDS)
        check(bonuses.sumOf { it.piratesBySkullKing }, DeckRules.maxPirates(expansion), WarningCode.TOO_MANY_PIRATES)
        check(lootCount, DeckRules.MAX_LOOT, WarningCode.TOO_MANY_LOOT)

        val duplicated = monsters.groupingBy { it }.eachCount().filterValues { it > 1 }
        if (duplicated.isNotEmpty()) {
            warnings += RoundWarning(WarningCode.DUPLICATE_MONSTER, duplicated.keys.map { it.ordinal })
        }
        return warnings
    }
}
