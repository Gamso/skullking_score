package com.gamso.skullkingscore.ui.feature.bids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.ScoredGame
import com.gamso.skullkingscore.domain.model.ScoredRound
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.scoring.GameScoreCalculator
import com.gamso.skullkingscore.domain.undo.UndoAction
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.undo.UndoKind
import com.gamso.skullkingscore.domain.undo.UndoState
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.ui.components.ActionBar
import com.gamso.skullkingscore.ui.components.DealerLine
import com.gamso.skullkingscore.ui.components.DeltaStepper
import com.gamso.skullkingscore.ui.components.KeepScreenOn
import com.gamso.skullkingscore.ui.components.PlayerAvatar
import com.gamso.skullkingscore.ui.components.SkullScaffold
import com.gamso.skullkingscore.ui.components.UndoNotice
import com.gamso.skullkingscore.ui.components.UndoNotices
import com.gamso.skullkingscore.ui.components.UndoRedoActions
import com.gamso.skullkingscore.ui.navigation.Routes
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

data class BidsUiState(val scored: ScoredGame, val round: ScoredRound) {
    val players: List<Player> get() = scored.game.players
    val cards: Int get() = round.round.cardsDealt
    val totalBid: Int get() = round.details.entries.sumOf { it.effectiveBid }
}

@HiltViewModel
class BidsViewModel @Inject constructor(
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

    val state: StateFlow<BidsUiState?> = games.observeGame(gameId)
        .filterNotNull()
        .map { details ->
            val scored = GameScoreCalculator.calculate(details)
            scored.round(roundId)?.let { BidsUiState(scored, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun nameOf(playerId: Long) = state.value?.players?.firstOrNull { it.id == playerId }?.name.orEmpty()

    /**
     * Adds [step] to the stored bid, inside the update: a second tap made before the screen is refreshed still
     * counts. The undo entry is built from the entry read in that update, so each effective tap records exactly
     * one, and a tap that changes nothing records none.
     */
    fun stepBid(playerId: Long, step: Int) {
        val cards = state.value?.cards ?: return
        viewModelScope.launch {
            val apply: suspend (Int, Int?) -> Unit = { newBid, newFinal ->
                edit.updateEntry(gameId, roundId, playerId) { it.copy(bid = newBid, finalBid = newFinal) }
            }
            val (before, after) = edit.updateEntry(gameId, roundId, playerId) {
                it.copy(bid = (it.bid + step).coerceIn(0, cards), finalBid = null)
            } ?: return@launch
            if (before.bid == after.bid && before.finalBid == after.finalBid) return@launch
            history.record(
                gameId,
                UndoAction(UndoKind.BID, nameOf(playerId), {
                    apply(before.bid, before.finalBid)
                }, { apply(after.bid, null) }),
            )
        }
    }

    fun setCannonball(playerId: Long, enabled: Boolean) {
        val before = state.value?.round?.details?.entry(playerId)?.cannonball ?: return
        viewModelScope.launch {
            val apply: suspend (Boolean) -> Unit = { value ->
                edit.updateEntry(gameId, roundId, playerId) { it.copy(cannonball = value) }
            }
            apply(enabled)
            history.record(
                gameId,
                UndoAction(UndoKind.CANNONBALL, nameOf(playerId), { apply(before) }, { apply(enabled) }),
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

    fun confirm(onDone: () -> Unit) {
        viewModelScope.launch {
            history.clear(gameId)
            edit.setPhase(gameId, roundId, RoundPhase.PLAYING)
            onDone()
        }
    }
}

@Composable
fun BidsScreen(
    onBack: () -> Unit,
    onScoreboard: () -> Unit,
    onHelp: () -> Unit,
    onValidated: () -> Unit,
    viewModel: BidsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()
    val current = state
    val snackbar = remember { SnackbarHostState() }
    KeepScreenOn()
    UndoNotices(viewModel.notices, snackbar)

    SkullScaffold(
        title = if (current == null) {
            stringResource(R.string.app_name)
        } else {
            stringResource(R.string.round_title_plain, current.round.round.number)
        },
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            UndoRedoActions(undoState, onUndo = viewModel::undo, onRedo = viewModel::redo)
            IconButton(onClick = onScoreboard) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.scoreboard))
            }
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help)) }
        },
        bottomBar = {
            if (current != null) {
                ActionBar(
                    primaryText = stringResource(R.string.bids_validate),
                    onPrimary = { viewModel.confirm(onValidated) },
                )
            }
        },
    ) { padding ->
        if (current == null) return@SkullScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                DealerLine(current.scored.game, current.round.round.number, onChoose = viewModel::setDealer)
            }
            // All the players in one card, one thin line each: the whole table fits on the screen.
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    current.players.forEachIndexed { index, player ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BidRow(current, player, viewModel)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bids_total, current.totalBid, current.cards),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One line per player: name on the left, bid adjusted with − / + on the right (fits every round size). */
@Composable
private fun BidRow(state: BidsUiState, player: Player, viewModel: BidsViewModel) {
    val entry = state.round.details.entry(player.id)
    val selected = entry?.bid ?: 0
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(player, size = 24.dp)
            Text(
                player.name,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DeltaStepper(
                value = selected,
                onStep = { viewModel.stepBid(player.id, it) },
                range = 0..state.cards,
            )
        }
        // Rascal + Cannonball mode: each player can wager "all or nothing" along with their bid.
        if (state.scored.game.mode == ScoringMode.CANNONBALL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "💣 " + stringResource(R.string.results_cannonball),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = entry?.cannonball == true,
                    onCheckedChange = { viewModel.setCannonball(player.id, it) },
                )
            }
        }
    }
}
