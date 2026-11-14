package com.gamso.skullkingscore.ui.feature.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.repository.PlayerRepository
import com.gamso.skullkingscore.ui.components.ActionBar
import com.gamso.skullkingscore.ui.components.PlayerEditDialog
import com.gamso.skullkingscore.ui.components.PlayerLabel
import com.gamso.skullkingscore.ui.components.SkullScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayersViewModel @Inject constructor(private val players: PlayerRepository) : ViewModel() {
    val state: StateFlow<List<Player>> =
        players.observePlayers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { players.createPlayer(name) }
    }

    fun update(player: Player) {
        viewModelScope.launch { players.updatePlayer(player) }
    }
}

@Composable
fun PlayersScreen(onBack: () -> Unit, viewModel: PlayersViewModel = hiltViewModel()) {
    val players by viewModel.state.collectAsStateWithLifecycle()
    // Id of the profile being renamed, saveable: the dialog survives a rotation.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    SkullScaffold(
        title = stringResource(R.string.home_players),
        onBack = onBack,
        bottomBar = { ActionBar(stringResource(R.string.player_new), onPrimary = { creating = true }) },
    ) { padding ->
        if (players.isEmpty()) {
            Box(Modifier.padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.players_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            items(players, key = { it.id }) { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = player.id }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PlayerLabel(
                        player,
                        subtitle = if (player.archived) stringResource(R.string.player_archived) else null,
                    )
                    TextButton(onClick = { viewModel.update(player.copy(archived = !player.archived)) }) {
                        Text(stringResource(if (player.archived) R.string.player_restore else R.string.player_archive))
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        PlayerEditDialog(
            initial = null,
            onConfirm = { name ->
                viewModel.create(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    players.firstOrNull { it.id == editing }?.let { player ->
        PlayerEditDialog(
            initial = player,
            onConfirm = { name ->
                viewModel.update(player.copy(name = name))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
