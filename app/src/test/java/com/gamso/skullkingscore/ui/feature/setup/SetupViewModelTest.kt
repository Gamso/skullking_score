package com.gamso.skullkingscore.ui.feature.setup

import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.fakes.FakeGameRepository
import com.gamso.skullkingscore.fakes.FakePlayerRepository
import com.gamso.skullkingscore.fakes.FakeSettingsRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val players = listOf(Player(1, "u1", "Ann", 0, 0), Player(2, "u2", "Bob", 0, 0))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aSecondTapOnStartCreatesNoSecondGame() = runTest {
        val games = FakeGameRepository(players).apply { createGameGate = CompletableDeferred() }
        val viewModel = SetupViewModel(FakePlayerRepository(players), games, FakeSettingsRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        players.forEach(viewModel::addPlayer)
        assertTrue(viewModel.state.value.canStart)
        assertFalse(viewModel.state.value.starting)

        val created = mutableListOf<Long>()
        viewModel.start { created += it }
        assertTrue(viewModel.state.value.starting)
        viewModel.start { created += it }
        checkNotNull(games.createGameGate).complete(Unit)

        assertEquals(1, games.createdGames)
        assertEquals(1, created.size)
        // The guard holds after success, until the screen is left.
        viewModel.start { created += it }
        assertEquals(1, games.createdGames)
        assertTrue(viewModel.state.value.starting)
    }
}
