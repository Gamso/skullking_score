package com.skullking.score.ui.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skullking.score.R
import com.skullking.score.core.BonusType
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.stats.CardStats
import com.skullking.score.domain.stats.ExpansionComparison
import com.skullking.score.domain.stats.ExpansionSide
import com.skullking.score.domain.stats.SuccessRate
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.labelRes
import java.util.Locale

private val TIMING_CARDS = listOf(WinningCard.SKULL_KING, WinningCard.MERMAID, WinningCard.PIRATE)
private const val TOP_LOOT_PLAYERS = 3

/** Statistics about the cards (over all players), shown in the "Cards" tab. */
@Composable
fun CardStatsContent(cards: CardStats, expansion: ExpansionComparison, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.cards_coverage, cards.gamesWithTricks, cards.gamesCount, cards.tricksRecorded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!cards.hasTricks) {
            Text(stringResource(R.string.cards_no_tricks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TricksByCard(cards)
            PiratesSection(cards)
            SuccessWhenWon(cards)
            TimingSection(cards)
        }
        CapturesSection(cards, locale)
        if (cards.hasTricks) {
            WagersSection(cards)
            LootsSection(cards)
        }
        DavyJonesSection(cards)
        if (cards.hasTricks) KrakenSection(cards, locale)
        ExpansionSection(expansion, locale)
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun SuccessRate.text(): String = rate?.let { "${it.percent()} ($successes/$attempts)" } ?: "–"

@Composable
private fun TricksByCard(cards: CardStats) {
    val withCard = cards.winsByCard.values.sum()
    StatCard(stringResource(R.string.cards_by_card_title)) {
        WinningCard.entries.filter { (cards.winsByCard[it] ?: 0) > 0 }
            .sortedByDescending { cards.winsByCard[it] }
            .forEach { card ->
                val count = cards.winsByCard.getValue(card)
                BarRow(
                    stringResource(card.labelRes()),
                    count.toFloat() / withCard,
                    "$count (${(count.toDouble() / withCard).percent()})",
                )
            }
        Hint(stringResource(R.string.cards_by_card_hint))
    }
}

@Composable
private fun PiratesSection(cards: CardStats) {
    if (cards.winsByPirate.isEmpty()) return
    val total = cards.winsByPirate.values.sum()
    StatCard(stringResource(R.string.cards_pirates_title)) {
        cards.winsByPirate.entries.sortedByDescending { it.value }.forEach { (pirate, count) ->
            BarRow(
                stringResource(pirate.labelRes()),
                count.toFloat() / total,
                "$count (${(count.toDouble() / total).percent()})",
            )
        }
        Hint(stringResource(R.string.cards_pirates_hint))
    }
}

@Composable
private fun SuccessWhenWon(cards: CardStats) {
    StatCard(stringResource(R.string.cards_success_title)) {
        WinningCard.entries.filter { cards.successWhenWonByCard.containsKey(it) }.forEach { card ->
            ValueRow(stringResource(card.labelRes()), cards.successWhenWonByCard.getValue(card).text())
        }
        cards.successWhenWonByPirate.entries.sortedByDescending { it.value.attempts }.forEach { (pirate, rate) ->
            ValueRow("   " + stringResource(pirate.labelRes()), rate.text())
        }
        Hint(stringResource(R.string.cards_success_hint))
    }
}

@Composable
private fun TimingSection(cards: CardStats) {
    val rows = TIMING_CARDS.filter { (cards.timing[it]?.sum() ?: 0) > 0 }
    if (rows.isEmpty()) return
    StatCard(stringResource(R.string.cards_timing_title)) {
        rows.forEach { card ->
            val thirds = cards.timing.getValue(card)
            val total = thirds.sum().toDouble()
            ValueRow(
                stringResource(card.labelRes()),
                stringResource(
                    R.string.cards_timing_line,
                    (thirds[0] / total).percent(),
                    (thirds[1] / total).percent(),
                    (thirds[2] / total).percent(),
                ),
            )
        }
        Hint(stringResource(R.string.cards_timing_hint))
    }
}

@Composable
private fun CapturesSection(cards: CardStats, locale: Locale) {
    if (cards.captures.isEmpty()) return
    StatCard(stringResource(R.string.cards_captures_title)) {
        BonusType.entries.filter { cards.captures.containsKey(it) }.forEach { type ->
            val stat = cards.captures.getValue(type)
            ValueRow(stringResource(type.labelRes()), "${stat.count} · ${formatPoints(stat.points)}")
        }
        if (cards.playerRounds > 0) {
            ValueRow(
                stringResource(R.string.cards_bonus_share),
                (cards.playerRoundsWithBonus.toDouble() / cards.playerRounds).percent(),
            )
            ValueRow(
                stringResource(R.string.cards_bonus_average),
                "%.1f".format(locale, cards.bonusPoints.toDouble() / cards.playerRounds),
            )
        }
    }
}

@Composable
private fun WagersSection(cards: CardStats) {
    if (cards.rascalWagers.isEmpty() && cards.harryAdjustments.isEmpty()) return
    StatCard(stringResource(R.string.cards_settings_title)) {
        cards.rascalWagers.toSortedMap().forEach { (amount, wager) ->
            ValueRow(
                stringResource(R.string.cards_rascal_wager, amount),
                stringResource(
                    R.string.cards_wager_line,
                    wager.successes,
                    wager.attempts,
                    formatPoints(wager.netPoints),
                ),
            )
        }
        cards.harryAdjustments.toSortedMap().forEach { (adjustment, rate) ->
            ValueRow(stringResource(R.string.cards_harry_adjustment, formatPoints(adjustment)), rate.text())
        }
        Hint(stringResource(R.string.cards_settings_hint))
    }
}

@Composable
private fun LootsSection(cards: CardStats) {
    val loots = cards.loots
    if (loots.total == 0) return
    StatCard(stringResource(R.string.cards_loots_title)) {
        ValueRow(stringResource(R.string.cards_loots_played), loots.total.toString())
        ValueRow(
            stringResource(R.string.cards_loots_valid),
            "${loots.validated} (${(loots.validated.toDouble() / loots.total).percent()})",
        )
        loots.byPlayer.take(TOP_LOOT_PLAYERS).forEach { (name, count) -> ValueRow("   $name", count.toString()) }
    }
}

@Composable
private fun DavyJonesSection(cards: CardStats) {
    if (cards.davyJonesMonsters.isEmpty()) return
    StatCard(stringResource(R.string.cards_davy_title)) {
        cards.davyJonesMonsters.entries.sortedByDescending { it.value }.forEach { (monster, count) ->
            ValueRow(stringResource(monster.labelRes()), count.toString())
        }
        ValueRow(stringResource(R.string.cards_davy_points), formatPoints(cards.davyJonesPoints))
    }
}

@Composable
private fun KrakenSection(cards: CardStats, locale: Locale) {
    val kraken = cards.kraken
    StatCard(stringResource(R.string.cards_kraken_title)) {
        ValueRow(stringResource(R.string.cards_kraken_destroyed), kraken.destroyedTricks.toString())
        if (cards.gamesWithTricks > 0) {
            ValueRow(
                stringResource(R.string.cards_kraken_per_game),
                "%.1f".format(locale, kraken.destroyedTricks.toDouble() / cards.gamesWithTricks),
            )
        }
        ValueRow(stringResource(R.string.cards_kraken_success_with), kraken.roundsWithDestroyed.text())
        ValueRow(stringResource(R.string.cards_kraken_success_without), kraken.roundsWithout.text())
    }
}

@Composable
private fun ExpansionSection(expansion: ExpansionComparison, locale: Locale) {
    if (expansion.with.games == 0 || expansion.without.games == 0) return
    StatCard(stringResource(R.string.cards_expansion_title)) {
        ExpansionRows(stringResource(R.string.cards_expansion_with), expansion.with, locale)
        ExpansionRows(stringResource(R.string.cards_expansion_without), expansion.without, locale)
        ValueRow(stringResource(R.string.cards_expansion_wins), expansion.expansionCardWins.toString())
    }
}

@Composable
private fun ExpansionRows(title: String, side: ExpansionSide, locale: Locale) {
    Text(
        "$title · " + pluralStringResource(R.plurals.stats_games_count, side.games, side.games),
        fontWeight = FontWeight.Medium,
    )
    ValueRow("   " + stringResource(R.string.stats_bid_success), side.bidSuccess.text())
    ValueRow(
        "   " + stringResource(R.string.cards_expansion_points),
        "%.1f".format(locale, side.averagePointsPerRound),
    )
}
