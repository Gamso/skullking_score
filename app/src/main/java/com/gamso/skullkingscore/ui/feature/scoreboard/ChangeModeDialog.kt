package com.gamso.skullkingscore.ui.feature.scoreboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.usecase.ChangeModeUseCase
import com.gamso.skullkingscore.ui.descriptionRes
import com.gamso.skullkingscore.ui.labelRes
import com.gamso.skullkingscore.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ChangeModeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val changeMode: ChangeModeUseCase,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))

    fun change(mode: ScoringMode) {
        viewModelScope.launch { changeMode(gameId, mode) }
    }
}

/**
 * Changes the scoring mode of the game in progress, when the wrong one was picked at the start. The scores of the
 * rounds already played are recomputed with the new mode.
 */
@Composable
fun ChangeModeDialog(current: ScoringMode, onDismiss: () -> Unit, viewModel: ChangeModeViewModel = hiltViewModel()) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_change_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.mode_change_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScoringMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = mode },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mode, onClick = { selected = mode })
                        Column {
                            Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(mode.descriptionRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.change(selected)
                    onDismiss()
                },
                enabled = selected != current,
            ) { Text(stringResource(R.string.mode_change_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
