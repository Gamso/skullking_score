package com.gamso.skullkingscore.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.data.backup.AutoBackupManager
import com.gamso.skullkingscore.domain.usecase.FinishGameUseCase
import com.gamso.skullkingscore.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class EndGameMenuViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val finishGame: FinishGameUseCase,
    private val autoBackup: AutoBackupManager,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            finishGame(gameId)
            // The end screen opens right away. The backup (slow on a cloud folder) goes on in the background.
            autoBackup.runInBackground()
            onDone()
        }
    }
}

/**
 * Menu of the round screens: ends the game before its last round (lack of time, a player leaves), after a
 * confirmation. The round in progress, if any, is not counted.
 *
 * @param roundInProgress a round has started and is not validated: the confirmation says it is not counted
 */
@Composable
fun EndGameMenu(roundInProgress: Boolean, onFinished: () -> Unit, viewModel: EndGameMenuViewModel = hiltViewModel()) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.scoreboard_finish)) },
            onClick = {
                menuOpen = false
                confirm = true
            },
        )
    }
    if (confirm) {
        val message = stringResource(R.string.scoreboard_finish_message)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.scoreboard_finish_title)) },
            text = {
                Text(
                    if (roundInProgress) message + " " + stringResource(R.string.finish_round_not_counted) else message,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.finish(onFinished)
                }) { Text(stringResource(R.string.scoreboard_finish)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
