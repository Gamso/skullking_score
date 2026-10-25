package com.skullking.score.domain.scoring

import com.skullking.score.core.LootPair
import com.skullking.score.core.PlayerRoundInput
import com.skullking.score.core.RoundScorer
import com.skullking.score.core.RoundValidator
import com.skullking.score.core.RoundWarning
import com.skullking.score.core.ScoreBreakdown
import com.skullking.score.core.ScoringMode
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound

/**
 * Pont entre le modèle de l'application et le module de calcul pur : le score n'est jamais saisi,
 * il est toujours recalculé à partir des saisies.
 */
object GameScoreCalculator {

    fun scoreRound(game: Game, round: RoundDetails, mode: ScoringMode = game.mode): Map<Long, ScoreBreakdown> =
        RoundScorer.scoreRound(
            mode = mode,
            cardsDealt = round.round.cardsDealt,
            inputs = inputs(round),
            loots = round.activeLoots.mapNotNull { loot ->
                loot.trickWinnerId?.let { LootPair(loot.lootPlayerId, it) }
            },
            davyJonesRequiresSuccess = game.davyJonesRequiresSuccess,
        )

    fun warnings(game: Game, round: RoundDetails): List<RoundWarning> = RoundValidator.validate(
        cardsDealt = round.round.cardsDealt,
        playerCount = game.players.size,
        discardedTricks = round.round.discardedTricks,
        inputs = inputs(round),
        lootCount = round.activeLoots.size,
        monsters = round.activeCaptures.flatMap { it.creatures },
        expansion = game.expansionCards,
    )

    /** Calcule toute la partie. [mode] permet de comparer en lecture seule avec un autre mode. */
    fun calculate(details: GameDetails, mode: ScoringMode = details.game.mode): ScoredGame {
        val game = details.game
        val running = game.players.associate { it.id to 0 }.toMutableMap()
        val rounds = details.rounds.map { round ->
            val breakdowns = scoreRound(game, round, mode)
            if (round.round.phase == RoundPhase.SCORED) {
                breakdowns.forEach { (playerId, b) -> running[playerId] = (running[playerId] ?: 0) + b.total }
            }
            ScoredRound(round, breakdowns, running.toMap())
        }
        return ScoredGame(details, mode, rounds, running.toMap())
    }

    private fun inputs(round: RoundDetails): List<PlayerRoundInput> = round.entries.map { entry ->
        PlayerRoundInput(
            playerId = entry.playerId,
            finalBid = entry.effectiveBid,
            tricks = entry.tricks,
            bonus = entry.bonus,
            rascalWager = entry.rascalWager,
            cannonball = entry.cannonball,
            davyJonesDestroyed = round.activeCaptures
                .filter { it.playerId == entry.playerId }
                .sumOf { it.creatures.size },
        )
    }
}
