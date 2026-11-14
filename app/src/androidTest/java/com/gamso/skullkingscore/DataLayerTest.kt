package com.gamso.skullkingscore

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.Pirate
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.WinningCard
import com.gamso.skullkingscore.data.backup.BackupRepositoryImpl
import com.gamso.skullkingscore.data.local.SkullKingDatabase
import com.gamso.skullkingscore.data.repository.GameRepositoryImpl
import com.gamso.skullkingscore.data.repository.PlayerRepositoryImpl
import com.gamso.skullkingscore.data.settings.SettingsRepositoryImpl
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.ImportOptions
import com.gamso.skullkingscore.domain.repository.InvalidBackupException
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.domain.usecase.FinishGameUseCase
import com.gamso.skullkingscore.domain.usecase.RecalculateScoresUseCase
import com.gamso.skullkingscore.domain.usecase.StartRoundUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Real database, repositories and use cases on an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class DataLayerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<SkullKingDatabase>()

    private class Env(val db: SkullKingDatabase) {
        val players = PlayerRepositoryImpl(db.playerDao())
        val games = GameRepositoryImpl(db, db.gameDao(), db.playerDao())
        val backup = BackupRepositoryImpl(db, db.gameDao(), db.playerDao(), RecalculateScoresUseCase(games))
        val edit = EditRoundUseCase(games, RecalculateScoresUseCase(games))
        val start = StartRoundUseCase(games)
    }

    private fun env(): Env {
        val db = Room.inMemoryDatabaseBuilder(context, SkullKingDatabase::class.java).build()
        databases += db
        return Env(db)
    }

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    /** Two-player, one-card round: the first player wins the trick with a pirate. */
    private suspend fun Env.playOneRound(): Triple<Long, Long, List<Long>> {
        val ana = players.createPlayer("Ana")
        val bob = players.createPlayer("Bob")
        val gameId = games.createGame(
            playerIds = listOf(ana, bob),
            mode = ScoringMode.CLASSIC,
            expansionCards = setOf(ExpansionCard.SEVENS),
            firstDealerSeat = 1,
        )
        val roundId = start(gameId, 1)
        edit.recordTrick(
            gameId,
            roundId,
            1,
            TrickDraft(
                winnerId = ana,
                bonus = Bonus(colored14 = 1),
                winningCard = WinningCard.PIRATE,
                winningPirate = Pirate.ROSIE,
            ),
        )
        edit.setPhase(gameId, roundId, RoundPhase.SCORED)
        return Triple(gameId, roundId, listOf(ana, bob))
    }

    @Test
    fun aGameKeepsItsTableOrderDealerAndOptions() = runBlocking {
        val env = env()
        val (gameId, _, ids) = env.playOneRound()
        val game = checkNotNull(env.games.getGame(gameId)).game
        assertEquals(ids, game.players.map { it.id })
        assertEquals(1, game.firstDealerSeat)
        assertEquals(setOf(ExpansionCard.SEVENS), game.expansionCards)
        assertEquals(GameStatus.IN_PROGRESS, game.status)
        env.games.setFirstDealerSeat(gameId, 0)
        assertEquals(0, checkNotNull(env.games.getGame(gameId)).game.firstDealerSeat)
    }

    @Test
    fun liveTricksAreScoredAndReloadedWithTheirWinningCard() = runBlocking {
        val env = env()
        val (gameId, _, ids) = env.playOneRound()
        val round = checkNotNull(env.games.getGame(gameId)).rounds.single()
        val trick = round.tricks.single()
        assertEquals(WinningCard.PIRATE, trick.winningCard)
        assertEquals(Pirate.ROSIE, trick.winningPirate)
        // Bid 0: Ana wins a trick and misses (-10), Bob succeeds (+10 x 1 card).
        assertEquals(1, round.entry(ids[0])?.tricks)
        assertEquals(-10.0, round.entry(ids[0])?.score)
        assertEquals(10.0, round.entry(ids[1])?.score)
    }

    @Test
    fun deletingTheLastTrickClearsTheRoundResults() = runBlocking {
        val env = env()
        val (gameId, roundId, ids) = env.playOneRound()
        env.edit.deleteLastTrick(gameId, roundId)
        val round = checkNotNull(env.games.getGame(gameId)).rounds.single()
        assertTrue(round.tricks.isEmpty())
        assertEquals(0, round.entry(ids[0])?.tricks)
    }

    @Test
    fun reopeningTheLastRoundPutsItBackInPlay() = runBlocking {
        val env = env()
        val (gameId, roundId, _) = env.playOneRound()
        assertEquals(roundId, env.edit.reopenLastRound(gameId))
        assertEquals(RoundPhase.PLAYING, checkNotNull(env.games.getGame(gameId)).rounds.single().round.phase)
    }

    @Test
    fun finishingAGameMarksItFinishedAndKeepsItInTheSummaries() = runBlocking {
        val env = env()
        val (gameId, _, _) = env.playOneRound()
        FinishGameUseCase(env.games)(gameId)
        val game = checkNotNull(env.games.getGame(gameId)).game
        assertEquals(GameStatus.FINISHED, game.status)
        assertNotNull(game.endedAt)
        assertEquals(1, env.games.getAllGames().size)
    }

    @Test
    fun deletingAGameRemovesItsRoundsButKeepsThePlayers() = runBlocking {
        val env = env()
        val (gameId, _, _) = env.playOneRound()
        env.games.deleteGame(gameId)
        assertNull(env.games.getGame(gameId))
        assertTrue(env.games.getAllGames().isEmpty())
        assertEquals(2, env.players.getAllPlayers().size)
    }

    @Test
    fun aBackupRestoresTheGameInAnotherDatabaseAndImportingTwiceAddsNothing() = runBlocking {
        val source = env()
        val (gameId, _, _) = source.playOneRound()
        val json = source.backup.exportJson(source.games.getAllGames())

        val target = env()
        val preview = target.backup.previewImport(json)
        assertEquals(1, preview.newGames)
        assertEquals(2, preview.newPlayers)
        assertTrue(preview.nameConflicts.isEmpty())

        val first = target.backup.importJson(json)
        assertEquals(1, first.importedGames)
        val restored = target.games.getAllGames().single()
        assertEquals(WinningCard.PIRATE, restored.rounds.single().tricks.single().winningCard)
        assertEquals(1, restored.game.firstDealerSeat)
        assertEquals(checkNotNull(source.games.getGame(gameId)).game.uuid, restored.game.uuid)

        val second = target.backup.importJson(json)
        assertEquals(0, second.importedGames)
        assertEquals(1, second.skippedGames)
        assertEquals(1, target.games.getAllGames().size)
        assertEquals(2, target.players.getAllPlayers().size)
    }

    @Test
    fun homonymsAreMergedOnlyWhenTheUserAsksForIt() = runBlocking {
        val source = env()
        source.playOneRound()
        val json = source.backup.exportJson(source.games.getAllGames())

        val merged = env().also { it.players.createPlayer("Ana") }
        val conflicts = merged.backup.previewImport(json).nameConflicts
        assertEquals(listOf("Ana"), conflicts.map { it.name })
        val resultMerged = merged.backup.importJson(json, ImportOptions(conflicts.map { it.importedUuid }.toSet()))
        assertEquals(1, resultMerged.mergedPlayers)
        assertEquals(listOf("Ana", "Bob"), merged.players.getAllPlayers().map { it.name }.sorted())

        val separate = env().also { it.players.createPlayer("Ana") }
        val resultSeparate = separate.backup.importJson(json)
        assertEquals(0, resultSeparate.mergedPlayers)
        assertEquals(listOf("Ana", "Ana", "Bob"), separate.players.getAllPlayers().map { it.name }.sorted())
    }

    @Test
    fun reImportingAMergedBackupCreatesNoProfile() = runBlocking {
        val source = env()
        source.playOneRound()
        val json = source.backup.exportJson(source.games.getAllGames())

        val target = env().also { it.players.createPlayer("Ana") }
        val conflicts = target.backup.previewImport(json).nameConflicts
        target.backup.importJson(json, ImportOptions(conflicts.map { it.importedUuid }.toSet()))
        val preview = target.backup.previewImport(json)
        assertEquals(0, preview.newGames)
        assertEquals(0, preview.newPlayers)
        assertTrue(preview.nameConflicts.isEmpty())

        val again = target.backup.importJson(json)
        assertEquals(0, again.createdPlayers)
        assertEquals(listOf("Ana", "Bob"), target.players.getAllPlayers().map { it.name }.sorted())
    }

    @Test
    fun theCachedScoresOfABackupAreRecomputed() = runBlocking {
        val source = env()
        source.playOneRound()
        val json = source.backup.exportJson(source.games.getAllGames())
            .replace("\"score\":-10.0", "\"score\":999.0")

        val target = env()
        target.backup.importJson(json)
        val round = target.games.getAllGames().single().rounds.single()
        assertEquals(listOf(-10.0, 10.0), round.entries.map { it.score }.sorted())
    }

    @Test
    fun twoPlayersMergedIntoTheSameProfileMakeTheBackupInvalid() = runBlocking {
        val source = env()
        val ana = source.players.createPlayer("Ana")
        val other = source.players.createPlayer("ana")
        val gameId = source.games.createGame(listOf(ana, other), ScoringMode.CLASSIC, emptySet(), 0)
        source.start(gameId, 1)
        val json = source.backup.exportJson(source.games.getAllGames())

        val target = env().also { it.players.createPlayer("Ana") }
        val conflicts = target.backup.previewImport(json).nameConflicts
        assertEquals(2, conflicts.size)
        val result = runCatching {
            target.backup.importJson(json, ImportOptions(conflicts.map { it.importedUuid }.toSet()))
        }
        assertTrue(result.exceptionOrNull() is InvalidBackupException)
        assertTrue(target.games.getAllGames().isEmpty())
        assertEquals(1, target.players.getAllPlayers().size)
    }

    @Test
    fun anInvalidBackupIsRejectedWithoutTouchingTheDatabase() = runBlocking {
        val env = env()
        val result = runCatching { env.backup.importJson("pas du json") }
        assertTrue(result.isFailure)
        assertTrue(env.games.getAllGames().isEmpty())
    }

    @Test
    fun settingsArePersistedBetweenInstances() {
        val name = "settings"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        val first = SettingsRepositoryImpl(context)
        assertTrue(first.settings.value.keepScreenOn)
        assertFalse(first.settings.value.autoBackup)
        first.update { it.copy(keepScreenOn = false, autoBackup = true, backupFolder = "content://folder") }
        val second = SettingsRepositoryImpl(context)
        assertFalse(second.settings.value.keepScreenOn)
        assertTrue(second.settings.value.autoBackup)
        assertEquals("content://folder", second.settings.value.backupFolder)
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
