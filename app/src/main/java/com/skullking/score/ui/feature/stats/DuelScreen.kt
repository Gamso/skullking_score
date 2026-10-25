package com.skullking.score.ui.feature.stats

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.ScoringMode
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.PlayerRepository
import com.skullking.score.domain.stats.DuelCalculator
import com.skullking.score.domain.stats.DuelStats
import com.skullking.score.domain.stats.RoundRecord
import com.skullking.score.domain.stats.StatsCalculator
import com.skullking.score.domain.stats.StatsFilter
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DuelUiState(
    val mode: ScoringMode = ScoringMode.CLASSIC,
    val players: List<Player> = emptyList(),
    val firstId: Long? = null,
    val secondId: Long? = null,
    val duel: DuelStats? = null,
)

private data class DuelSelection(
    val mode: ScoringMode = ScoringMode.CLASSIC,
    val first: Long? = null,
    val second: Long? = null,
)

@HiltViewModel
class DuelViewModel @Inject constructor(games: GameRepository, playerRepository: PlayerRepository) : ViewModel() {

    private val selection = MutableStateFlow(DuelSelection())

    val state: StateFlow<DuelUiState> = combine(
        games.observeAllGames(),
        playerRepository.observePlayers(),
        selection,
    ) { all, players, current ->
        val usable = players.filter { !it.archived || it.id == current.first || it.id == current.second }
        val first = usable.firstOrNull { it.id == current.first }
        val second = usable.firstOrNull { it.id == current.second }
        val duel = if (first != null && second != null && first.id != second.id) {
            DuelCalculator.compare(StatsCalculator.select(all, StatsFilter(current.mode)), first, second)
        } else {
            null
        }
        DuelUiState(current.mode, usable, current.first, current.second, duel)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuelUiState())

    fun setMode(mode: ScoringMode) = selection.update { it.copy(mode = mode) }

    fun pickFirst(player: Player) = selection.update {
        it.copy(first = player.id, second = it.second.takeIf { id -> id != player.id })
    }

    fun pickSecond(player: Player) = selection.update {
        it.copy(second = player.id, first = it.first.takeIf { id -> id != player.id })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuelScreen(onBack: () -> Unit, viewModel: DuelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SkullScaffold(title = stringResource(R.string.duel_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoringMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }
            SectionTitle(stringResource(R.string.duel_first))
            PlayerChips(state.players, state.firstId, viewModel::pickFirst)
            SectionTitle(stringResource(R.string.duel_second))
            PlayerChips(state.players, state.secondId, viewModel::pickSecond)

            val duel = state.duel
            when {
                duel == null -> Text(
                    stringResource(R.string.duel_choose),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                !duel.hasEnoughGames -> Text(
                    stringResource(R.string.duel_not_enough),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> DuelResult(duel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChips(players: List<Player>, selectedId: Long?, onPick: (Player) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEach { player ->
            FilterChip(
                selected = player.id == selectedId,
                onClick = { onPick(player) },
                label = { Text(player.name) },
                leadingIcon = { PlayerAvatar(player, size = 22.dp) },
            )
        }
    }
}

@Composable
private fun DuelResult(duel: DuelStats) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.duel_common_games, duel.commonGames),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreSide(duel.first, duel.firstWins)
                Text(stringResource(R.string.duel_versus), style = MaterialTheme.typography.titleMedium)
                ScoreSide(duel.second, duel.secondWins)
            }
            if (duel.ties > 0) Text(stringResource(R.string.duel_ties, duel.ties))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.duel_average_gap, duel.first.name))
                Text("%+.1f".format(locale, duel.averageGap), fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.duel_biggest_gap))
                Text(duel.biggestGap.toString(), fontWeight = FontWeight.Medium)
            }
            SectionTitle(stringResource(R.string.stats_records_title))
            RecordLine(R.string.stats_record_best_round, duel.first.name, duel.firstBestRound)
            RecordLine(R.string.stats_record_best_round, duel.second.name, duel.secondBestRound)
            RecordLine(R.string.stats_record_worst_round, duel.first.name, duel.firstWorstRound)
            RecordLine(R.string.stats_record_worst_round, duel.second.name, duel.secondWorstRound)
        }
    }
}

@Composable
private fun ScoreSide(player: Player, wins: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerAvatar(player)
        Text(player.name, style = MaterialTheme.typography.bodyMedium)
        Text(wins.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordLine(labelRes: Int, name: String, record: RoundRecord?) {
    val value = if (record == null) {
        "–"
    } else {
        val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(record.gameStartedAt))
        stringResource(R.string.stats_record_round, formatPoints(record.points), record.roundNumber, date)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("${stringResource(labelRes)} · $name", style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
    }
}
