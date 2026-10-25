package com.skullking.score.domain.scoring

import com.skullking.score.core.Bonus
import com.skullking.score.domain.model.Trick

data class PlayerTrickTotals(val tricks: Int, val bonus: Bonus, val rascalWager: Int = 0, val bidAdjustment: Int = 0)

data class TrickTotals(
    val perPlayer: Map<Long, PlayerTrickTotals>,
    /** Plis détruits (Kraken) : personne ne les gagne. */
    val destroyed: Int,
)

/** Déduit des plis saisis en direct les plis remportés, les cartes capturées et les plis détruits. */
object TrickAggregator {
    fun aggregate(tricks: List<Trick>): TrickTotals {
        val won = tricks.filter { it.winnerId != null }.groupBy { it.winnerId!! }
        val perPlayer = won.mapValues { (_, list) ->
            PlayerTrickTotals(
                tricks = list.size,
                bonus = list.fold(Bonus.NONE) { acc, trick -> acc + trick.bonus },
                rascalWager = list.sumOf { it.rascalWager },
                bidAdjustment = list.sumOf { it.bidAdjustment },
            )
        }
        return TrickTotals(perPlayer, destroyed = tricks.count { it.winnerId == null })
    }
}
