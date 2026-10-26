package com.skullking.score.ui.navigation

import com.skullking.score.domain.model.GameStep

object Routes {
    const val ARG_GAME_ID = "gameId"
    const val ARG_ROUND_ID = "roundId"
    const val ARG_IMPORT = "import"

    const val HOME = "home"
    const val SETUP = "setup"
    const val PLAYERS = "players"
    const val HISTORY = "history?$ARG_IMPORT={$ARG_IMPORT}"
    const val STATS = "stats"
    const val DUEL = "duel"
    const val HELP = "help"
    const val SETTINGS = "settings"

    const val PREPARE = "prepare/{$ARG_GAME_ID}"
    const val BIDS = "bids/{$ARG_GAME_ID}/{$ARG_ROUND_ID}"
    const val PLAY = "play/{$ARG_GAME_ID}/{$ARG_ROUND_ID}"
    const val RESULTS = "results/{$ARG_GAME_ID}/{$ARG_ROUND_ID}"
    const val SCOREBOARD = "scoreboard/{$ARG_GAME_ID}"
    const val END = "end/{$ARG_GAME_ID}"
    const val SHARE = "share/{$ARG_GAME_ID}"

    fun history(import: Boolean = false) = if (import) "history?$ARG_IMPORT=true" else "history"
    fun prepare(gameId: Long) = "prepare/$gameId"
    fun bids(gameId: Long, roundId: Long) = "bids/$gameId/$roundId"
    fun play(gameId: Long, roundId: Long) = "play/$gameId/$roundId"
    fun results(gameId: Long, roundId: Long) = "results/$gameId/$roundId"
    fun scoreboard(gameId: Long) = "scoreboard/$gameId"
    fun end(gameId: Long) = "end/$gameId"
    fun share(gameId: Long) = "share/$gameId"

    /** Screen to resume a game. */
    fun forStep(gameId: Long, step: GameStep): String = when (step) {
        is GameStep.Prepare -> prepare(gameId)
        is GameStep.Bids -> bids(gameId, step.roundId)
        is GameStep.Play -> play(gameId, step.roundId)
        GameStep.Scoreboard -> scoreboard(gameId)
        GameStep.Finished -> end(gameId)
    }
}
