package com.skullking.score.domain.export

import com.skullking.score.core.BonusType
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.stats.StatsPeriod
import java.time.Instant
import java.time.ZoneId

/** Export filter: period and player. */
data class ExportFilter(val period: StatsPeriod = StatsPeriod.ALL, val playerId: Long? = null) {
    fun apply(
        games: List<GameDetails>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<GameDetails> {
        val from = period.startMillis(now, zone)
        return games.filter { details ->
            (from == null || details.game.startedAt >= from) &&
                (playerId == null || details.game.players.any { it.id == playerId })
        }
    }
}

/**
 * CSV, one line per player and per round, directly readable in a French spreadsheet
 * (";" separator, UTF-8 encoding with BOM to be written by the caller).
 */
object CsvExporter {

    private val HEADER = listOf(
        "date", "partie", "mode", "manche", "manche_supplementaire", "cartes", "joueur",
        "annonce", "annonce_finale", "plis",
        "14_couleur", "14_noir", "sirenes_par_pirate", "pirates_par_skull_king", "skull_king_par_sirene",
        "con", "huits", "sept", "butins", "monstres_davy_jones", "pari_rascal",
        "score_manche", "cumul",
    )

    fun export(games: List<GameDetails>, playerId: Long? = null, zone: ZoneId = ZoneId.systemDefault()): String {
        val out = StringBuilder()
        out.append(HEADER.joinToString(SEPARATOR)).append('\n')
        for (details in games.sortedBy { it.game.startedAt }) {
            val game = details.game
            val date = Instant.ofEpochMilli(game.startedAt).atZone(zone).toLocalDate().toString()
            val scored = GameScoreCalculator.calculate(details)
            for (round in scored.rounds.filter { it.isScored }) {
                for (player in game.players.filter { playerId == null || it.id == playerId }) {
                    val entry = round.details.entry(player.id)
                    val breakdown = round.breakdowns[player.id]
                    if (entry == null || breakdown == null) continue
                    fun count(type: BonusType) = breakdown.bonusLines.firstOrNull { it.type == type }?.count ?: 0
                    val row = listOf(
                        date,
                        game.uuid,
                        game.mode.name,
                        round.round.number.toString(),
                        if (round.round.isExtra) "oui" else "non",
                        round.round.cardsDealt.toString(),
                        player.name,
                        entry.bid.toString(),
                        entry.effectiveBid.toString(),
                        entry.tricks.toString(),
                        entry.bonus.colored14.toString(),
                        entry.bonus.black14.toString(),
                        entry.bonus.mermaidsByPirate.toString(),
                        entry.bonus.piratesBySkullKing.toString(),
                        entry.bonus.skullKingByMermaid.toString(),
                        entry.bonus.conCaptured.toString(),
                        entry.bonus.eights.toString(),
                        entry.bonus.sevens.toString(),
                        count(BonusType.LOOT).toString(),
                        count(BonusType.DAVY_JONES).toString(),
                        entry.rascalWager.toString(),
                        breakdown.total.toString(),
                        (round.cumulative[player.id] ?: 0).toString(),
                    )
                    out.append(row.joinToString(SEPARATOR) { escape(it) }).append('\n')
                }
            }
        }
        return out.toString()
    }

    private const val SEPARATOR = ";"

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
