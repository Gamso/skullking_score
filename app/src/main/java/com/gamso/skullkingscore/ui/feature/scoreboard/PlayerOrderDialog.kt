package com.gamso.skullkingscore.ui.feature.scoreboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.usecase.ReorderPlayersUseCase
import com.gamso.skullkingscore.ui.components.PlayerAvatar
import com.gamso.skullkingscore.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reorderPlayers: ReorderPlayersUseCase,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))

    fun reorder(playerIds: List<Long>) {
        viewModelScope.launch { reorderPlayers(gameId, playerIds) }
    }
}

/** Moves a player one seat up or down at the table, when the order was wrong at the start of the game. */
@Composable
fun PlayerOrderDialog(
    players: List<Player>,
    onDismiss: () -> Unit,
    viewModel: PlayerOrderViewModel = hiltViewModel(),
) {
    // Kept as text ("3,1,2"), which survives a rotation.
    var order by rememberSaveable { mutableStateOf(players.joinToString(",") { it.id.toString() }) }
    val byId = players.associateBy { it.id }
    val ids = order.split(",").mapNotNull { it.toLongOrNull() }.filter { it in byId }
    val move = { index: Int, by: Int ->
        order = ids.toMutableList().apply { add(index + by, removeAt(index)) }.joinToString(",")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.order_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ids.forEachIndexed { index, id ->
                    val player = byId.getValue(id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(player, size = 24.dp)
                        Text(player.name, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.order_up, player.name),
                            )
                        }
                        IconButton(onClick = { move(index, 1) }, enabled = index < ids.lastIndex) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.order_down, player.name),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.reorder(ids)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
