package com.gamso.skullkingscore.ui.feature.play

import androidx.lifecycle.SavedStateHandle
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.undo.UndoHistory
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import com.gamso.skullkingscore.ui.components.TrickEvents
import com.gamso.skullkingscore.ui.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
class PlayViewModelTest {

    private val games = FakeGameRepository()
    private val history = UndoHistory()
    private val ann = games.addPlayer("Ann")
    private val bob = games.addPlayer("Bob")
    private val gameId = runBlocking { games.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet()) }
    private val roundId = runBlocking {
        games.addRound(gameId, 1, 2, false).also { id ->
            games.updateRound(id) { it.copy(phase = RoundPhase.PLAYING) }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The ViewModel of the round in play, its state observed as by the screen. */
    private fun TestScope.viewModel(): PlayViewModel {
        val handle = SavedStateHandle(mapOf(Routes.ARG_GAME_ID to gameId, Routes.ARG_ROUND_ID to roundId))
        val edit = EditRoundUseCase(games, RecalculateScoresUseCase(games))
        return PlayViewModel(handle, games, edit, history).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.undoRedo.state.collect {} }
        }
    }

    private fun PlayViewModel.round(): RoundDetails = assertNotNull(state.value).round.details

    @Test
    fun aTrickAndItsEventsAreUndoneTogetherAndRedone() = runTest {
        val viewModel = viewModel()
        val events = TrickEvents(lootPlayerIds = listOf(bob), chests = listOf(bob to setOf(SeaMonster.KRAKEN)))

        viewModel.recordTrick(1, TrickDraft(winnerId = ann), events)
        assertEquals(1, viewModel.round().entry(ann)?.tricks)
        assertEquals(1, viewModel.round().activeLoots.size)

        viewModel.undoRedo.undo()
        viewModel.round().let {
            assertTrue(it.tricks.isEmpty())
            assertTrue(it.activeLoots.isEmpty())
            assertTrue(it.activeCaptures.isEmpty())
            assertEquals(0, it.entry(ann)?.tricks)
        }

        viewModel.undoRedo.redo()
        viewModel.round().let {
            assertEquals(ann, it.tricks.single().winnerId)
            assertEquals(1, it.activeLoots.size)
            assertEquals(1, it.activeCaptures.size)
        }
    }

    @Test
    fun undoingACorrectionRestoresThePreviousWinner() = runTest {
        val viewModel = viewModel()
        viewModel.recordTrick(1, TrickDraft(winnerId = ann))
        viewModel.recordTrick(1, TrickDraft(winnerId = bob))
        assertEquals(bob, viewModel.round().tricks.single().winnerId)

        viewModel.undoRedo.undo()

        assertEquals(ann, viewModel.round().tricks.single().winnerId)
        assertEquals(1, viewModel.round().entry(ann)?.tricks)
        assertEquals(0, viewModel.round().entry(bob)?.tricks)
    }

    @Test
    fun aDeletedTrickComesBackWithUndo() = runTest {
        val viewModel = viewModel()
        viewModel.recordTrick(1, TrickDraft(winnerId = ann))
        viewModel.recordTrick(2, TrickDraft(winnerId = bob))

        viewModel.deleteLastTrick()
        assertEquals(listOf(1), viewModel.round().tricks.map { it.number })

        viewModel.undoRedo.undo()
        assertEquals(listOf(ann, bob), viewModel.round().tricks.map { it.winnerId })

        viewModel.undoRedo.redo()
        assertEquals(listOf(1), viewModel.round().tricks.map { it.number })
    }

    @Test
    fun validatingTheRoundScoresItAndClearsTheUndoHistory() = runTest {
        val viewModel = viewModel()
        viewModel.recordTrick(1, TrickDraft(winnerId = ann))
        viewModel.recordTrick(2, TrickDraft(winnerId = ann))
        var done = false

        viewModel.validateRound { done = true }

        assertTrue(done)
        assertEquals(RoundPhase.SCORED, assertNotNull(viewModel.state.value).round.round.phase)
        assertFalse(viewModel.undoRedo.state.value.canUndo)
        // Both bid 0: Ann took the two tricks (-20), Bob none (+20).
        assertEquals(-20.0, viewModel.round().entry(ann)?.score)
        assertEquals(20.0, viewModel.round().entry(bob)?.score)
    }
}
