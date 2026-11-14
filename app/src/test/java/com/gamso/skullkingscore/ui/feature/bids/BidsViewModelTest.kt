package com.gamso.skullkingscore.ui.feature.bids

import androidx.lifecycle.SavedStateHandle
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.undo.UndoKind
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import com.gamso.skullkingscore.ui.components.UndoNotice
import com.gamso.skullkingscore.ui.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BidsViewModelTest {

    private val games = FakeGameRepository()
    private val history = UndoHistory()
    private val ann = games.addPlayer("Ann")
    private val bob = games.addPlayer("Bob")
    private val gameId = runBlocking { games.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet()) }
    private val roundId = runBlocking { games.addRound(gameId, 1, 2, false) }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The ViewModel of the bids of the round, its state observed as by the screen. */
    private fun TestScope.viewModel(): BidsViewModel {
        val handle = SavedStateHandle(mapOf(Routes.ARG_GAME_ID to gameId, Routes.ARG_ROUND_ID to roundId))
        val edit = EditRoundUseCase(games, RecalculateScoresUseCase(games))
        return BidsViewModel(handle, games, edit, history).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.undoState.collect {} }
        }
    }

    private fun bid(viewModel: BidsViewModel, playerId: Long) =
        viewModel.state.value?.round?.details?.entry(playerId)?.bid

    @Test
    fun aBidStepCanBeUndoneAndRedone() = runTest {
        val viewModel = viewModel()
        val notices = mutableListOf<UndoNotice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.notices.collect(notices::add) }

        viewModel.stepBid(ann, 1)
        assertEquals(1, bid(viewModel, ann))
        assertTrue(viewModel.undoState.value.canUndo)

        viewModel.undo()
        assertEquals(0, bid(viewModel, ann))
        assertTrue(viewModel.undoState.value.canRedo)

        viewModel.redo()
        assertEquals(1, bid(viewModel, ann))
        assertEquals(listOf(false, true), notices.map { it.redone })
        assertEquals(listOf("Ann", "Ann"), notices.map { it.action.detail })
        assertTrue(notices.all { it.action.kind == UndoKind.BID })
    }

    @Test
    fun aStepPastTheNumberOfCardsRecordsNothing() = runTest {
        val viewModel = viewModel()

        viewModel.stepBid(bob, -1)

        assertEquals(0, bid(viewModel, bob))
        assertFalse(viewModel.undoState.value.canUndo)
    }

    @Test
    fun theCannonballCanBeUndone() = runTest {
        val viewModel = viewModel()

        viewModel.setCannonball(bob, true)
        assertEquals(true, viewModel.state.value?.round?.details?.entry(bob)?.cannonball)

        viewModel.undo()
        assertEquals(false, viewModel.state.value?.round?.details?.entry(bob)?.cannonball)
    }

    @Test
    fun confirmingTheBidsStartsThePlayAndClearsTheUndoHistory() = runTest {
        val viewModel = viewModel()
        viewModel.stepBid(ann, 1)
        var done = false

        viewModel.confirm { done = true }

        assertTrue(done)
        assertEquals(RoundPhase.PLAYING, viewModel.state.value?.round?.round?.phase)
        assertFalse(viewModel.undoState.value.canUndo)
    }
}
