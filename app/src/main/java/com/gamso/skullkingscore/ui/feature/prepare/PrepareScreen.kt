package com.gamso.skullkingscore.ui.feature.prepare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.usecase.StartRoundUseCase
import com.gamso.skullkingscore.domain.usecase.suggestedCards
import com.gamso.skullkingscore.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Starts the next round with the suggested number of cards (previous round + 1). The number of cards and the
 * dealer are then set on the bid entry, so there is no screen for them before.
 */
@HiltViewModel
class PrepareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val startRound: StartRoundUseCase,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))

    /** Kept after success: the screen is being left, a second call during the transition starts nothing. */
    private var starting = false

    fun start(onStarted: (roundId: Long) -> Unit) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            var started = false
            try {
                val details = checkNotNull(games.getGame(gameId)) { "Game not found" }
                // A round already pending (a second tap on "next round") is returned as is.
                val roundId = startRound(gameId, details.suggestedCards())
                started = true
                onStarted(roundId)
            } finally {
                if (!started) starting = false
            }
        }
    }
}

/** No screen of its own: the round starts and the bid entry opens. */
@Composable
fun PrepareScreen(onRoundStarted: (roundId: Long) -> Unit, viewModel: PrepareViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.start(onRoundStarted) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
