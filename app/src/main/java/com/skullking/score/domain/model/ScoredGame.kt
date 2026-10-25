package com.skullking.score.domain.model

import com.skullking.score.core.ScoreBreakdown
import com.skullking.score.core.ScoringMode

data class ScoredRound(
    val details: RoundDetails,
    /** Détail du calcul par joueur (calculé même pour une manche en cours, pour l'aperçu en direct). */
    val breakdowns: Map<Long, ScoreBreakdown>,
    /** Cumul après cette manche ; inchangé pour une manche non terminée. */
    val cumulative: Map<Long, Int>,
) {
    val round: Round get() = details.round
    val isScored: Boolean get() = round.phase == RoundPhase.SCORED
}

data class RankedPlayer(val player: Player, val total: Int, val rank: Int)

data class ScoredGame(
    val details: GameDetails,
    val mode: ScoringMode,
    val rounds: List<ScoredRound>,
    val totals: Map<Long, Int>,
) {
    val game: Game get() = details.game

    /** Classement par total décroissant ; les ex æquo partagent le même rang. */
    val ranking: List<RankedPlayer>
        get() {
            val sorted = game.players.sortedByDescending { totals[it.id] ?: 0 }
            var rank = 0
            var previous: Int? = null
            return sorted.mapIndexed { index, player ->
                val total = totals[player.id] ?: 0
                if (total != previous) rank = index + 1
                previous = total
                RankedPlayer(player, total, rank)
            }
        }

    fun round(roundId: Long): ScoredRound? = rounds.firstOrNull { it.round.id == roundId }
}
