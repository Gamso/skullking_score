package com.skullking.score.ui.feature.bids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.ScoringMode
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound
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
import com.skullking.score.ui.components.NumberSelector
import com.skullking.score.ui.components.PlayerLabel
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.UndoNotice
import com.skullking.score.ui.components.UndoNotices
import com.skullking.score.ui.components.UndoRedoActions
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

data class BidsUiState(val scored: ScoredGame, val round: ScoredRound) {
    val players: List<Player> get() = scored.game.players
    val cards: Int get() = round.round.cardsDealt
    val totalBid: Int get() = round.details.entries.sumOf { it.effectiveBid }

    /** Cumul de chaque joueur avant cette manche. */
    fun scoreBefore(playerId: Long): Int {
        val index = scored.rounds.indexOfFirst { it.round.id == round.round.id }
        return if (index <= 0) 0 else scored.rounds[index - 1].cumulative[playerId] ?: 0
    }
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

    fun setBid(playerId: Long, bid: Int) {
        val before = state.value?.round?.details?.entry(playerId) ?: return
        if (before.bid == bid && before.finalBid == null) return
        viewModelScope.launch {
            val apply: suspend (Int, Int?) -> Unit = { newBid, newFinal ->
                edit.updateEntry(gameId, roundId, playerId) { it.copy(bid = newBid, finalBid = newFinal) }
            }
            apply(bid, null)
            history.record(
                gameId,
                UndoAction(UndoKind.BID, nameOf(playerId), {
                    apply(before.bid, before.finalBid)
                }, { apply(bid, null) }),
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
            stringResource(R.string.round_title_cards, current.round.round.number, current.cards)
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DealerLine(current.scored.game, current.round.round.number, onChoose = viewModel::setDealer)
            }
            items(current.players, key = { it.id }) { player -> BidCard(current, player, viewModel) }
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

@Composable
private fun BidCard(state: BidsUiState, player: Player, viewModel: BidsViewModel) {
    val entry = state.round.details.entry(player.id)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Le score cumulé sous chaque nom aide à décider de l'annonce.
            PlayerLabel(player, subtitle = stringResource(R.string.bids_score_so_far, state.scoreBefore(player.id)))
            NumberSelector(
                selected = entry?.bid ?: 0,
                max = state.cards,
                onSelect = { viewModel.setBid(player.id, it) },
            )
            // Mode Rascal + Boulet de canon : chaque joueur peut miser « tout ou rien » en même temps que son annonce.
            if (state.scored.game.mode == ScoringMode.CANNONBALL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "💣 " + stringResource(R.string.results_cannonball),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = entry?.cannonball == true,
                        onCheckedChange = { viewModel.setCannonball(player.id, it) },
                    )
                }
            }
        }
    }
}
