package com.gamso.skullkingscore.domain.scoring

import com.gamso.skullkingscore.core.LootPair
import com.gamso.skullkingscore.core.PlayerRoundInput
import com.gamso.skullkingscore.core.RoundScorer
import com.gamso.skullkingscore.core.RoundValidator
import com.gamso.skullkingscore.core.RoundWarning
import com.gamso.skullkingscore.core.ScoreBreakdown
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.Game
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.ScoredGame
import com.gamso.skullkingscore.domain.model.ScoredRound

/**
 * Bridge between the app model and the pure scoring module: the score is never entered,
 * it is always recomputed from the entries.
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

    /** Computes the whole game. [mode] allows a read-only comparison with another mode. */
    fun calculate(details: GameDetails, mode: ScoringMode = details.game.mode): ScoredGame {
        val game = details.game
        val running = game.players.associate { it.id to 0.0 }.toMutableMap()
        val rounds = details.rounds.map { round ->
            val breakdowns = scoreRound(game, round, mode)
            if (round.round.phase == RoundPhase.SCORED) {
                breakdowns.forEach { (playerId, b) -> running[playerId] = (running[playerId] ?: 0.0) + b.total }
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
