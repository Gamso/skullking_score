package com.skullking.score.ui.feature.scoreboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skullking.score.R
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.ui.components.ChartSeries
import com.skullking.score.ui.components.LineChart
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.components.formatScore

/** Player colors are light: they are darkened so they read well on the chart background. */
fun Player.chartColor(): Color = lerp(Color(color), Color.Black, CHART_DARKEN)

private const val CHART_DARKEN = 0.35f

/** Cumulative score round by round, one curve per player; tapping a round shows its values in detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreCurve(scored: ScoredGame, modifier: Modifier = Modifier) {
    val rounds = scored.rounds.filter { it.isScored }
    var hidden by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val players = scored.game.players

    if (rounds.isEmpty()) {
        Text(
            stringResource(R.string.curve_empty),
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val series = players.filter { it.id !in hidden }.map { player ->
        ChartSeries(
            label = player.name,
            color = player.chartColor(),
            values = rounds.map { (it.cumulative[player.id] ?: 0).toFloat() },
        )
    }
    val resources = LocalResources.current
    val summary = scored.ranking.joinToString(". ") { entry ->
        resources.getString(R.string.curve_summary_line, entry.player.name, formatScore(entry.total), rounds.size)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            players.forEach { player ->
                FilterChip(
                    selected = player.id !in hidden,
                    onClick = { hidden = if (player.id in hidden) hidden - player.id else hidden + player.id },
                    label = { Text(player.name) },
                    leadingIcon = { PlayerAvatar(player, size = 22.dp) },
                )
            }
        }
        LineChart(
            series = series,
            description = summary,
            selectedIndex = selected,
            onSelect = { selected = it },
        )
        Text(
            stringResource(R.string.curve_axis_rounds, rounds.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val index = selected
        if (index != null && index in rounds.indices) {
            val round = rounds[index]
            Text(
                stringResource(R.string.curve_round_detail, round.round.number, round.round.cardsDealt),
                style = MaterialTheme.typography.titleMedium,
            )
            players.sortedByDescending { round.cumulative[it.id] ?: 0.0 }.forEach { player ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(player.name)
                    Text(
                        stringResource(
                            R.string.curve_round_line,
                            formatPoints(round.breakdowns[player.id]?.total ?: 0.0),
                            formatScore(round.cumulative[player.id] ?: 0.0),
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.curve_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
