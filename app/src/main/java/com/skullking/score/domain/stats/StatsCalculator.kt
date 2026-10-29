package com.skullking.score.domain.stats

import com.skullking.score.core.BonusType
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.scoring.GameScoreCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

enum class StatsPeriod {
    MONTH,
    YEAR,
    ALL,
    ;

    /** Start of the period in milliseconds, or `null` for "all". */
    fun startMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start: LocalDate = when (this) {
            MONTH -> today.withDayOfMonth(1)
            YEAR -> today.withDayOfYear(1)
            ALL -> return null
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** Number of players at the table. */
@Suppress("MagicNumber")
enum class PlayerCountFilter(val range: IntRange?) {
    ALL(null),
    TWO_THREE(2..3),
    FOUR_FIVE(4..5),
    SIX_PLUS(6..Int.MAX_VALUE),
}

enum class ExpansionFilter { ALL, WITH, WITHOUT }

/** Round size, for bid success by number of cards. */
@Suppress("MagicNumber")
enum class RoundSize(val cards: IntRange) {
    SMALL(1..3),
    MEDIUM(4..6),
    LARGE(7..10),
    HUGE(11..Int.MAX_VALUE),
    ;

    companion object {
        fun of(cards: Int): RoundSize = entries.first { cards in it.cards }
    }
}

/**
 * A Rascal score cannot be compared with a classic score: the mode is part of the filter. The other filters are
 * optional and combinable.
 */
data class StatsFilter(
    val mode: ScoringMode = ScoringMode.CLASSIC,
    val playerCount: PlayerCountFilter = PlayerCountFilter.ALL,
    val expansion: ExpansionFilter = ExpansionFilter.ALL,
    /** If not empty, only games where all these players were at the table count. */
    val presentPlayerIds: Set<Long> = emptySet(),
)

data class MonthlyPoint(val month: YearMonth, val games: Int, val averageScore: Double, val bidSuccessRate: Double)

data class HeadToHead(val opponent: Player, val commonGames: Int, val wins: Int, val losses: Int)

/** A notable round (best, worst): points, round number and date of the game. */
data class RoundRecord(val points: Double, val roundNumber: Int, val gameStartedAt: Long)

data class SuccessRate(val attempts: Int, val successes: Int) {
    val rate: Double? get() = if (attempts == 0) null else successes.toDouble() / attempts
}

data class PlayerStats(
    val player: Player,
    val gamesPlayed: Int,
    val wins: Int,
    val averageScore: Double,
    val bestScore: Double,
    val worstScore: Double,
    val roundsPlayed: Int,
    val bidsSucceeded: Int,
    val zeroBids: Int,
    val zeroBidsSucceeded: Int,
    /** Bonus points actually scored, by type. */
    val bonusPoints: Map<BonusType, Int>,
    val monthly: List<MonthlyPoint>,
    val headToHead: List<HeadToHead>,
    /** Tricks won with each card, when it was entered. */
    val winsByCard: Map<WinningCard, Int> = emptyMap(),
    /** Tricks won with each pirate, when it was entered. */
    val winsByPirate: Map<Pirate, Int> = emptyMap(),
    val bestRound: RoundRecord? = null,
    val worstRound: RoundRecord? = null,
    val longestExactStreak: Int = 0,
    val longestWinStreak: Int = 0,
    val longestZeroStreak: Int = 0,
    /** Biggest bonus total actually scored in a single round. */
    val biggestBonusRound: Double = 0.0,
    val successBySize: Map<RoundSize, SuccessRate> = emptyMap(),
    /** Tricks won minus tricks bid, on average per round: positive = tends to under-bid. */
    val averageBidBias: Double = 0.0,
    /** Games in which at least one trick was entered live (trick statistics only cover those). */
    val gamesWithTricks: Int = 0,
    val rascalWagers: SuccessRate = SuccessRate(0, 0),
    val harryAdjustments: SuccessRate = SuccessRate(0, 0),
    /** Game totals, from the oldest to the most recent. */
    val gameTotals: List<Double> = emptyList(),
) {
    /** Card this player wins tricks with most often, when tricks were entered live. */
    val favoriteCard: WinningCard? get() = winsByCard.maxByOrNull { it.value }?.key

    /** Pirate this player wins tricks with most often. */
    val favoritePirate: Pirate? get() = winsByPirate.maxByOrNull { it.value }?.key

    val winRate: Double get() = if (gamesPlayed == 0) 0.0 else wins.toDouble() / gamesPlayed
    val bidSuccessRate: Double get() = if (roundsPlayed == 0) 0.0 else bidsSucceeded.toDouble() / roundsPlayed
    val zeroBidSuccessRate: Double? get() = if (zeroBids == 0) null else zeroBidsSucceeded.toDouble() / zeroBids

    /** Rolling average score over the last [window] games, for each game played. */
    fun rollingAverage(window: Int = ROLLING_WINDOW): List<Double> =
        gameTotals.indices.map { i -> gameTotals.subList(maxOf(0, i - window + 1), i + 1).average() }

    companion object {
        const val ROLLING_WINDOW = 5
    }
}

data class StatsOverview(val gamesCount: Int)

object StatsCalculator {

    /** Finished games kept by the filter, scored. */
    fun select(games: List<GameDetails>, filter: StatsFilter): List<ScoredGame> = games
        .filter { it.game.status == GameStatus.FINISHED }
        .filter { it.game.mode == filter.mode }
        .filter { filter.playerCount.range?.contains(it.game.players.size) ?: true }
        .filter {
            when (filter.expansion) {
                ExpansionFilter.ALL -> true
                ExpansionFilter.WITH -> it.game.expansionCards.isNotEmpty()
                ExpansionFilter.WITHOUT -> it.game.expansionCards.isEmpty()
            }
        }
        .filter { details ->
            val seated = details.game.players.map { it.id }.toSet()
            seated.containsAll(filter.presentPlayerIds)
        }
        .map { GameScoreCalculator.calculate(it) }
        .sortedBy { it.game.startedAt }

    fun overview(scored: List<ScoredGame>): StatsOverview = StatsOverview(scored.size)

    fun compute(
        games: List<GameDetails>,
        filter: StatsFilter,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlayerStats> = computeFrom(select(games, filter), zone)

    fun computeFrom(scored: List<ScoredGame>, zone: ZoneId = ZoneId.systemDefault()): List<PlayerStats> {
        val players = scored.flatMap { it.game.players }.distinctBy { it.id }
        return players.map { player -> statsFor(player, scored, zone) }
            .sortedWith(compareByDescending<PlayerStats> { it.gamesPlayed }.thenBy { it.player.name.lowercase() })
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun statsFor(player: Player, scoredGames: List<ScoredGame>, zone: ZoneId): PlayerStats {
        val mine = scoredGames.filter { sg -> sg.game.players.any { it.id == player.id } }
        val totals = mine.map { it.totals.getValue(player.id) }
        val wins = mine.count { sg -> sg.totals.getValue(player.id) == sg.totals.values.max() }

        var rounds = 0
        var succeeded = 0
        var zeroBids = 0
        var zeroOk = 0
        var bidBias = 0
        var exactStreak = 0
        var longestExact = 0
        var zeroStreak = 0
        var longestZero = 0
        var biggestBonus = 0.0
        var best: RoundRecord? = null
        var worst: RoundRecord? = null
        val sizes = mutableMapOf<RoundSize, SuccessRate>()
        val bonus = mutableMapOf<BonusType, Int>()
        val monthRounds = mutableMapOf<YearMonth, Pair<Int, Int>>() // (rounds, successful)
        val monthTotals = mutableMapOf<YearMonth, MutableList<Double>>()
        var rascalTried = 0
        var rascalWon = 0
        var harryUsed = 0
        var harryWon = 0

        for (sg in mine) {
            val month = YearMonth.from(Instant.ofEpochMilli(sg.game.startedAt).atZone(zone))
            monthTotals.getOrPut(month) { mutableListOf() }.add(sg.totals.getValue(player.id))
            for (round in sg.rounds.filter { it.isScored }) {
                val breakdown = round.breakdowns[player.id] ?: continue
                val entry = round.details.entry(player.id) ?: continue
                rounds++
                if (breakdown.success) succeeded++
                bidBias += entry.tricks - entry.effectiveBid
                val record = RoundRecord(breakdown.total, round.round.number, sg.game.startedAt)
                if (best == null || record.points > best.points) best = record
                if (worst == null || record.points < worst.points) worst = record
                biggestBonus = maxOf(biggestBonus, breakdown.bonusApplied)

                exactStreak = if (breakdown.success) exactStreak + 1 else 0
                longestExact = maxOf(longestExact, exactStreak)
                val size = RoundSize.of(round.round.cardsDealt)
                val previous = sizes[size] ?: SuccessRate(0, 0)
                sizes[size] = SuccessRate(previous.attempts + 1, previous.successes + if (breakdown.success) 1 else 0)

                if (entry.effectiveBid == 0) {
                    zeroBids++
                    if (breakdown.success) zeroOk++
                    zeroStreak = if (breakdown.success) zeroStreak + 1 else 0
                    longestZero = maxOf(longestZero, zeroStreak)
                }
                if (!breakdown.bonusDiscarded) {
                    breakdown.bonusLines.forEach { bonus[it.type] = (bonus[it.type] ?: 0) + it.points }
                }
                round.details.tricks.filter { it.winnerId == player.id }.forEach { trick ->
                    if (trick.rascalWager > 0) {
                        rascalTried++
                        if (breakdown.success) rascalWon++
                    }
                    if (trick.bidAdjustment != 0) {
                        harryUsed++
                        if (breakdown.success) harryWon++
                    }
                }
                val (r, s) = monthRounds[month] ?: (0 to 0)
                monthRounds[month] = (r + 1) to (s + if (breakdown.success) 1 else 0)
            }
            // Streaks of successful bids: a new game starts again from zero.
            exactStreak = 0
            zeroStreak = 0
        }

        val monthly = monthTotals.keys.sorted().map { month ->
            val (r, s) = monthRounds[month] ?: (0 to 0)
            MonthlyPoint(
                month = month,
                games = monthTotals.getValue(month).size,
                averageScore = monthTotals.getValue(month).average(),
                bidSuccessRate = if (r == 0) 0.0 else s.toDouble() / r,
            )
        }

        val opponents = mine.flatMap { it.game.players }.filter { it.id != player.id }.distinctBy { it.id }
        val headToHead = opponents.map { opponent ->
            val common = mine.filter { sg -> sg.game.players.any { it.id == opponent.id } }
            HeadToHead(
                opponent = opponent,
                commonGames = common.size,
                wins = common.count { it.totals.getValue(player.id) > it.totals.getValue(opponent.id) },
                losses = common.count { it.totals.getValue(player.id) < it.totals.getValue(opponent.id) },
            )
        }.sortedByDescending { it.commonGames }

        val tricksWon = mine
            .flatMap { sg -> sg.rounds.filter { it.isScored }.flatMap { it.details.tricks } }
            .filter { it.winnerId == player.id }

        var winStreak = 0
        var longestWin = 0
        mine.forEach { sg ->
            winStreak = if (sg.totals.getValue(player.id) == sg.totals.values.max()) winStreak + 1 else 0
            longestWin = maxOf(longestWin, winStreak)
        }

        return PlayerStats(
            player = player,
            gamesPlayed = mine.size,
            wins = wins,
            averageScore = if (totals.isEmpty()) 0.0 else totals.average(),
            bestScore = totals.maxOrNull() ?: 0.0,
            worstScore = totals.minOrNull() ?: 0.0,
            roundsPlayed = rounds,
            bidsSucceeded = succeeded,
            zeroBids = zeroBids,
            zeroBidsSucceeded = zeroOk,
            bonusPoints = bonus,
            monthly = monthly,
            headToHead = headToHead,
            winsByCard = tricksWon.mapNotNull { it.winningCard }.groupingBy { it }.eachCount(),
            winsByPirate = tricksWon.mapNotNull { it.winningPirate }.groupingBy { it }.eachCount(),
            bestRound = best,
            worstRound = worst,
            longestExactStreak = longestExact,
            longestWinStreak = longestWin,
            longestZeroStreak = longestZero,
            biggestBonusRound = biggestBonus,
            successBySize = sizes,
            averageBidBias = if (rounds == 0) 0.0 else bidBias.toDouble() / rounds,
            gamesWithTricks = mine.count { sg -> sg.rounds.any { it.details.tricks.isNotEmpty() } },
            rascalWagers = SuccessRate(rascalTried, rascalWon),
            harryAdjustments = SuccessRate(harryUsed, harryWon),
            gameTotals = totals,
        )
    }
}

/** Head-to-head between two players, over the games where they were both present. */
data class DuelStats(
    val first: Player,
    val second: Player,
    val commonGames: Int,
    val firstWins: Int,
    val secondWins: Int,
    val ties: Int,
    /** Average score gap, positive when [first] is ahead. */
    val averageGap: Double,
    val biggestGap: Double,
    val firstBestRound: RoundRecord?,
    val firstWorstRound: RoundRecord?,
    val secondBestRound: RoundRecord?,
    val secondWorstRound: RoundRecord?,
) {
    val hasEnoughGames: Boolean get() = commonGames >= MIN_COMMON_GAMES

    companion object {
        const val MIN_COMMON_GAMES = 2
    }
}

object DuelCalculator {

    fun compare(scored: List<ScoredGame>, first: Player, second: Player): DuelStats {
        val common = scored.filter { sg ->
            sg.game.players.any { it.id == first.id } && sg.game.players.any { it.id == second.id }
        }
        val gaps = common.map { it.totals.getValue(first.id) - it.totals.getValue(second.id) }
        fun records(player: Player): Pair<RoundRecord?, RoundRecord?> {
            val all = common.flatMap { sg ->
                sg.rounds.filter { it.isScored }.mapNotNull { round ->
                    round.breakdowns[player.id]?.let { RoundRecord(it.total, round.round.number, sg.game.startedAt) }
                }
            }
            return all.maxByOrNull { it.points } to all.minByOrNull { it.points }
        }
        val (firstBest, firstWorst) = records(first)
        val (secondBest, secondWorst) = records(second)
        return DuelStats(
            first = first,
            second = second,
            commonGames = common.size,
            firstWins = gaps.count { it > 0 },
            secondWins = gaps.count { it < 0 },
            ties = gaps.count { it == 0.0 },
            averageGap = if (gaps.isEmpty()) 0.0 else gaps.average(),
            biggestGap = gaps.maxOfOrNull { abs(it) } ?: 0.0,
            firstBestRound = firstBest,
            firstWorstRound = firstWorst,
            secondBestRound = secondBest,
            secondWorstRound = secondWorst,
        )
    }
}
