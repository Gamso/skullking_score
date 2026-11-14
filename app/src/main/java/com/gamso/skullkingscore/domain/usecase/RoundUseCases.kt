package com.gamso.skullkingscore.domain.usecase

import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.DeckRules
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.EntryChange
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.repository.ScoreUpdate
import com.gamso.skullkingscore.domain.scoring.GameScoreCalculator
import com.gamso.skullkingscore.domain.scoring.TrickAggregator
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

/** Identifiers of the Loots and Davy Jones captures recorded with a trick: undoing the trick cancels them. */
data class TrickEventIds(val lootIds: List<Long> = emptyList(), val captureIds: List<Long> = emptyList())

/**
 * Every entry goes through here: one write transaction holding the change and the recomputation of the
 * cached scores, so the screens never observe a half-written entry.
 */
class EditRoundUseCase @Inject constructor(
    private val games: GameRepository,
    private val recalculate: RecalculateScoresUseCase,
) {
    /** Runs [block] then recomputes the scores, in one transaction. */
    private suspend fun <R> write(gameId: Long, block: suspend () -> R): R = games.inTransaction {
        val result = block()
        recalculate(gameId)
        result
    }

    /**
     * [transform] is applied to the stored entry, inside the transaction: an increment computed from it counts
     * even if the screen has not shown the previous one yet. Returns the entry before and after the change.
     */
    suspend fun updateEntry(
        gameId: Long,
        roundId: Long,
        playerId: Long,
        transform: (PlayerRound) -> PlayerRound,
    ): EntryChange? = write(gameId) { games.updateEntry(roundId, playerId, transform) }

    suspend fun updateRound(gameId: Long, roundId: Long, transform: (Round) -> Round) =
        write(gameId) { games.updateRound(roundId, transform) }

    suspend fun setPhase(gameId: Long, roundId: Long, phase: RoundPhase) =
        updateRound(gameId, roundId) { it.copy(phase = phase) }

    /** [trickWinnerId] is `null` during play: the winner is filled in when trick [trickNumber] is entered. */
    suspend fun addLoot(
        gameId: Long,
        roundId: Long,
        lootPlayerId: Long,
        trickWinnerId: Long?,
        trickNumber: Int? = null,
    ): Long = write(gameId) { games.addLoot(roundId, lootPlayerId, trickWinnerId, trickNumber) }

    suspend fun addDavyJones(
        gameId: Long,
        roundId: Long,
        playerId: Long,
        creatures: Set<SeaMonster>,
        trickNumber: Int? = null,
    ): Long = write(gameId) { games.addDavyJones(roundId, playerId, creatures, trickNumber) }

    /**
     * Records (or corrects) a live trick, then copies into the round the tricks won, the cards
     * captured, the Rascal wager, the bid adjusted by Harry the Giant and the destroyed tricks; the results
     * screen shows them pre-filled. Everything, with the events played in the trick, is one transaction.
     *
     * The events go first: recording the trick then gives its winner to the Loots of that trick.
     *
     * @param draft the trick entered; `bidAdjustment` (-1, 0, +1) is Harry the Giant's adjustment for the winner
     * @param lootPlayerIds players who played a Loot in this trick
     * @param chests Davy Jones' Lockers opened in this trick: player and creatures
     * @return the identifiers of the events created
     */
    suspend fun recordTrick(
        gameId: Long,
        roundId: Long,
        number: Int,
        draft: TrickDraft,
        lootPlayerIds: List<Long> = emptyList(),
        chests: List<Pair<Long, Set<SeaMonster>>> = emptyList(),
    ): TrickEventIds = write(gameId) {
        val ids = TrickEventIds(
            lootIds = lootPlayerIds.map { games.addLoot(roundId, it, null, number) },
            captureIds = chests.map { (playerId, creatures) ->
                games.addDavyJones(roundId, playerId, creatures, number)
            },
        )
        games.upsertTrick(roundId, number, draft)
        games.setLootWinner(roundId, number, draft.winnerId)
        syncFromTricks(gameId, roundId)
        ids
    }

    /**
     * Undoes [recordTrick]: cancels the events recorded with the trick, then restores the previous version of
     * the trick ([before]), or deletes it if it was new. One transaction.
     */
    suspend fun undoTrick(gameId: Long, roundId: Long, number: Int, before: TrickDraft?, events: TrickEventIds) =
        write(gameId) {
            events.lootIds.forEach { games.cancelLoot(it) }
            events.captureIds.forEach { games.cancelDavyJones(it) }
            if (before == null) {
                games.deleteTrick(roundId, number)
            } else {
                games.upsertTrick(roundId, number, before)
            }
            games.setLootWinner(roundId, number, before?.winnerId)
            syncFromTricks(gameId, roundId)
        }

    /** Cancels the last trick entered. */
    suspend fun deleteLastTrick(gameId: Long, roundId: Long) = write(gameId) {
        val round = games.getGame(gameId)?.rounds?.firstOrNull { it.round.id == roundId } ?: return@write
        val last = round.tricks.lastOrNull() ?: return@write
        games.deleteTrick(roundId, last.number)
        games.setLootWinner(roundId, last.number, null)
        syncFromTricks(gameId, roundId)
    }

    /** Copies the tricks into the entries and the round. Called inside the transaction of the caller. */
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
    }

    suspend fun cancelLoot(gameId: Long, lootId: Long) = write(gameId) { games.cancelLoot(lootId) }

    suspend fun cancelDavyJones(gameId: Long, captureId: Long) = write(gameId) { games.cancelDavyJones(captureId) }

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
