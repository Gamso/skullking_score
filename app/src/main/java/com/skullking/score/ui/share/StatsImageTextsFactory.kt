package com.skullking.score.ui.share

import android.content.Context
import com.skullking.score.R
import com.skullking.score.domain.stats.ExpansionFilter
import com.skullking.score.domain.stats.PlayerCountFilter
import com.skullking.score.domain.stats.PlayerStats
import com.skullking.score.domain.stats.RoundRecord
import com.skullking.score.domain.stats.RoundSize
import com.skullking.score.domain.stats.StatsFilter
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.feature.stats.labelRes
import com.skullking.score.ui.feature.stats.percent
import com.skullking.score.ui.labelRes
import java.text.DateFormat
import java.util.Date

/** Rassemble les textes (traduits) de l'image de statistiques d'un joueur. */
object StatsImageTextsFactory {

    fun create(context: Context, stats: PlayerStats, filter: StatsFilter, gamesCount: Int): StatsImageTexts {
        val locale = context.resources.configuration.locales[0]
        fun text(id: Int, vararg args: Any) = context.getString(id, *args)
        fun record(value: RoundRecord?): String {
            if (value == null) return "–"
            val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(value.gameStartedAt))
            return text(R.string.stats_record_round, formatPoints(value.points), value.roundNumber, date)
        }

        val filters = buildList {
            add(text(filter.mode.labelRes()))
            if (filter.playerCount != PlayerCountFilter.ALL) add(text(filter.playerCount.labelRes()))
            if (filter.expansion != ExpansionFilter.ALL) add(text(filter.expansion.labelRes()))
            add(context.resources.getQuantityString(R.plurals.stats_games_count, gamesCount, gamesCount))
        }
        val records = buildList {
            add(text(R.string.stats_record_best_round) to record(stats.bestRound))
            add(text(R.string.stats_record_worst_round) to record(stats.worstRound))
            add(text(R.string.stats_record_exact_streak) to stats.longestExactStreak.toString())
            add(text(R.string.stats_record_win_streak) to stats.longestWinStreak.toString())
            if (stats.zeroBids > 0) add(text(R.string.stats_record_zero_streak) to stats.longestZeroStreak.toString())
            add(text(R.string.stats_record_bonus) to formatPoints(stats.biggestBonusRound))
        }
        return StatsImageTexts(
            appName = text(R.string.app_name),
            subtitle = filters.joinToString(" · "),
            playerLine = text(R.string.stats_games_wins, stats.gamesPlayed, stats.wins, stats.winRate.percent()),
            summary = listOf(
                text(R.string.stats_average) to "%.1f".format(locale, stats.averageScore),
                text(R.string.stats_best) to stats.bestScore.toString(),
                text(R.string.stats_worst) to stats.worstScore.toString(),
                text(R.string.stats_bid_success) to stats.bidSuccessRate.percent(),
                text(R.string.stats_zero_success) to (stats.zeroBidSuccessRate?.percent() ?: "–"),
            ),
            recordsTitle = text(R.string.stats_records_title),
            records = records,
            sizeTitle = text(R.string.stats_size_title),
            sizes = RoundSize.entries.map { size ->
                val rate = stats.successBySize[size]
                text(size.labelRes()) to
                    (rate?.rate?.let { "${it.percent()} (${rate.successes}/${rate.attempts})" } ?: "–")
            },
            trendTitle = text(R.string.stats_trend_title),
            trendLegend = text(R.string.stats_image_legend_games) to text(
                R.string.stats_image_legend_average,
                PlayerStats.ROLLING_WINDOW,
            ),
        )
    }
}
