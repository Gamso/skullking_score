package com.gamso.skullkingscore

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end flows on the app, each one on its own empty in-memory database and settings ([TestDataModule]).
 * The writes are made on a background thread: the checks wait for the screen to show their result.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class GameFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    /** Waits for the button (it may come with a screen opened after a write), then taps it. */
    private fun click(id: Int, vararg args: Any) {
        rule.waitUntilExactlyOneExists(hasText(text(id, *args)), TIMEOUT_MS)
        rule.onNodeWithText(text(id, *args)).performClick()
    }

    /** Waits until exactly one node matches [matcher]: the screen shows the result of a write. */
    private fun expect(matcher: SemanticsMatcher) = rule.waitUntilExactlyOneExists(matcher, TIMEOUT_MS)

    /** Creates a profile and waits until it is listed (outside the creation dialog, whose field holds the name). */
    private fun createPlayer(name: String) {
        click(R.string.player_new)
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(name)
        click(R.string.save)
        expect(hasText(name) and !hasSetTextAction())
    }

    @Test
    fun homeShowsTheMainActions() {
        rule.onNodeWithText(text(R.string.home_new_game)).assertExists()
        rule.onNodeWithText(text(R.string.home_history)).assertExists()
        rule.onNodeWithText(text(R.string.home_stats)).assertExists()
    }

    @Test
    fun settingsScreenOpensFromHome() {
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        rule.onNodeWithText(text(R.string.settings_keep_screen_on)).assertExists()
        rule.onNodeWithText(text(R.string.settings_auto_backup)).assertExists()
    }

    @Test
    fun aFirstRoundCanBePlayedFromSetupToScoreboard() {
        click(R.string.home_new_game)
        createPlayer(ANA)
        createPlayer(BEA)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)

        // Trick 1 opens by itself: Ana wins with a colored card; the one-card round is then complete.
        chooseWinner(ANA)
        // The trick screen sits over the round screen: its buttons are the last ones.
        rule.onAllNodesWithText(text(R.string.win_other)).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)

        // Both bids were 0: Bea succeeds (+10), Ana misses (-10). The scoreboard opens on the ranking.
        click(R.string.tab_table)
        expect(hasContentDescription(text(R.string.a11y_score_cell, BEA, 1, "+10", 10)))
        expect(hasContentDescription(text(R.string.a11y_score_cell, ANA, 1, "-10", -10)))
        rule.onNodeWithText(text(R.string.tab_curve)).assertExists()
    }

    @Test
    fun finishingAGameShowsThePodium() {
        click(R.string.home_new_game)
        createPlayer(ANA)
        createPlayer(BEA)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
        chooseWinner(ANA)
        rule.onAllNodesWithText(text(R.string.win_other)).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)

        rule.waitUntilExactlyOneExists(hasContentDescription(text(R.string.more)), TIMEOUT_MS)
        rule.onNodeWithContentDescription(text(R.string.more)).performClick()
        click(R.string.scoreboard_finish)
        click(R.string.scoreboard_finish)
        // Bea made her bid of 0: she wins.
        expect(hasText(text(R.string.end_winner, BEA)))
    }

    /** New game with two fresh players, up to the first trick, with [cards] cards dealt. */
    private fun startRound(cards: Int) {
        click(R.string.home_new_game)
        createPlayer(ANA)
        createPlayer(BEA)
        click(R.string.setup_start)
        rule.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.stepper_increase)), TIMEOUT_MS)
        repeat(cards - 1) {
            rule.onAllNodesWithContentDescription(text(R.string.stepper_increase)).onFirst().performClick()
        }
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
    }

    /** In the trick screen, which sits over the round screen: the player buttons of the trick come last. */
    private fun chooseWinner(name: String) {
        click(R.string.trick_choose_winner)
        rule.onAllNodesWithText(name).onLast().performClick()
    }

    @Test
    fun theFirstTrickOpensRightAfterTheBids() {
        startRound(cards = 1)
        expect(hasText(text(R.string.trick_dialog_new, 1)))
    }

    @Test
    fun validatingATrickOpensTheNextOneAndBackReturnsToThePreviousOne() {
        startRound(cards = 2)
        chooseWinner(ANA)
        click(R.string.trick_validate)
        expect(hasText(text(R.string.trick_dialog_new, 2)))
        // Back: trick 1 opens again, to be corrected.
        // The trick screen sits over the round screen: its own back arrow is the last one.
        rule.onAllNodesWithContentDescription(text(R.string.back)).onLast().performClick()
        expect(hasText(text(R.string.trick_dialog_edit, 1)))
    }

    @Test
    fun rascalWagersOnlyBeforeTheLastTrick() {
        startRound(cards = 2)
        chooseWinner(ANA)
        click(R.string.win_pirate)
        click(R.string.pirate_rascal_full)
        rule.onNodeWithText(text(R.string.trick_rascal_wager)).assertExists()
        click(R.string.trick_validate)

        // Trick 2 is the last one: only Harry's power can be used.
        expect(hasText(text(R.string.trick_dialog_new, 2)))
        chooseWinner(ANA)
        click(R.string.win_pirate)
        click(R.string.pirate_rascal_full)
        rule.onNodeWithText(text(R.string.trick_rascal_wager)).assertDoesNotExist()
    }

    @Test
    fun theCardsWonFollowTheWinningCard() {
        startRound(cards = 1)
        chooseWinner(ANA)
        val pirateBySkullKing = hasText(text(R.string.bonus_pirate_by_skull_king), substring = true)
        val skullKingByMermaid = hasText(text(R.string.bonus_skull_king_by_mermaid), substring = true)
        // A numbered card captures no character.
        rule.onNode(pirateBySkullKing).assertDoesNotExist()
        click(R.string.win_mermaid)
        rule.onNode(skullKingByMermaid).assertExists()
        rule.onNode(pirateBySkullKing).assertDoesNotExist()
        click(R.string.win_skull_king)
        rule.onNode(pirateBySkullKing).assertExists()
        rule.onNode(skullKingByMermaid).assertDoesNotExist()
    }

    @Test
    fun aBidChangeCanBeUndoneAndRedone() {
        click(R.string.home_new_game)
        createPlayer(ANA)
        createPlayer(BEA)
        click(R.string.setup_start)
        // Change a bid and undo it, then redo it: each change is written to the database first.
        click(R.string.prepare_start_bids)
        expect(hasText(text(R.string.bids_total, 0, 1)))
        // Each bid is set with - / +: raise the first player's bid.
        rule.onAllNodesWithContentDescription(text(R.string.stepper_increase)).onFirst().performClick()
        expect(hasText(text(R.string.bids_total, 1, 1)))
        rule.onNodeWithText("↶").performClick()
        expect(hasText(text(R.string.bids_total, 0, 1)))
        rule.onNodeWithText("↷").performClick()
        expect(hasText(text(R.string.bids_total, 1, 1)))
    }

    @Test
    fun theCardStatisticsTabOpensFromTheStatisticsScreen() {
        click(R.string.home_stats)
        click(R.string.stats_tab_cards)
        rule.onNodeWithText(text(R.string.stats_more_filters)).assertExists()
        click(R.string.stats_tab_players)
        rule.onNodeWithText(text(R.string.stats_more_filters)).assertExists()
    }

    @Test
    fun selectedPlayersSurviveARotation() {
        click(R.string.home_new_game)
        createPlayer(ANA)
        try {
            rule.activity.runOnUiThread {
                rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(TIMEOUT_MS) { orientation() == Configuration.ORIENTATION_LANDSCAPE }
            // The screen is short in landscape: the chosen players may be below the visible part.
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ANA))
            expect(hasText(ANA))
        } finally {
            // The orientation must not outlive the test: the next tests expect the device in portrait.
            rule.activity.runOnUiThread {
                rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            rule.waitUntil(TIMEOUT_MS) { orientation() == Configuration.ORIENTATION_PORTRAIT }
        }
    }

    private fun orientation(): Int = rule.activity.resources.configuration.orientation

    private companion object {
        const val ANA = "Ana"
        const val BEA = "Bea"
        const val TIMEOUT_MS = 5_000L
    }
}
