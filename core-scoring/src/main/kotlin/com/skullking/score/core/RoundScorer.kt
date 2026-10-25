package com.skullking.score.core

import kotlin.math.abs

/** Data of a player for a round, as the calculation consumes it. */
data class PlayerRoundInput(
    val playerId: Long,
    /** Final bid, after a possible correction (Harry the Giant). */
    val finalBid: Int,
    val tricks: Int,
    val bonus: Bonus = Bonus.NONE,
    /** Rascal of Roatan wager: 0, 10 or 20. */
    val rascalWager: Int = 0,
    /** Cannonball mode: the player wagers "all or nothing" on their bid. */
    val cannonball: Boolean = false,
    /** Number of sea monsters destroyed by this player's Davy Jones' Locker (derived from events). */
    val davyJonesDestroyed: Int = 0,
) {
    val isExact: Boolean get() = tricks == finalBid
}

/** A Loot: [lootPlayerId] played the card, [trickWinnerId] won the trick. */
data class LootPair(val lootPlayerId: Long, val trickWinnerId: Long)

data class BonusLine(val type: BonusType, val count: Int, val points: Int)

/** Calculation detail of a player for a round (shown when tapping the scoreboard). */
data class ScoreBreakdown(
    val playerId: Long,
    val success: Boolean,
    val base: Int,
    /** All bonus lines entered, even if they do not count. */
    val bonusLines: List<BonusLine>,
    /** Bonus actually added to the score. */
    val bonusApplied: Int,
    /** Gain (+) or loss (-) of the Rascal of Roatan wager. */
    val rascalPoints: Int,
    val total: Int,
) {
    val bonusEntered: Int get() = bonusLines.sumOf { it.points }

    /** True if bonuses were entered but do not count (missed bid). */
    val bonusDiscarded: Boolean get() = bonusLines.any { it.count != 0 } && bonusApplied != bonusEntered
}

object RoundScorer {
    private const val PERCENT = 100

    /**
     * Computes the scores of all players of a round. The result does not depend on the order
     * of the players nor of the Loots.
     *
     * @param davyJonesRequiresSuccess open rule point: the +20 of the Locker only counts
     * if the bid is successful (default), or always if `false`.
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
        // A Loot is only valid if both linked players make their bid (exactly).
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
        // Option: Davy Jones' Locker pays even on a missed bid.
        val salvaged = if (davyJonesRequiresSuccess) 0 else davyJonesPoints

        fun rascalBase(): Pair<Int, Int> = when (diff) {
            0 -> ruleSet.rascalExactPerCard * n to bonusFull

            // Gap of 1: 50% of the bonuses, Loots excluded (they require an exact bid).
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

        // The Rascal wager is the only term applied even when the bid is missed.
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
