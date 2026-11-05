package com.skullking.score

import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows on the real app (the test device's database): player names are unique so that the tests do not
 * depend on the state left behind by a previous run.
 */
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun click(id: Int, vararg args: Any) {
        rule.onNodeWithText(text(id, *args)).performClick()
    }

    /**
     * Creates a profile. In the new game screen, the "New profile" button comes after the available profiles, which
     * grow with every test run: on a small screen it is below the visible part, so the list scrolls to it first.
     */
    private fun createPlayer(name: String, inSetup: Boolean = true) {
        if (inSetup) rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(R.string.player_new)))
        click(R.string.player_new)
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(name)
        click(R.string.save)
        rule.waitForIdle()
    }

    private fun uniqueName(prefix: String) = prefix + (System.currentTimeMillis() % TEN_THOUSAND)

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
        val winner = uniqueName("Ana")
        val other = uniqueName("Bea")
        click(R.string.home_new_game)
        createPlayer(winner)
        createPlayer(other)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)

        // Trick 1 opens by itself: "winner" wins with a colored card; the one-card round is then complete.
        click(R.string.trick_choose_winner)
        rule.onAllNodesWithText(winner).onLast().performClick()
        rule.onAllNodesWithText(text(R.string.win_other)).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)

        // Both bids were 0: "other" succeeds (+10), "winner" misses (-10). The scoreboard opens on the ranking.
        click(R.string.tab_table)
        rule.onNodeWithContentDescription(text(R.string.a11y_score_cell, other, 1, "+10", 10)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.a11y_score_cell, winner, 1, "-10", -10)).assertExists()
        rule.onNodeWithText(text(R.string.tab_curve)).assertExists()
    }

    @Test
    fun finishingAGameShowsThePodium() {
        val winner = uniqueName("Cleo")
        val other = uniqueName("Dan")
        click(R.string.home_new_game)
        createPlayer(winner)
        createPlayer(other)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
        click(R.string.trick_choose_winner)
        rule.onAllNodesWithText(winner).onLast().performClick()
        rule.onAllNodesWithText(text(R.string.win_other)).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)

        rule.onNodeWithContentDescription(text(R.string.more)).performClick()
        click(R.string.scoreboard_finish)
        click(R.string.scoreboard_finish)
        // "other" made their bid of 0: they win.
        rule.onNodeWithText(text(R.string.end_winner, other)).assertExists()
    }

    /** New game with two fresh players, up to the first trick, with [cards] cards dealt. */
    private fun startRound(cards: Int, first: String, second: String) {
        click(R.string.home_new_game)
        createPlayer(first)
        createPlayer(second)
        click(R.string.setup_start)
        repeat(cards - 1) {
            rule.onAllNodesWithContentDescription(text(R.string.stepper_increase)).onFirst().performClick()
        }
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
    }

    private fun chooseWinner(name: String) {
        click(R.string.trick_choose_winner)
        rule.onAllNodesWithText(name).onLast().performClick()
    }

    @Test
    fun theFirstTrickOpensRightAfterTheBids() {
        startRound(cards = 1, first = uniqueName("Gil"), second = uniqueName("Hal"))
        rule.onNodeWithText(text(R.string.trick_dialog_new, 1)).assertExists()
    }

    @Test
    fun validatingATrickOpensTheNextOneAndBackReturnsToThePreviousOne() {
        val first = uniqueName("Ida")
        startRound(cards = 2, first = first, second = uniqueName("Jon"))
        chooseWinner(first)
        click(R.string.trick_validate)
        rule.onNodeWithText(text(R.string.trick_dialog_new, 2)).assertExists()
        // Back: trick 1 opens again, to be corrected.
        // The trick screen sits over the round screen: its own back arrow is the last one.
        rule.onAllNodesWithContentDescription(text(R.string.back)).onLast().performClick()
        rule.onNodeWithText(text(R.string.trick_dialog_edit, 1)).assertExists()
    }

    @Test
    fun rascalWagersOnlyBeforeTheLastTrick() {
        val first = uniqueName("Kim")
        startRound(cards = 2, first = first, second = uniqueName("Lou"))
        chooseWinner(first)
        click(R.string.win_pirate)
        click(R.string.pirate_rascal_full)
        rule.onNodeWithText(text(R.string.trick_rascal_wager)).assertExists()
        click(R.string.trick_validate)

        // Trick 2 is the last one: only Harry's power can be used.
        chooseWinner(first)
        click(R.string.win_pirate)
        click(R.string.pirate_rascal_full)
        rule.onNodeWithText(text(R.string.trick_rascal_wager)).assertDoesNotExist()
    }

    @Test
    fun theCardsWonFollowTheWinningCard() {
        val first = uniqueName("Max")
        startRound(cards = 1, first = first, second = uniqueName("Ned"))
        chooseWinner(first)
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
        val first = uniqueName("Eva")
        val second = uniqueName("Fay")
        click(R.string.home_new_game)
        createPlayer(first)
        createPlayer(second)
        click(R.string.setup_start)
        // Change a bid and undo it, then redo it.
        click(R.string.prepare_start_bids)
        rule.onNodeWithText(text(R.string.bids_total, 0, 1)).assertExists()
        // Each bid is set with - / +: raise the first player's bid.
        rule.onAllNodesWithContentDescription(text(R.string.stepper_increase)).onFirst().performClick()
        rule.onNodeWithText(text(R.string.bids_total, 1, 1)).assertExists()
        rule.onNodeWithText("↶").performClick()
        rule.onNodeWithText(text(R.string.bids_total, 0, 1)).assertExists()
        rule.onNodeWithText("↷").performClick()
        rule.onNodeWithText(text(R.string.bids_total, 1, 1)).assertExists()
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
        val name = uniqueName("Rot")
        click(R.string.home_new_game)
        createPlayer(name)
        // The chosen players come after the available profiles, which grow with every test run: scroll to them.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onAllNodesWithText(name).onLast().assertExists()
        rule.activity.runOnUiThread {
            rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.waitForIdle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onAllNodesWithText(name).onLast().assertExists()
        rule.activity.runOnUiThread {
            rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private companion object {
        const val TEN_THOUSAND = 10_000L
    }
}
