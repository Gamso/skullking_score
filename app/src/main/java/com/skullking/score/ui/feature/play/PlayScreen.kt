package com.skullking.score.ui.feature.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.RoundWarning
import com.skullking.score.core.SeaMonster
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.model.TrickDraft
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.undo.UndoAction
import com.skullking.score.domain.undo.UndoHistory
import com.skullking.score.domain.undo.UndoKind
import com.skullking.score.domain.undo.UndoState
import com.skullking.score.domain.usecase.EditRoundUseCase
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.DealerLine
import com.skullking.score.ui.components.KeepScreenOn
import com.skullking.score.ui.components.LiveEventsPanel
import com.skullking.score.ui.components.PlayerLabel
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.TrickDialog
import com.skullking.score.ui.components.UndoNotice
import com.skullking.score.ui.components.UndoNotices
import com.skullking.score.ui.components.UndoRedoActions
import com.skullking.score.ui.components.WidthClass
import com.skullking.score.ui.components.message
import com.skullking.score.ui.components.rememberWidthClass
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun Trick.toDraft() = TrickDraft(winnerId, bonus, rascalWager, bidAdjustment, winningCard, winningPirate)

data class PlayUiState(val scored: ScoredGame, val round: ScoredRound, val warnings: List<RoundWarning>)

@HiltViewModel
class PlayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val edit: EditRoundUseCase,
    private val history: UndoHistory,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))
    private val roundId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_ROUND_ID))

    val undoState: StateFlow<UndoState> = history.observe(gameId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UndoState())
    private val _notices = MutableSharedFlow<UndoNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<UndoNotice> = _notices.asSharedFlow()

    init {
        history.clear(gameId)
    }

    val state: StateFlow<PlayUiState?> = games.observeGame(gameId)
        .filterNotNull()
        .map { details ->
            val scored = GameScoreCalculator.calculate(details)
            scored.round(roundId)?.let {
                PlayUiState(scored, it, GameScoreCalculator.warnings(scored.game, it.details))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val currentTrick: Int? get() = state.value?.round?.details?.currentTrickNumber

    /** During play, the winner of a Loot is unknown: it is filled in when the trick is entered. */
    fun addLoot(lootPlayerId: Long, trickWinnerId: Long?) {
        viewModelScope.launch { edit.addLoot(gameId, roundId, lootPlayerId, trickWinnerId, currentTrick) }
    }

    fun addDavyJones(playerId: Long, creatures: Set<SeaMonster>) {
        viewModelScope.launch { edit.addDavyJones(gameId, roundId, playerId, creatures, currentTrick) }
    }

    fun cancelLoot(id: Long) {
        viewModelScope.launch { edit.cancelLoot(gameId, id) }
    }

    fun cancelDavyJones(id: Long) {
        viewModelScope.launch { edit.cancelDavyJones(gameId, id) }
    }

    fun recordTrick(number: Int, draft: TrickDraft) {
        val before = state.value?.round?.details?.tricks?.firstOrNull { it.number == number }?.toDraft()
        viewModelScope.launch {
            edit.recordTrick(gameId, roundId, number, draft)
            history.record(
                gameId,
                UndoAction(
                    UndoKind.TRICK,
                    number.toString(),
                    undo = {
                        if (before == null) {
                            edit.deleteLastTrick(gameId, roundId)
                        } else {
                            edit.recordTrick(gameId, roundId, number, before)
                        }
                    },
                    redo = { edit.recordTrick(gameId, roundId, number, draft) },
                ),
            )
        }
    }

    fun undo() {
        viewModelScope.launch { history.undo(gameId)?.let { _notices.emit(UndoNotice(it, redone = false)) } }
    }

    fun redo() {
        viewModelScope.launch { history.redo(gameId)?.let { _notices.emit(UndoNotice(it, redone = true)) } }
    }

    fun setDealer(player: Player) {
        val game = state.value?.scored?.game ?: return
        val number = state.value?.round?.round?.number ?: return
        val index = game.players.indexOfFirst { it.id == player.id }
        viewModelScope.launch {
            games.setFirstDealerSeat(gameId, Math.floorMod(index - (number - 1), game.players.size))
        }
    }

    /** Validates the round as is: the tricks entered live have already filled in the results. */
    fun validateRound(onDone: () -> Unit) {
        viewModelScope.launch {
            history.clear(gameId)
            edit.setPhase(gameId, roundId, RoundPhase.SCORED)
            onDone()
        }
    }

    fun deleteLastTrick() {
        val last = state.value?.round?.details?.tricks?.lastOrNull() ?: return
        val draft = last.toDraft()
        viewModelScope.launch {
            edit.deleteLastTrick(gameId, roundId)
            history.record(
                gameId,
                UndoAction(
                    UndoKind.TRICK_DELETED,
                    last.number.toString(),
                    undo = { edit.recordTrick(gameId, roundId, last.number, draft) },
                    redo = { edit.deleteLastTrick(gameId, roundId) },
                ),
            )
        }
    }
}

@Composable
fun PlayScreen(
    onBack: () -> Unit,
    onScoreboard: () -> Unit,
    onHelp: () -> Unit,
    onEnterResults: () -> Unit,
    onRoundValidated: () -> Unit,
    viewModel: PlayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()
    val current = state
    var confirmWarnings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    KeepScreenOn()
    UndoNotices(viewModel.notices, snackbar)

    SkullScaffold(
        title = if (current == null) {
            stringResource(R.string.app_name)
        } else {
            stringResource(R.string.round_title_cards, current.round.round.number, current.round.round.cardsDealt)
        },
        onBack = onBack,
        maxContentWidth = Dp.Unspecified,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            UndoRedoActions(undoState, onUndo = viewModel::undo, onRedo = viewModel::redo)
            IconButton(onClick = onScoreboard) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.scoreboard))
            }
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help)) }
        },
        bottomBar = {
            if (current != null && current.round.details.tricks.isNotEmpty()) {
                // The tricks were entered live: no need to enter the results again.
                ActionBar(
                    primaryText = stringResource(R.string.play_validate_round),
                    onPrimary = {
                        if (current.warnings.isEmpty()) {
                            viewModel.validateRound(onRoundValidated)
                        } else {
                            confirmWarnings = true
                        }
                    },
                )
            } else {
                ActionBar(
                    primaryText = stringResource(R.string.play_enter_results),
                    primaryEnabled = current != null,
                    onPrimary = onEnterResults,
                )
            }
        },
    ) { padding ->
        if (current == null) return@SkullScaffold
        PlayContent(current, viewModel, Modifier.padding(padding))
    }

    if (confirmWarnings && current != null) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text(stringResource(R.string.play_warnings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.warnings.forEach { Text(it.message(), style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    viewModel.validateRound(onRoundValidated)
                }) { Text(stringResource(R.string.play_validate_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWarnings = false }) { Text(stringResource(R.string.play_fix)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayContent(state: PlayUiState, viewModel: PlayViewModel, modifier: Modifier = Modifier) {
    val game = state.scored.game
    val details = state.round.details
    val totalTricks = details.round.cardsDealt
    val allRecorded = details.tricks.size >= totalTricks
    // Number of the trick open in the entry dialog (new trick or correction); null = closed.
    var editing by remember { mutableStateOf<Int?>(null) }

    val wide = rememberWidthClass() != WidthClass.COMPACT
    val reminder: @Composable ColumnScope.() -> Unit = {
        DealerLine(game, details.round.number, onChoose = viewModel::setDealer)
        SectionTitle(stringResource(R.string.play_bids_reminder))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                game.players.forEach { player ->
                    val entry = details.entry(player.id)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PlayerLabel(player)
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(
                                        R.string.play_bid_tricks_chip,
                                        entry?.effectiveBid ?: 0,
                                        entry?.tricks ?: 0,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    val tricksAndEvents: @Composable ColumnScope.() -> Unit = {
        SectionTitle(stringResource(R.string.play_tricks_title))
        Text(
            text = if (allRecorded) {
                stringResource(R.string.play_all_tricks_done)
            } else {
                stringResource(R.string.play_trick_progress, details.currentTrickNumber, totalTricks)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (details.tricks.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                details.tricks.forEach { trick ->
                    SuggestionChip(
                        onClick = { editing = trick.number },
                        label = { Text(trickLabel(trick, game.players.associate { it.id to it.name })) },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        Button(
            onClick = { editing = details.currentTrickNumber },
            enabled = !allRecorded,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(stringResource(R.string.play_enter_trick, details.currentTrickNumber)) }
        if (details.tricks.isNotEmpty()) {
            OutlinedButton(onClick = viewModel::deleteLastTrick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.play_undo_last_trick))
            }
        }

        SectionTitle(stringResource(R.string.play_live_events))
        LiveEventsPanel(
            game = game,
            round = details,
            onAddLoot = viewModel::addLoot,
            onAddDavyJones = viewModel::addDavyJones,
            onCancelLoot = viewModel::cancelLoot,
            onCancelDavyJones = viewModel::cancelDavyJones,
            askWinner = false,
        )
    }
    if (wide) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = reminder,
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = tricksAndEvents,
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            reminder()
            tricksAndEvents()
        }
    }

    editing?.let { number ->
        val existing = details.tricks.firstOrNull { it.number == number }
        TrickDialog(
            trickNumber = number,
            players = game.players,
            expansion = game.expansionCards,
            initial = existing?.let {
                TrickDraft(it.winnerId, it.bonus, it.rascalWager, it.bidAdjustment, it.winningCard, it.winningPirate)
            },
            onConfirm = { draft ->
                viewModel.recordTrick(number, draft)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun trickLabel(trick: Trick, names: Map<Long, String>): String {
    val winner = trick.winnerId?.let { names[it] } ?: stringResource(R.string.trick_destroyed_short)
    val with = trick.winningPirate?.let { stringResource(it.labelRes()) }
        ?: trick.winningCard?.let { stringResource(it.labelRes()) }
    return listOfNotNull("${trick.number}", winner, with).joinToString(" · ")
}
