package com.skullking.score.ui.feature.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.DeckRules
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.usecase.StartRoundUseCase
import com.skullking.score.domain.usecase.suggestedCards
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.DealerLine
import com.skullking.score.ui.components.KeepScreenOn
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.Stepper
import com.skullking.score.ui.components.WarningCard
import com.skullking.score.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrepareUiState(
    val details: GameDetails,
    val roundNumber: Int,
    val isExtra: Boolean,
    val cards: Int,
    val maxCards: Int,
    val deckSize: Int,
) {
    val needed: Int get() = cards * details.game.players.size
    val exceedsDeck: Boolean get() = needed > deckSize
}

@HiltViewModel
class PrepareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val startRound: StartRoundUseCase,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))
    private val chosenCards = MutableStateFlow<Int?>(null)
    private var starting = false

    val state: StateFlow<PrepareUiState?> = combine(games.observeGame(gameId).filterNotNull(), chosenCards) {
            details,
            chosen,
        ->
        val number = details.rounds.size + 1
        PrepareUiState(
            details = details,
            roundNumber = number,
            isExtra = number > DeckRules.PLANNED_ROUNDS,
            cards = chosen ?: details.suggestedCards(),
            maxCards = DeckRules.MAX_CARDS_PER_ROUND,
            deckSize = DeckRules.deckSize(details.game.expansionCards),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDealer(player: Player) {
        val game = state.value?.details?.game ?: return
        val number = state.value?.roundNumber ?: return
        val index = game.players.indexOfFirst { it.id == player.id }
        viewModelScope.launch {
            games.setFirstDealerSeat(gameId, Math.floorMod(index - (number - 1), game.players.size))
        }
    }

    fun setCards(cards: Int) {
        chosenCards.value = cards.coerceIn(1, DeckRules.MAX_CARDS_PER_ROUND)
    }

    fun start(onStarted: (roundId: Long) -> Unit) {
        val current = state.value ?: return
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                onStarted(startRound(gameId, current.cards))
            } finally {
                starting = false
            }
        }
    }
}

@Composable
fun PrepareScreen(
    onBack: () -> Unit,
    onScoreboard: () -> Unit,
    onRoundStarted: (roundId: Long) -> Unit,
    viewModel: PrepareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    KeepScreenOn()

    SkullScaffold(
        title = if (current == null) {
            stringResource(R.string.app_name)
        } else if (current.isExtra) {
            stringResource(R.string.round_title_extra, current.roundNumber)
        } else {
            stringResource(R.string.round_title_plain, current.roundNumber)
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = onScoreboard) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.scoreboard))
            }
        },
        bottomBar = {
            ActionBar(
                primaryText = stringResource(R.string.prepare_start_bids),
                primaryEnabled = current != null,
                onPrimary = { viewModel.start(onRoundStarted) },
            )
        },
    ) { padding ->
        if (current == null) return@SkullScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DealerLine(
                game = current.details.game,
                roundNumber = current.roundNumber,
                onChoose = viewModel::setDealer,
                prominent = true,
            )
            Text(
                stringResource(R.string.prepare_cards_question),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                current.cards.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Stepper(
                value = current.cards,
                onValueChange = viewModel::setCards,
                range = 1..current.maxCards,
            )
            Text(
                stringResource(R.string.prepare_cards_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.exceedsDeck) {
                WarningCard(stringResource(R.string.warn_cards_exceed_deck, current.needed, current.deckSize))
            }
        }
    }
}
