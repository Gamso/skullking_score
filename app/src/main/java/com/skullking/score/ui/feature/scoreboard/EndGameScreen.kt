package com.skullking.score.ui.feature.scoreboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skullking.score.R
import com.skullking.score.domain.model.RankedPlayer
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SkullScaffold

private fun medalFor(rank: Int): String = when (rank) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> ""
}

/** End of game: podium (ties share their rank) then the full ranking. */
@Composable
fun EndGameScreen(
    onBack: () -> Unit,
    onScoreboard: () -> Unit,
    onReplay: () -> Unit,
    onShare: () -> Unit,
    viewModel: ScoreboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scored = state

    SkullScaffold(
        title = stringResource(R.string.end_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_action))
            }
        },
        bottomBar = {
            ActionBar(
                primaryText = stringResource(R.string.replay),
                onPrimary = onReplay,
                secondary = {
                    OutlinedButton(onClick = onScoreboard, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.end_see_scoreboard))
                    }
                },
            )
        },
    ) { padding ->
        if (scored == null) return@SkullScaffold
        val ranking = scored.ranking
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val winners = ranking.filter { it.rank == 1 }
            Text(
                stringResource(R.string.end_winner, winners.joinToString(" & ") { it.player.name }),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            ranking.forEach { RankRow(it) }
        }
    }
}

@Composable
private fun RankRow(entry: RankedPlayer) {
    val podium = entry.rank <= 3
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.rank ==
                1
            ) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (entry.rank ==
                1
            ) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (podium) 16.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = medalFor(entry.rank).ifEmpty { "${entry.rank}." },
                style = if (podium) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            PlayerAvatar(entry.player, size = if (podium) 48.dp else 36.dp)
            Text(
                entry.player.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                style = if (podium) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            )
            Text(
                entry.total.toString(),
                style = if (podium) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
