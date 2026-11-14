package com.gamso.skullkingscore.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gamso.skullkingscore.ui.feature.bids.BidsScreen
import com.gamso.skullkingscore.ui.feature.help.HelpScreen
import com.gamso.skullkingscore.ui.feature.history.HistoryScreen
import com.gamso.skullkingscore.ui.feature.home.HomeScreen
import com.gamso.skullkingscore.ui.feature.licenses.LicensesScreen
import com.gamso.skullkingscore.ui.feature.play.PlayScreen
import com.gamso.skullkingscore.ui.feature.players.PlayersScreen
import com.gamso.skullkingscore.ui.feature.prepare.PrepareScreen
import com.gamso.skullkingscore.ui.feature.results.ResultsScreen
import com.gamso.skullkingscore.ui.feature.scoreboard.EndGameScreen
import com.gamso.skullkingscore.ui.feature.scoreboard.ScoreboardScreen
import com.gamso.skullkingscore.ui.feature.settings.SettingsScreen
import com.gamso.skullkingscore.ui.feature.setup.SetupScreen
import com.gamso.skullkingscore.ui.feature.stats.DuelScreen
import com.gamso.skullkingscore.ui.feature.stats.StatsScreen
import com.gamso.skullkingscore.ui.share.ShareScreen

/**
 * The transitions of the round loop (preparation, bids, play, results, scoreboard) replace the current
 * screen up to the home screen, so that "back" always goes to the home screen and never to a stale screen.
 */
private fun NavController.replaceFlow(route: String) {
    navigate(route) { popUpTo(Routes.HOME) }
}

/**
 * Goes back one screen. Ignored while the current screen is already leaving (a fast double tap on the back
 * arrow would otherwise pop the home screen too and leave an empty stack).
 */
private fun NavController.safeBack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

/**
 * Opens [route] on top of the current screen. Ignored while the current screen is not resumed (it is already
 * leaving, or still entering): a fast double tap would otherwise push the same screen twice.
 */
private fun NavController.safeNavigate(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

@Composable
fun SkullKingNavHost() {
    val navController = rememberNavController()
    val gameArg = navArgument(Routes.ARG_GAME_ID) { type = NavType.LongType }
    val roundArg = navArgument(Routes.ARG_ROUND_ID) { type = NavType.LongType }
    val importArg = navArgument(Routes.ARG_IMPORT) {
        type = NavType.BoolType
        defaultValue = false
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewGame = { navController.safeNavigate(Routes.SETUP) },
                onResume = { route -> navController.safeNavigate(route) },
                onHistory = { navController.safeNavigate(Routes.history()) },
                onStats = { navController.safeNavigate(Routes.STATS) },
                onPlayers = { navController.safeNavigate(Routes.PLAYERS) },
                onHelp = { navController.safeNavigate(Routes.HELP) },
                onSettings = { navController.safeNavigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                onBack = { navController.safeBack() },
                onGameCreated = { gameId -> navController.replaceFlow(Routes.prepare(gameId)) },
            )
        }
        composable(Routes.PLAYERS) { PlayersScreen(onBack = { navController.safeBack() }) }
        // The import argument is read (once) by HistoryViewModel from its SavedStateHandle.
        composable(Routes.HISTORY, arguments = listOf(importArg)) {
            HistoryScreen(
                onShare = { id -> navController.safeNavigate(Routes.share(id)) },
                onBack = { navController.safeBack() },
                onOpen = { route -> navController.safeNavigate(route) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                onBack = { navController.safeBack() },
                onDuel = { navController.safeNavigate(Routes.DUEL) },
            )
        }
        composable(Routes.DUEL) { DuelScreen(onBack = { navController.safeBack() }) }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.safeBack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.safeBack() },
                onRestore = { navController.safeNavigate(Routes.history(import = true)) },
                onLicenses = { navController.safeNavigate(Routes.LICENSES) },
            )
        }
        composable(Routes.LICENSES) { LicensesScreen(onBack = { navController.safeBack() }) }

        composable(Routes.PREPARE, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            PrepareScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.safeNavigate(Routes.scoreboard(gameId)) },
                onRoundStarted = { roundId -> navController.replaceFlow(Routes.bids(gameId, roundId)) },
            )
        }
        composable(Routes.BIDS, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            val roundId = entry.arguments?.getLong(Routes.ARG_ROUND_ID) ?: return@composable
            BidsScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.safeNavigate(Routes.scoreboard(gameId)) },
                onHelp = { navController.safeNavigate(Routes.HELP) },
                onValidated = { navController.replaceFlow(Routes.play(gameId, roundId)) },
            )
        }
        composable(Routes.PLAY, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            val roundId = entry.arguments?.getLong(Routes.ARG_ROUND_ID) ?: return@composable
            PlayScreen(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onScoreboard = { navController.safeNavigate(Routes.scoreboard(gameId)) },
                onHelp = { navController.safeNavigate(Routes.HELP) },
                onRoundValidated = { navController.replaceFlow(Routes.scoreboard(gameId)) },
            )
        }
        composable(Routes.RESULTS, arguments = listOf(gameArg, roundArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            ResultsScreen(
                // Only opened from the scoreboard (round correction): back returns to it.
                onBack = { navController.safeBack() },
                onHelp = { navController.safeNavigate(Routes.HELP) },
                onValidated = { navController.replaceFlow(Routes.scoreboard(gameId)) },
            )
        }
        composable(Routes.SCOREBOARD, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            ScoreboardScreen(
                onBack = { navController.safeBack() },
                onHelp = { navController.safeNavigate(Routes.HELP) },
                onNavigate = { route -> navController.replaceFlow(route) },
                onEditRound = { roundId -> navController.safeNavigate(Routes.results(gameId, roundId)) },
                onFinished = { navController.replaceFlow(Routes.end(gameId)) },
                onShare = { navController.safeNavigate(Routes.share(gameId)) },
            )
        }
        composable(Routes.END, arguments = listOf(gameArg)) { entry ->
            val gameId = entry.arguments?.getLong(Routes.ARG_GAME_ID) ?: return@composable
            EndGameScreen(
                onBack = { navController.safeBack() },
                onScoreboard = { navController.safeNavigate(Routes.scoreboard(gameId)) },
                onShare = { navController.safeNavigate(Routes.share(gameId)) },
            )
        }
        composable(Routes.SHARE, arguments = listOf(gameArg)) {
            ShareScreen(onBack = { navController.safeBack() })
        }
    }
}
