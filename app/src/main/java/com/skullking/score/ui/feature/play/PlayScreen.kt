package com.skullking.score.ui.feature.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.skullking.score.domain.model.RoundDetails
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
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.TrickDialog
import com.skullking.score.ui.components.TrickEvents
import com.skullking.score.ui.components.UndoNotice
import com.skullking.score.ui.components.UndoNotices
import com.skullking.score.ui.components.UndoRedoActions
import com.skullking.score.ui.components.WidthClass
import com.skullking.score.ui.components.message
import com.skullking.score.ui.components.rememberWidthClass
import com.skullking.score.ui.components.trickEventLines
import com.skullking.score.ui.components.trickLabel
import com.skullking.score.ui.emoji
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.navigation.Routes
import com.skullking.score.ui.theme.ScoreNegative
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

    /**
     * Records trick [number] with the events played in it. The events go first: recording the trick
     * then gives its winner to the Loots of that trick.
     */
    fun recordTrick(number: Int, draft: TrickDraft, events: TrickEvents = TrickEvents()) {
        val before = state.value?.round?.details?.tricks?.firstOrNull { it.number == number }?.toDraft()
        viewModelScope.launch {
            events.lootPlayerIds.forEach { edit.addLoot(gameId, roundId, it, null, number) }
            events.chests.forEach { (playerId, creatures) ->
                edit.addDavyJones(gameId, roundId, playerId, creatures, number)
            }
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
    onRoundValidated: () -> Unit,
    viewModel: PlayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()
    val current = state
    var confirmWarnings by remember { mutableStateOf(false) }
    // Number of the trick open in the entry dialog (new trick or correction); null = closed.
    var editing by remember { mutableStateOf<Int?>(null) }
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
            val details = current?.round?.details
            // The main button enters the next trick, then validates the round once every trick is entered.
            if (current != null && details != null && details.tricks.size < details.round.cardsDealt) {
                ActionBar(
                    primaryText = stringResource(R.string.play_enter_trick, details.currentTrickNumber),
                    onPrimary = { editing = details.currentTrickNumber },
                )
            } else {
                ActionBar(
                    primaryText = stringResource(R.string.play_validate_round),
                    primaryEnabled = current != null,
                    onPrimary = {
                        if (current?.warnings.isNullOrEmpty()) {
                            viewModel.validateRound(onRoundValidated)
                        } else {
                            confirmWarnings = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (current == null) return@SkullScaffold
        PlayContent(current, viewModel, onEditTrick = { editing = it }, Modifier.padding(padding))
    }

    val details = current?.round?.details
    val number = editing
    if (current != null && details != null && number != null) {
        val game = current.scored.game
        val existing = details.tricks.firstOrNull { it.number == number }
        TrickDialog(
            trickNumber = number,
            players = game.players,
            expansion = game.expansionCards,
            initial = existing?.toDraft(),
            recordedEvents = trickEventLines(details, number, game.players.associate { it.id to it.name }),
            onConfirm = { draft, events ->
                viewModel.recordTrick(number, draft, events)
                editing = null
            },
            onDismiss = { editing = null },
        )
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
private fun PlayContent(
    state: PlayUiState,
    viewModel: PlayViewModel,
    onEditTrick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.scored.game
    val details = state.round.details
    val totalTricks = details.round.cardsDealt
    val allRecorded = details.tricks.size >= totalTricks
    val hasEvents = details.loots.isNotEmpty() || details.captures.isNotEmpty()

    val wide = rememberWidthClass() != WidthClass.COMPACT
    val reminder: @Composable ColumnScope.() -> Unit = {
        DealerLine(game, details.round.number, onChoose = viewModel::setDealer)
        BidsReminder(state)
    }
    val tricksAndEvents: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (allRecorded) {
                    stringResource(R.string.play_all_tricks_done)
                } else {
                    stringResource(R.string.play_trick_progress, details.currentTrickNumber, totalTricks)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (details.tricks.isNotEmpty()) {
                TextButton(onClick = viewModel::deleteLastTrick) {
                    Text(stringResource(R.string.play_undo_last_trick))
                }
            }
        }
        if (details.tricks.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                details.tricks.forEach { trick ->
                    SuggestionChip(
                        onClick = { onEditTrick(trick.number) },
                        label = { Text(trickLabel(trick, game.players.associate { it.id to it.name })) },
                    )
                }
            }
        }

        // Loot and Davy Jones are entered in the trick dialog: only the log stays here.
        if (hasEvents) {
            SectionTitle(stringResource(R.string.play_live_events))
            LiveEventsPanel(
                game = game,
                round = details,
                onAddLoot = viewModel::addLoot,
                onAddDavyJones = viewModel::addDavyJones,
                onCancelLoot = viewModel::cancelLoot,
                onCancelDavyJones = viewModel::cancelDavyJones,
                askWinner = false,
                showButtons = false,
            )
        }
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = reminder,
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = tricksAndEvents,
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reminder()
            tricksAndEvents()
        }
    }
}

/**
 * Bids of the round, collapsible. The header recalls the total bid against the number of tricks; each
 * player shows "tricks / bid", colored once the bid is made or exceeded.
 */
@Composable
private fun BidsReminder(state: PlayUiState) {
    val game = state.scored.game
    val details = state.round.details
    var expanded by rememberSaveable { mutableStateOf(true) }
    val totalBid = details.entries.sumOf { it.effectiveBid }
    val cards = details.round.cardsDealt
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.play_bids_reminder),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.play_bids_summary, totalBid, cards) + bidBalance(totalBid - cards),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        }
        AnimatedVisibility(visible = expanded) {
            // Two players per line: the whole table fits in a few lines.
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                game.players.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { player ->
                            val entry = details.entry(player.id)
                            BidCell(
                                player = player,
                                bid = entry?.effectiveBid ?: 0,
                                tricks = entry?.tricks ?: 0,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun bidBalance(delta: Int): String = when {
    delta > 0 -> " · " + stringResource(R.string.play_bids_over, delta)
    delta < 0 -> " · " + stringResource(R.string.play_bids_under, -delta)
    else -> ""
}

@Composable
private fun BidCell(player: Player, bid: Int, tricks: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player, size = 24.dp)
        Text(
            player.name,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$tricks / $bid",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                tricks > bid -> ScoreNegative
                tricks == bid -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
