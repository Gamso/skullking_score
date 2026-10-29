package com.skullking.score.domain.model

import com.skullking.score.core.ScoreBreakdown
import com.skullking.score.core.ScoringMode

data class ScoredRound(
    val details: RoundDetails,
    /** Calculation detail per player (computed even for a round in progress, for the live preview). */
    val breakdowns: Map<Long, ScoreBreakdown>,
    /** Cumulative total after this round; unchanged for an unfinished round. */
    val cumulative: Map<Long, Double>,
) {
    val round: Round get() = details.round
    val isScored: Boolean get() = round.phase == RoundPhase.SCORED
}

data class RankedPlayer(val player: Player, val total: Double, val rank: Int)

data class ScoredGame(
    val details: GameDetails,
    val mode: ScoringMode,
    val rounds: List<ScoredRound>,
    val totals: Map<Long, Double>,
) {
    val game: Game get() = details.game

    /** Ranking by descending total; ties share the same rank. */
    val ranking: List<RankedPlayer>
        get() {
            val sorted = game.players.sortedByDescending { totals[it.id] ?: 0.0 }
            var rank = 0
            var previous: Double? = null
            return sorted.mapIndexed { index, player ->
                val total = totals[player.id] ?: 0.0
                if (total != previous) rank = index + 1
                previous = total
                RankedPlayer(player, total, rank)
            }
        }

    fun round(roundId: Long): ScoredRound? = rounds.firstOrNull { it.round.id == roundId }
}
