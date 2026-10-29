package com.skullking.score

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skullking.score.data.local.SkullKingDatabase
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Database migrations, from the exported schemas (`app/schemas`), on a real file. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = SkullKingDatabase::class,
    )

    @Test
    fun version1ScoresSurviveTheMoveToDecimalScores() = runBlocking {
        // Version 1: a game of two players with a validated round, scores stored as integers.
        helper.createDatabase(1).use { db ->
            db.execSQL("INSERT INTO players VALUES (1, 'p1', 'Ana', 0, 0, 0), (2, 'p2', 'Bob', 0, 0, 0)")
            db.execSQL("INSERT INTO games VALUES (1, 'g1', 0, NULL, 'RASCAL', '', 'IN_PROGRESS', 0)")
            db.execSQL("INSERT INTO game_players VALUES (1, 1, 0), (1, 2, 1)")
            db.execSQL("INSERT INTO rounds VALUES (1, 1, 1, 2, 0, 0, 'SCORED')")
            db.execSQL(
                "INSERT INTO player_rounds VALUES " +
                    "(1, 1, 2, NULL, 2, 0, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0), " +
                    "(1, 2, 1, NULL, 0, 0, 0, -10, 0, 0, 0, 0, 0, 0, 0, 0)",
            )
        }

        // Version 2: the score column becomes a REAL; Room checks the schema against 2.json.
        helper.runMigrationsAndValidate(2).use { db ->
            assertEquals(listOf(20.0 to "real", -10.0 to "real"), db.scores())
            // A half point (Rascal glancing blow) is now kept as is.
            db.execSQL("UPDATE player_rounds SET score = 2.5 WHERE playerId = 2")
            assertEquals(listOf(20.0 to "real", 2.5 to "real"), db.scores())
        }

        // The app opens the migrated file and reads the history totals from it.
        val database = Room.databaseBuilder(context, SkullKingDatabase::class.java, file.absolutePath).build()
        try {
            val totals = database.gameDao().observeTotals().first().associate { it.playerId to it.total }
            assertEquals(mapOf(1L to 20.0, 2L to 2.5), totals)
        } finally {
            database.close()
        }
    }

    private fun SQLiteConnection.scores(): List<Pair<Double, String>> =
        prepare("SELECT score, typeof(score) FROM player_rounds ORDER BY playerId").use { statement ->
            buildList {
                while (statement.step()) add(statement.getDouble(0) to statement.getText(1))
            }
        }
}
