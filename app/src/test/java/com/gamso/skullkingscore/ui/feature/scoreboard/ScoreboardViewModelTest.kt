package com.gamso.skullkingscore.ui.feature.scoreboard

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.data.backup.AutoBackupManager
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.repository.AppSettings
import com.gamso.skullkingscore.domain.repository.BackupRepository
import com.gamso.skullkingscore.domain.repository.ImportOptions
import com.gamso.skullkingscore.domain.repository.ImportPreview
import com.gamso.skullkingscore.domain.repository.ImportResult
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.FinishGameUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.fakes.FakeGameRepository
import com.gamso.skullkingscore.fakes.FakeSettingsRepository
import com.gamso.skullkingscore.ui.navigation.Routes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The backup is held at its first step (reading the games): nothing reaches this repository. */
private object UnusedBackupRepository : BackupRepository {
    override suspend fun exportJson(games: List<GameDetails>): String = error("Not used")

    override suspend fun previewImport(json: String): ImportPreview = error("Not used")

    override suspend fun importJson(json: String, options: ImportOptions): ImportResult = error("Not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreboardViewModelTest {

    private val games = FakeGameRepository()
    private val ann = games.addPlayer("Ann")
    private val bob = games.addPlayer("Bob")
    private val gameId = runBlocking {
        games.createGame(listOf(ann, bob), ScoringMode.CLASSIC, emptySet()).also { id ->
            val roundId = games.addRound(id, 1, 1, false)
            games.updateRound(roundId) { it.copy(phase = RoundPhase.SCORED) }
        }
    }

    /** Application scope of the backup: it outlives the screen, cancelled at the end of the test. */
    private val applicationScope = CoroutineScope(Job())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(settings: AppSettings): ScoreboardViewModel {
        val edit = EditRoundUseCase(games, RecalculateScoresUseCase(games))
        // The backup never reaches the folder in these tests: the context is not used.
        val autoBackup = AutoBackupManager(
            ContextWrapper(null),
            games,
            UnusedBackupRepository,
            FakeSettingsRepository(settings),
            applicationScope,
        )
        val handle = SavedStateHandle(mapOf(Routes.ARG_GAME_ID to gameId))
        return ScoreboardViewModel(handle, games, edit, FinishGameUseCase(games), autoBackup).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        }
    }

    @Test
    fun finishingTheGameDoesNotWaitForTheBackup() = runTest {
        // A slow backup (cloud folder): it is held while it reads the games.
        val gate = CompletableDeferred<Unit>()
        games.getAllGamesGate = gate
        val viewModel = viewModel(AppSettings(autoBackup = true, backupFolder = "content://folder"))
        var done = false

        viewModel.finish { done = true }

        assertTrue(done, "the end screen opens right away")
        assertEquals(GameStatus.FINISHED, viewModel.state.value?.game?.status)
        // The backup has started on a background thread, and is still running in the application scope.
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                while (games.allGamesReads == 0) delay(POLL_MS)
            }
        }
        assertFalse(gate.isCompleted)
    }

    @Test
    fun finishingWithoutAutomaticBackupReadsNoGame() = runTest {
        val viewModel = viewModel(AppSettings(autoBackup = false))
        var done = false

        viewModel.finish { done = true }

        assertTrue(done)
        assertEquals(GameStatus.FINISHED, viewModel.state.value?.game?.status)
        assertEquals(0, games.allGamesReads)
    }

    @Test
    fun reopeningTheLastRoundPutsItBackInPlay() = runTest {
        val viewModel = viewModel(AppSettings())
        var reopened: Long? = null

        viewModel.reopenLastRound { reopened = it }

        val round = viewModel.state.value?.rounds?.single()?.round
        assertEquals(round?.id, reopened)
        assertEquals(RoundPhase.PLAYING, round?.phase)
        // Only a validated round can be reopened.
        reopened = null
        viewModel.reopenLastRound { reopened = it }
        assertNull(reopened)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
