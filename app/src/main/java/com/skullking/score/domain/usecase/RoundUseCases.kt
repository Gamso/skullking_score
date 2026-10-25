package com.skullking.score.domain.usecase

import com.skullking.score.core.Bonus
import com.skullking.score.core.DeckRules
import com.skullking.score.core.SeaMonster
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.TrickDraft
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.ScoreUpdate
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.scoring.TrickAggregator
import javax.inject.Inject

/** Recomputes the whole game and updates the cached score of each player and round. */
class RecalculateScoresUseCase @Inject constructor(private val games: GameRepository) {
    suspend operator fun invoke(gameId: Long) {
        val details = games.getGame(gameId) ?: return
        val scored = GameScoreCalculator.calculate(details)
        val updates = scored.rounds.flatMap { round ->
            round.breakdowns.map { (playerId, breakdown) ->
                ScoreUpdate(round.round.id, playerId, breakdown.total)
            }
        }
        games.updateScores(updates)
    }
}

/** Number of cards suggested for the next round, based on the previous one. */
fun GameDetails.suggestedCards(): Int {
    val number = rounds.size + 1
    return DeckRules.suggestedCards(
        number,
        rounds.lastOrNull()?.round?.cardsDealt,
        game.players.size,
        game.expansionCards,
    )
}

class StartRoundUseCase @Inject constructor(private val games: GameRepository) {
    /** Creates the next round (bidding in progress) and returns its identifier. */
    suspend operator fun invoke(gameId: Long, cardsDealt: Int): Long {
        val details = checkNotNull(games.getGame(gameId)) { "Game not found" }
        val number = details.rounds.size + 1
        return games.addRound(
            gameId = gameId,
            number = number,
            cardsDealt = cardsDealt.coerceIn(1, DeckRules.MAX_CARDS_PER_ROUND),
            isExtra = number > DeckRules.PLANNED_ROUNDS,
        )
    }
}

/** Every entry goes through here: atomic write, then recomputation of the cached scores. */
class EditRoundUseCase @Inject constructor(
    private val games: GameRepository,
    private val recalculate: RecalculateScoresUseCase,
) {
    suspend fun updateEntry(gameId: Long, roundId: Long, playerId: Long, transform: (PlayerRound) -> PlayerRound) {
        games.updateEntry(roundId, playerId, transform)
        recalculate(gameId)
    }

    suspend fun updateRound(gameId: Long, roundId: Long, transform: (Round) -> Round) {
        games.updateRound(roundId, transform)
        recalculate(gameId)
    }

    suspend fun setPhase(gameId: Long, roundId: Long, phase: RoundPhase) =
        updateRound(gameId, roundId) { it.copy(phase = phase) }

    /** [trickWinnerId] is `null` during play: the winner is filled in when trick [trickNumber] is entered. */
    suspend fun addLoot(
        gameId: Long,
        roundId: Long,
        lootPlayerId: Long,
        trickWinnerId: Long?,
        trickNumber: Int? = null,
    ) {
        games.addLoot(roundId, lootPlayerId, trickWinnerId, trickNumber)
        recalculate(gameId)
    }

    suspend fun addDavyJones(
        gameId: Long,
        roundId: Long,
        playerId: Long,
        creatures: Set<SeaMonster>,
        trickNumber: Int? = null,
    ) {
        games.addDavyJones(roundId, playerId, creatures, trickNumber)
        recalculate(gameId)
    }

    /**
     * Records (or corrects) a live trick, then copies into the round the tricks won, the cards
     * captured, the Rascal wager, the bid adjusted by Harry the Giant and the destroyed tricks; the results
     * screen shows them pre-filled.
     *
     * @param draft the trick entered; `bidAdjustment` (-1, 0, +1) is Harry the Giant's adjustment for the winner
     */
    suspend fun recordTrick(gameId: Long, roundId: Long, number: Int, draft: TrickDraft) {
        games.upsertTrick(roundId, number, draft)
        games.setLootWinner(roundId, number, draft.winnerId)
        syncFromTricks(gameId, roundId)
    }

    /** Cancels the last trick entered. */
    suspend fun deleteLastTrick(gameId: Long, roundId: Long) {
        val round = games.getGame(gameId)?.rounds?.firstOrNull { it.round.id == roundId } ?: return
        val last = round.tricks.lastOrNull() ?: return
        games.deleteTrick(roundId, last.number)
        games.setLootWinner(roundId, last.number, null)
        syncFromTricks(gameId, roundId)
    }

    private suspend fun syncFromTricks(gameId: Long, roundId: Long) {
        val details = games.getGame(gameId) ?: return
        val round = details.rounds.firstOrNull { it.round.id == roundId } ?: return
        val totals = TrickAggregator.aggregate(round.tricks)
        details.game.players.forEach { player ->
            val mine = totals.perPlayer[player.id]
            games.updateEntry(roundId, player.id) {
                it.copy(
                    tricks = mine?.tricks ?: 0,
                    bonus = mine?.bonus ?: Bonus.NONE,
                    rascalWager = mine?.rascalWager ?: 0,
                    finalBid = mine?.bidAdjustment?.takeIf { it != 0 }?.let { delta ->
                        (it.bid + delta).coerceIn(0, round.round.cardsDealt)
                    },
                )
            }
        }
        games.updateRound(roundId) { it.copy(discardedTricks = totals.destroyed) }
        recalculate(gameId)
    }

    suspend fun cancelLoot(gameId: Long, lootId: Long) {
        games.cancelLoot(lootId)
        recalculate(gameId)
    }

    suspend fun cancelDavyJones(gameId: Long, captureId: Long) {
        games.cancelDavyJones(captureId)
        recalculate(gameId)
    }

    /** "Undo the last action": reopens the last validated round to correct its results. */
    suspend fun reopenLastRound(gameId: Long): Long? {
        val last = games.getGame(gameId)?.rounds?.lastOrNull()?.round ?: return null
        if (last.phase != RoundPhase.SCORED) return null
        setPhase(gameId, last.id, RoundPhase.PLAYING)
        return last.id
    }
}

class FinishGameUseCase @Inject constructor(private val games: GameRepository) {
    suspend operator fun invoke(gameId: Long) = games.finishGame(gameId)
}
