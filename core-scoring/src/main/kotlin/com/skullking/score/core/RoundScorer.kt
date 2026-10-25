package com.skullking.score.core

import kotlin.math.abs

/** Données d'un joueur pour une manche, telles que le calcul les consomme. */
data class PlayerRoundInput(
    val playerId: Long,
    /** Annonce finale, après correction éventuelle (Harry le Géant). */
    val finalBid: Int,
    val tricks: Int,
    val bonus: Bonus = Bonus.NONE,
    /** Pari de Rascal de Roatan : 0, 10 ou 20. */
    val rascalWager: Int = 0,
    /** Mode Boulet de canon : le joueur mise « tout ou rien » sur son annonce. */
    val cannonball: Boolean = false,
    /** Nombre de monstres marins détruits par le Coffre de Davy Jones de ce joueur (dérivé des événements). */
    val davyJonesDestroyed: Int = 0,
) {
    val isExact: Boolean get() = tricks == finalBid
}

/** Un Butin : [lootPlayerId] a joué la carte, [trickWinnerId] a gagné le pli. */
data class LootPair(val lootPlayerId: Long, val trickWinnerId: Long)

data class BonusLine(val type: BonusType, val count: Int, val points: Int)

/** Détail du calcul d'un joueur pour une manche (affiché au toucher dans le tableau des scores). */
data class ScoreBreakdown(
    val playerId: Long,
    val success: Boolean,
    val base: Int,
    /** Toutes les lignes de bonus saisies, même si elles ne comptent pas. */
    val bonusLines: List<BonusLine>,
    /** Bonus effectivement ajoutés au score. */
    val bonusApplied: Int,
    /** Gain (+) ou perte (-) du pari de Rascal de Roatan. */
    val rascalPoints: Int,
    val total: Int,
) {
    val bonusEntered: Int get() = bonusLines.sumOf { it.points }

    /** Vrai si des bonus ont été saisis mais ne comptent pas (annonce ratée). */
    val bonusDiscarded: Boolean get() = bonusLines.any { it.count != 0 } && bonusApplied != bonusEntered
}

object RoundScorer {
    private const val PERCENT = 100

    /**
     * Calcule les scores de tous les joueurs d'une manche. Le résultat ne dépend pas de l'ordre
     * des joueurs ni de celui des Butins.
     *
     * @param davyJonesRequiresSuccess point ouvert de la règle : le +20 du Coffre n'est compté
     * que si l'annonce est réussie (défaut), ou toujours si `false`.
     */
    fun scoreRound(
        mode: ScoringMode,
        cardsDealt: Int,
        inputs: List<PlayerRoundInput>,
        loots: List<LootPair> = emptyList(),
        ruleSet: RuleSet = RuleSet(),
        davyJonesRequiresSuccess: Boolean = true,
    ): Map<Long, ScoreBreakdown> {
        val byId = inputs.associateBy { it.playerId }
        // Un Butin n'est valide que si les deux joueurs liés réussissent leur annonce (exactement).
        val validLoots = loots.filter { loot ->
            byId[loot.lootPlayerId]?.isExact == true && byId[loot.trickWinnerId]?.isExact == true
        }
        return inputs.associate { input ->
            val lootCount = validLoots.count {
                it.lootPlayerId == input.playerId || it.trickWinnerId == input.playerId
            }
            input.playerId to scorePlayer(mode, cardsDealt, input, lootCount, ruleSet, davyJonesRequiresSuccess)
        }
    }

    fun scorePlayer(
        mode: ScoringMode,
        cardsDealt: Int,
        input: PlayerRoundInput,
        lootCount: Int,
        ruleSet: RuleSet = RuleSet(),
        davyJonesRequiresSuccess: Boolean = true,
    ): ScoreBreakdown {
        val n = cardsDealt
        val bid = input.finalBid
        val diff = abs(input.tricks - bid)

        val lines = bonusLines(input, lootCount, ruleSet)
        val bonusFull = lines.sumOf { it.points }
        val lootPoints = lines.filter { it.type == BonusType.LOOT }.sumOf { it.points }
        val davyJonesPoints = lines.filter { it.type == BonusType.DAVY_JONES }.sumOf { it.points }
        // Option : le Coffre de Davy Jones rapporte même sur une annonce ratée.
        val salvaged = if (davyJonesRequiresSuccess) 0 else davyJonesPoints

        fun rascalBase(): Pair<Int, Int> = when (diff) {
            0 -> ruleSet.rascalExactPerCard * n to bonusFull

            // Écart de 1 : 50 % des bonus, Butins exclus (ils exigent une annonce exacte).
            1 ->
                ruleSet.rascalOffByOnePerCard * n to
                    (bonusFull - lootPoints) * ruleSet.rascalOffByOneBonusPercent / PERCENT

            else -> 0 to salvaged
        }

        val (base, applied) = when (mode) {
            ScoringMode.CLASSIC -> when {
                bid == 0 && diff == 0 -> ruleSet.zeroBidPerCard * n to bonusFull
                bid == 0 -> -ruleSet.zeroBidPerCard * n to salvaged
                diff == 0 -> ruleSet.exactPerTrick * bid to bonusFull
                else -> -ruleSet.missPerTrick * diff to salvaged
            }

            ScoringMode.RASCAL -> rascalBase()

            ScoringMode.CANNONBALL ->
                if (!input.cannonball) {
                    rascalBase()
                } else if (diff == 0) {
                    ruleSet.cannonballPerCard * n to bonusFull
                } else {
                    0 to salvaged
                }
        }

        // Le pari de Rascal est le seul terme appliqué même quand l'annonce est ratée.
        val rascal = when {
            input.rascalWager == 0 -> 0
            diff == 0 -> input.rascalWager
            else -> -input.rascalWager
        }

        return ScoreBreakdown(
            playerId = input.playerId,
            success = diff == 0,
            base = base,
            bonusLines = lines,
            bonusApplied = applied,
            rascalPoints = rascal,
            total = base + applied + rascal,
        )
    }

    private fun bonusLines(input: PlayerRoundInput, lootCount: Int, rules: RuleSet): List<BonusLine> {
        val b = input.bonus
        val raw = listOf(
            BonusType.COLORED_14 to b.colored14,
            BonusType.BLACK_14 to b.black14,
            BonusType.MERMAID_BY_PIRATE to b.mermaidsByPirate,
            BonusType.PIRATE_BY_SKULL_KING to b.piratesBySkullKing,
            BonusType.SKULL_KING_BY_MERMAID to b.skullKingByMermaid,
            BonusType.CON_CAPTURED to b.conCaptured,
            BonusType.LOOT to lootCount,
            BonusType.DAVY_JONES to input.davyJonesDestroyed,
            BonusType.EIGHT to b.eights,
            BonusType.SEVEN to b.sevens,
        )
        return raw.filter { it.second != 0 }
            .map { (type, count) -> BonusLine(type, count, count * rules.valueOf(type)) }
    }
}
