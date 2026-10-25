package com.skullking.score.demo

import com.skullking.score.core.ScoringMode
import com.skullking.score.data.backup.BackupCodec
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.scoring.GameScoreCalculator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Garde la base de démo (`demo/skullking-demo.json`) cohérente avec le code.
 *
 * Pour la régénérer : `DEMO_OUT=../demo/skullking-demo.json ./gradlew :app:testDebugUnitTest --tests "*DemoDataTest*"`
 */
class DemoDataTest {

    private val demoFile = File("../demo/skullking-demo.json")

    @Test
    fun generateWhenRequested() {
        val out = System.getenv("DEMO_OUT") ?: return
        File(out).apply { absoluteFile.parentFile.mkdirs() }.writeText(DemoData.json())
    }

    @Test
    fun committedDemoFileIsUpToDate() {
        if (System.getenv("DEMO_OUT") != null || !demoFile.exists()) return
        assertEquals(
            DemoData.json(),
            demoFile.readText(),
            "demo/skullking-demo.json est périmé : régénérez-le (voir demo/README.md)",
        )
    }

    @Test
    fun demoDataIsRealisticAndConsistent() {
        val games = DemoData.generate()
        assertEquals(12, DemoData.players.size)
        assertEquals(40, games.size)
        assertTrue(games.all { it.game.status == GameStatus.FINISHED })
        // Nombre de joueurs variable : plusieurs tailles de table, de 3 à 8.
        val sizes = games.map { it.game.players.size }
        assertEquals(setOf(3, 4, 5, 6, 7, 8), sizes.toSet())
        // Tous les modes et plusieurs extensions sont représentés.
        assertEquals(ScoringMode.entries.toSet(), games.map { it.game.mode }.toSet())
        assertTrue(games.any { it.game.expansionCards.isNotEmpty() })
        assertTrue(games.any { it.game.expansionCards.isEmpty() })

        for (details in games) {
            val scored = GameScoreCalculator.calculate(details)
            for (round in details.rounds) {
                assertEquals(RoundPhase.SCORED, round.round.phase)
                // Aucune alerte de cohérence : plis, cartes et captures sont plausibles.
                val warnings = GameScoreCalculator.warnings(details.game, round)
                assertTrue(warnings.isEmpty(), "Alertes manche ${round.round.number} : ${warnings.map { it.code }}")
                assertEquals(round.round.cardsDealt, round.entries.sumOf { it.tricks } + round.round.discardedTricks)
                assertTrue(round.tricks.all { it.winnerId == null || it.winningCard != null })
            }
            // Les scores en cache correspondent au calcul : l'historique affiche les mêmes totaux.
            val cached = details.rounds.flatMap {
                it.entries
            }.groupBy { it.playerId }.mapValues { (_, e) -> e.sumOf { it.score } }
            assertEquals(scored.totals, cached)
        }
    }

    @Test
    fun demoFileCanBeDecodedByTheImporter() {
        val dto = BackupCodec.decode(DemoData.json())
        assertEquals(12, dto.players.size)
        assertEquals(40, dto.games.size)
        assertTrue(dto.games.sumOf { it.rounds.size } >= 400)
        assertEquals(BackupCodec.FORMAT_VERSION, dto.formatVersion)
    }
}
