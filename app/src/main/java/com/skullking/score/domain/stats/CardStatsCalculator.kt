package com.skullking.score.domain.stats

import com.skullking.score.core.BonusType
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoreBreakdown
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound

/** Outcome of a Rascal wager of a given amount: how many were made, how many succeeded, and the points won or lost. */
data class WagerStats(val attempts: Int, val successes: Int, val netPoints: Int)

/** Loots played, how many were valid (both players made their bid) and who played them most. */
data class LootStats(val total: Int, val validated: Int, val byPlayer: List<Pair<String, Int>>)

/** Number of cards captured of one kind and the points they were worth. */
data class CaptureStats(val count: Int, val points: Int)

/** Kraken: tricks destroyed, and how bids went in rounds where one was destroyed or not. */
data class KrakenStats(val destroyedTricks: Int, val roundsWithDestroyed: SuccessRate, val roundsWithout: SuccessRate)

/** Games with or without the expansion: number of games, bid success and average points per player and round. */
data class ExpansionSide(val games: Int, val bidSuccess: SuccessRate, val averagePointsPerRound: Double)

data class ExpansionComparison(
    val with: ExpansionSide,
    val without: ExpansionSide,
    /** Tricks won with a card that only exists in the expansion (First Mate, Mary Thorne). */
    val expansionCardWins: Int,
)

/** Statistics about the cards themselves, over all players of the games kept by the filter. */
data class CardStats(
    val gamesCount: Int,
    /** Games where at least one trick was entered live: trick statistics only cover those. */
    val gamesWithTricks: Int,
    val tricksRecorded: Int,
    val winsByCard: Map<WinningCard, Int>,
    val winsByPirate: Map<Pirate, Int>,
    /** Share of the rounds ending with a successful bid, among the rounds where a player won a trick with the card. */
    val successWhenWonByCard: Map<WinningCard, SuccessRate>,
    val successWhenWonByPirate: Map<Pirate, SuccessRate>,
    val captures: Map<BonusType, CaptureStats>,
    /** Player-rounds analysed, and those where at least one bonus counted. */
    val playerRounds: Int,
    val playerRoundsWithBonus: Int,
    val bonusPoints: Int,
    /** Wagers made by the winner of a trick, by amount (10 or 20). */
    val rascalWagers: Map<Int, WagerStats>,
    /** Bid adjustments by Harry the Giant (+1 or -1) and whether the round was then successful. */
    val harryAdjustments: Map<Int, SuccessRate>,
    val loots: LootStats,
    val davyJonesMonsters: Map<SeaMonster, Int>,
    val davyJonesPoints: Int,
    /** For each card, how many of its trick wins happened in the first, middle and last third of a round. */
    val timing: Map<WinningCard, List<Int>>,
    val kraken: KrakenStats,
) {
    val hasTricks: Boolean get() = tricksRecorded > 0
}

object CardStatsCalculator {
    private const val THIRDS = 3
    private const val MIN_CARDS_FOR_TIMING = 3
    private const val SMALL_WAGER = 10
    private const val BIG_WAGER = 20

    private operator fun SuccessRate?.plus(success: Boolean): SuccessRate {
        val base = this ?: SuccessRate(0, 0)
        return SuccessRate(base.attempts + 1, base.successes + if (success) 1 else 0)
    }

    private class Tally {
        val wins = mutableMapOf<WinningCard, Int>()
        val pirates = mutableMapOf<Pirate, Int>()
        val cardSuccess = mutableMapOf<WinningCard, SuccessRate>()
        val pirateSuccess = mutableMapOf<Pirate, SuccessRate>()
        val captures = mutableMapOf<BonusType, CaptureStats>()
        val wagers = mutableMapOf<Int, WagerStats>()
        val harry = mutableMapOf<Int, SuccessRate>()
        val monsters = mutableMapOf<SeaMonster, Int>()
        val timing = mutableMapOf<WinningCard, IntArray>()
        val lootByPlayer = mutableMapOf<String, Int>()
        var tricks = 0
        var playerRounds = 0
        var playerRoundsWithBonus = 0
        var bonusPoints = 0
        var lootTotal = 0
        var lootValid = 0
        var davyPoints = 0
        var destroyed = 0
        var krakenWith = SuccessRate(0, 0)
        var krakenWithout = SuccessRate(0, 0)
    }

    fun compute(scored: List<ScoredGame>): CardStats {
        val tally = Tally()
        scored.forEach { game -> tally.addGame(game) }
        return CardStats(
            gamesCount = scored.size,
            gamesWithTricks = scored.count { g -> g.rounds.any { it.details.tricks.isNotEmpty() } },
            tricksRecorded = tally.tricks,
            winsByCard = tally.wins,
            winsByPirate = tally.pirates,
            successWhenWonByCard = tally.cardSuccess,
            successWhenWonByPirate = tally.pirateSuccess,
            captures = tally.captures,
            playerRounds = tally.playerRounds,
            playerRoundsWithBonus = tally.playerRoundsWithBonus,
            bonusPoints = tally.bonusPoints,
            rascalWagers = tally.wagers,
            harryAdjustments = tally.harry,
            loots = LootStats(
                tally.lootTotal,
                tally.lootValid,
                tally.lootByPlayer.entries.sortedByDescending { it.value }.map { it.key to it.value },
            ),
            davyJonesMonsters = tally.monsters,
            davyJonesPoints = tally.davyPoints,
            timing = tally.timing.mapValues { it.value.toList() },
            kraken = KrakenStats(tally.destroyed, tally.krakenWith, tally.krakenWithout),
        )
    }

    private fun Tally.addGame(game: ScoredGame) {
        val names = game.game.players.associate { it.id to it.name }
        for (round in game.rounds.filter { it.isScored }) {
            addBonuses(round)
            round.details.activeCaptures.forEach { capture ->
                capture.creatures.forEach { monsters[it] = (monsters[it] ?: 0) + 1 }
            }
            addLoots(round, names)
            addTricks(round)
        }
    }

    private fun Tally.addBonuses(round: ScoredRound) {
        round.breakdowns.values.forEach { breakdown ->
            playerRounds++
            bonusPoints += breakdown.bonusApplied
            if (breakdown.bonusApplied != 0) playerRoundsWithBonus++
            breakdown.bonusLines.filter { it.count != 0 }.forEach { line ->
                val previous = captures[line.type] ?: CaptureStats(0, 0)
                // Bonuses entered on a missed bid are counted as captured but are worth nothing.
                val points = if (breakdown.bonusDiscarded) 0 else line.points
                captures[line.type] = CaptureStats(previous.count + line.count, previous.points + points)
                if (line.type == BonusType.DAVY_JONES) davyPoints += points
            }
        }
    }

    private fun Tally.addLoots(round: ScoredRound, names: Map<Long, String>) {
        round.details.activeLoots.forEach { loot ->
            lootTotal++
            val name = names[loot.lootPlayerId].orEmpty()
            lootByPlayer[name] = (lootByPlayer[name] ?: 0) + 1
            val winner = loot.trickWinnerId
            val bothMadeTheirBid = winner != null &&
                round.breakdowns[loot.lootPlayerId]?.success == true &&
                round.breakdowns[winner]?.success == true
            if (bothMadeTheirBid) lootValid++
        }
    }

    private fun Tally.addTricks(round: ScoredRound) {
        val tricks = round.details.tricks
        if (tricks.isEmpty()) return
        this.tricks += tricks.size
        val destroyedHere = tricks.count { it.winnerId == null }
        destroyed += destroyedHere
        round.breakdowns.values.forEach { breakdown ->
            if (destroyedHere > 0) {
                krakenWith = krakenWith + breakdown.success
            } else {
                krakenWithout = krakenWithout + breakdown.success
            }
        }
        // Who won with which card in this round: success is counted once per player, card and round.
        val playersByCard = mutableMapOf<WinningCard, MutableSet<Long>>()
        val playersByPirate = mutableMapOf<Pirate, MutableSet<Long>>()
        tricks.forEach { trick ->
            val winner = trick.winnerId ?: return@forEach
            trick.winningCard?.let { card ->
                wins[card] = (wins[card] ?: 0) + 1
                playersByCard.getOrPut(card) { mutableSetOf() }.add(winner)
                addTiming(card, trick.number, round.round.cardsDealt)
            }
            trick.winningPirate?.let { pirate ->
                pirates[pirate] = (pirates[pirate] ?: 0) + 1
                playersByPirate.getOrPut(pirate) { mutableSetOf() }.add(winner)
            }
            round.breakdowns[winner]?.let { addTrickSettings(trick.rascalWager, trick.bidAdjustment, it) }
        }
        playersByCard.forEach { (card, players) ->
            players.mapNotNull { round.breakdowns[it] }.forEach { cardSuccess[card] = cardSuccess[card] + it.success }
        }
        playersByPirate.forEach { (pirate, players) ->
            players.mapNotNull { round.breakdowns[it] }.forEach {
                pirateSuccess[pirate] = pirateSuccess[pirate] + it.success
            }
        }
    }

    private fun Tally.addTiming(card: WinningCard, number: Int, cards: Int) {
        if (cards < MIN_CARDS_FOR_TIMING) return
        val third = ((number - 1) * THIRDS / cards).coerceIn(0, THIRDS - 1)
        timing.getOrPut(card) { IntArray(THIRDS) }[third]++
    }

    private fun Tally.addTrickSettings(wager: Int, adjustment: Int, breakdown: ScoreBreakdown) {
        if (wager == SMALL_WAGER || wager == BIG_WAGER) {
            val previous = wagers[wager] ?: WagerStats(0, 0, 0)
            wagers[wager] = WagerStats(
                previous.attempts + 1,
                previous.successes + if (breakdown.success) 1 else 0,
                previous.netPoints + breakdown.rascalPoints,
            )
        }
        if (adjustment != 0) harry[adjustment] = harry[adjustment] + breakdown.success
    }

    /**
     * Compares games with and without the expansion, over all the games kept by the other filters (so the
     * expansion filter itself must not be applied to [scored]).
     */
    fun expansionComparison(scored: List<ScoredGame>): ExpansionComparison {
        fun side(games: List<ScoredGame>): ExpansionSide {
            var bids: SuccessRate? = null
            var points = 0
            var count = 0
            games.forEach { game ->
                game.rounds.filter { it.isScored }.forEach { round ->
                    round.breakdowns.values.forEach {
                        bids = bids + it.success
                        points += it.total
                        count++
                    }
                }
            }
            return ExpansionSide(
                games.size,
                bids ?: SuccessRate(0, 0),
                if (count ==
                    0
                ) {
                    0.0
                } else {
                    points.toDouble() / count
                },
            )
        }
        val (withExpansion, without) = scored.partition { it.game.expansionCards.isNotEmpty() }
        val expansionWins = withExpansion.sumOf { game ->
            game.rounds.sumOf { round ->
                round.details.tricks.count { trick ->
                    trick.winningCard == WinningCard.FIRST_MATE || trick.winningPirate?.requires != null
                }
            }
        }
        return ExpansionComparison(side(withExpansion), side(without), expansionWins)
    }
}
