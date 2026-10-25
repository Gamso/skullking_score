package com.skullking.score.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skullking.score.ui.feature.bids.BidsScreen
import com.skullking.score.ui.feature.help.HelpScreen
import com.skullking.score.ui.feature.history.HistoryScreen
import com.skullking.score.ui.feature.home.HomeScreen
import com.skullking.score.ui.feature.play.PlayScreen
import com.skullking.score.ui.feature.players.PlayersScreen
import com.skullking.score.ui.feature.prepare.PrepareScreen
import com.skullking.score.ui.feature.results.ResultsScreen
import com.skullking.score.ui.feature.scoreboard.EndGameScreen
import com.skullking.score.ui.feature.scoreboard.ScoreboardScreen
import com.skullking.score.ui.feature.settings.SettingsScreen
import com.skullking.score.ui.feature.setup.SetupScreen
import com.skullking.score.ui.feature.stats.DuelScreen
import com.skullking.score.ui.feature.stats.StatsScreen
import com.skullking.score.ui.share.ShareScreen

/**
 * The transitions of the round loop (preparation, bids, play, results, scoreboard) replace the current
 * screen up to the home screen, so that "back" always goes to the home screen and never to a stale screen.
 */
private fun NavController.replaceFlow(route: String) {
    navigate(route) { popUpTo(Routes.HOME) }
}

@Composable
fun SkullKingNavHost() {
    val navController = rememberNavController()
    val gameArg = navArgument(Routes.ARG_GAME_ID) { type = NavType.LongType }
    val roundArg = navArgument(Routes.ARG_ROUND_ID) { type = NavType.LongType }
    val fromArg = navArgument(Routes.ARG_FROM) {
        type = NavType.LongType
        defaultValue = -1L
    }
    val importArg = navArgument(Routes.ARG_IMPORT) {
        type = NavType.BoolType
        defaultValue = false
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewGame = { navController.navigate(Routes.setup()) },
                onResume = { route -> navController.navigate(route) },
                onHistory = { navController.navigate(Routes.history()) },
                onStats = { navController.navigate(Routes.STATS) },
                onPlayers = { navController.navigate(Routes.PLAYERS) },
                onHelp = { navController.navigate(Routes.HELP) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETUP, arguments = listOf(fromArg)) {
            SetupScreen(
                onBack = { navController.popBackStack() },
                onGameCreated = { gameId -> navController.replaceFlow(Routes.prepare(gameId)) },
            )
        }
        composable(Routes.PLAYERS) { PlayersScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HISTORY, arguments = listOf(importArg)) { entry ->
            HistoryScreen(
                startImport = entry.arguments?.getBoolean(Routes.ARG_IMPORT) ?: false,
                onShare = { id -> navController.navigate(Routes.share(id)) },
                onBack = { navController.popBackStack() },
                onOpen = { route -> navController.navigate(route) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                onDuel = { navController.navigate(Routes.DUEL) },
            )
        }
        composable(Routes.DUEL) { DuelScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRestore = { navController.navigate(Routes.history(import = true)) },
            )
        }

        composable(Routes.PREPARE, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            PrepareScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.navigate(Routes.scoreboard(gameId)) },
                onRoundStarted = { roundId -> navController.replaceFlow(Routes.bids(gameId, roundId)) },
            )
        }
        composable(Routes.BIDS, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            val roundId = entry.arguments?.getLong(Routes.ARG_ROUND_ID) ?: return@composable
            BidsScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.navigate(Routes.scoreboard(gameId)) },
                onHelp = { navController.navigate(Routes.HELP) },
                onValidated = { navController.replaceFlow(Routes.play(gameId, roundId)) },
            )
        }
        composable(Routes.PLAY, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            val roundId = entry.arguments?.getLong(Routes.ARG_ROUND_ID) ?: return@composable
            PlayScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.navigate(Routes.scoreboard(gameId)) },
                onHelp = { navController.navigate(Routes.HELP) },
                onEnterResults = { navController.replaceFlow(Routes.results(gameId, roundId)) },
                onRoundValidated = { navController.replaceFlow(Routes.scoreboard(gameId)) },
            )
        }
        composable(Routes.RESULTS, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            ResultsScreen(
                onBack = { navController.popBackStack() },
                onHelp = { navController.navigate(Routes.HELP) },
                onValidated = { navController.replaceFlow(Routes.scoreboard(gameId)) },
            )
        }
        composable(Routes.SCOREBOARD, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            ScoreboardScreen(
                onBack = { navController.popBackStack() },
                onHelp = { navController.navigate(Routes.HELP) },
                onNavigate = { route -> navController.replaceFlow(route) },
                onEditRound = { roundId -> navController.navigate(Routes.results(gameId, roundId)) },
                onFinished = { navController.replaceFlow(Routes.end(gameId)) },
                onShare = { navController.navigate(Routes.share(gameId)) },
            )
        }
        composable(Routes.END, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            EndGameScreen(
                onBack = { navController.popBackStack() },
                onScoreboard = { navController.navigate(Routes.scoreboard(gameId)) },
                onReplay = { navController.navigate(Routes.setup(gameId)) },
                onShare = { navController.navigate(Routes.share(gameId)) },
            )
        }
        composable(Routes.SHARE, arguments = listOf(gameArg)) {
            ShareScreen(onBack = { navController.popBackStack() })
        }
    }
}
