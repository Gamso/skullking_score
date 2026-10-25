package com.skullking.score.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.nextStep
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.WidthClass
import com.skullking.score.ui.components.rememberWidthClass
import com.skullking.score.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(games: GameRepository) : ViewModel() {
    val inProgress: StateFlow<GameDetails?> =
        games.observeInProgressGame().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onResume: (route: String) -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onPlayers: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()

    SkullScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help)) }
        },
    ) { padding ->
        val wide = rememberWidthClass() != WidthClass.COMPACT
        val current = inProgress
        val hero: @Composable ColumnScope.() -> Unit = {
            Text("🏴‍☠️", style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current != null) {
                val scored = current.rounds.count { it.round.phase == RoundPhase.SCORED }
                Button(
                    onClick = { onResume(Routes.forStep(current.game.id, current.nextStep())) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.home_resume),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.home_resume_detail, current.game.players.size, scored),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        val actions: @Composable ColumnScope.() -> Unit = {
            Button(
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) { Text(stringResource(R.string.home_new_game), style = MaterialTheme.typography.titleMedium) }

            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.home_history))
            }
            OutlinedButton(onClick = onStats, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.home_stats))
            }
            OutlinedButton(onClick = onPlayers, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.home_players))
            }
        }
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = hero,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = actions,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                hero()
                actions()
            }
        }
    }
}
