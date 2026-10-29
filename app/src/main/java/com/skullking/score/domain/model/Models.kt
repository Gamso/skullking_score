package com.skullking.score.domain.model

import com.skullking.score.core.Bonus
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard

data class Player(
    val id: Long,
    val uuid: String,
    val name: String,
    /** Couleur ARGB. */
    val color: Long,
    val createdAt: Long,
    /** Hidden from selection lists, without losing their statistics. */
    val archived: Boolean = false,
)

enum class GameStatus { IN_PROGRESS, FINISHED }

enum class RoundPhase { BIDDING, PLAYING, SCORED }

data class Game(
    val id: Long,
    val uuid: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: ScoringMode,
    val expansionCards: Set<ExpansionCard>,
    val status: GameStatus,
    /** Players in table order. */
    val players: List<Player>,
    /** Seat of the dealer of the first round; the dealer advances one seat each round. */
    val firstDealerSeat: Int = 0,
) {
    /** Dealer of round [roundNumber] (starting at 1). */
    fun dealerFor(roundNumber: Int): Player? =
        if (players.isEmpty()) null else players[Math.floorMod(firstDealerSeat + roundNumber - 1, players.size)]
}

data class Round(
    val id: Long,
    val gameId: Long,
    val number: Int,
    val cardsDealt: Int,
    val isExtra: Boolean,
    val discardedTricks: Int,
    val phase: RoundPhase,
)

data class PlayerRound(
    val roundId: Long,
    val playerId: Long,
    val bid: Int,
    /** Bid after correction (Harry the Giant); `null` until it has been modified. */
    val finalBid: Int?,
    val tricks: Int,
    val cannonball: Boolean,
    val rascalWager: Int,
    val bonus: Bonus,
    /** Computed score, cached for display (never entered); half points exist (Rascal). */
    val score: Double,
) {
    val effectiveBid: Int get() = finalBid ?: bid
}

data class LootLink(
    val id: Long,
    val roundId: Long,
    val lootPlayerId: Long,
    /** `null` until the current trick is finished (or if it was destroyed). */
    val trickWinnerId: Long?,
    val createdAt: Long,
    /** Set when the event was cancelled; the log keeps a trace of it. */
    val cancelledAt: Long?,
    /** Trick during which the Loot was played; `null` if unknown. */
    val trickNumber: Int? = null,
) {
    val isActive: Boolean get() = cancelledAt == null
}

data class DavyJonesCapture(
    val id: Long,
    val roundId: Long,
    val playerId: Long,
    val creatures: Set<SeaMonster>,
    val createdAt: Long,
    val cancelledAt: Long?,
    val trickNumber: Int? = null,
) {
    val isActive: Boolean get() = cancelledAt == null
}

/** A trick entered live: its winner (`null` = trick destroyed by the Kraken) and the cards it captured. */
data class Trick(
    val id: Long,
    val roundId: Long,
    val number: Int,
    val winnerId: Long?,
    val bonus: Bonus,
    val createdAt: Long,
    /** Rascal wager (0, 10 or 20) made by the trick winner. */
    val rascalWager: Int = 0,
    /** Winner's bid adjusted by Harry the Giant played in this trick: -1, 0 or +1. */
    val bidAdjustment: Int = 0,
    val winningCard: WinningCard? = null,
    val winningPirate: Pirate? = null,
)

/** What entering a trick provides: winner (`null` = destroyed trick), captured cards and optional settings. */
data class TrickDraft(
    val winnerId: Long?,
    val bonus: Bonus = Bonus.NONE,
    val rascalWager: Int = 0,
    val bidAdjustment: Int = 0,
    val winningCard: WinningCard? = null,
    val winningPirate: Pirate? = null,
)

data class RoundDetails(
    val round: Round,
    val entries: List<PlayerRound>,
    val loots: List<LootLink>,
    val captures: List<DavyJonesCapture>,
    /** Tricks entered, sorted by number. */
    val tricks: List<Trick> = emptyList(),
) {
    /** Number of the trick being played. */
    val currentTrickNumber: Int get() = tricks.size + 1

    val activeLoots: List<LootLink> get() = loots.filter { it.isActive }
    val activeCaptures: List<DavyJonesCapture> get() = captures.filter { it.isActive }

    fun entry(playerId: Long): PlayerRound? = entries.firstOrNull { it.playerId == playerId }
}

data class GameDetails(
    val game: Game,
    /** Sorted by round number. */
    val rounds: List<RoundDetails>,
) {
    fun player(id: Long): Player? = game.players.firstOrNull { it.id == id }
}

/** Summary for the history: totals come from the cached scores. */
data class GameSummary(val game: Game, val totals: Map<Long, Double>) {
    val winners: List<Player>
        get() {
            val best = totals.values.maxOrNull() ?: return emptyList()
            return game.players.filter { totals[it.id] == best }
        }
}

/** Where to resume a game. */
sealed interface GameStep {
    data class Prepare(val roundNumber: Int) : GameStep
    data class Bids(val roundId: Long) : GameStep
    data class Play(val roundId: Long) : GameStep
    data object Scoreboard : GameStep
    data object Finished : GameStep
}

fun GameDetails.nextStep(): GameStep {
    if (game.status == GameStatus.FINISHED) return GameStep.Finished
    val last = rounds.lastOrNull()?.round ?: return GameStep.Prepare(1)
    return when (last.phase) {
        RoundPhase.BIDDING -> GameStep.Bids(last.id)
        RoundPhase.PLAYING -> GameStep.Play(last.id)
        RoundPhase.SCORED -> GameStep.Scoreboard
    }
}
