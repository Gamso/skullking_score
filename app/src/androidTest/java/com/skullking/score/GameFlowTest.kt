package com.skullking.score

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

    private fun createPlayer(name: String) {
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

        // Trick 1: "winner" wins with a colored card; the one-card round is then complete.
        click(R.string.play_enter_trick, 1)
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
        click(R.string.play_enter_trick, 1)
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

    @Test
    fun aBidChangeCanBeUndoneAndRedone() {
        val first = uniqueName("Eva")
        val second = uniqueName("Fay")
        click(R.string.home_new_game)
        createPlayer(first)
        createPlayer(second)
        click(R.string.setup_start)
        // Three cards so that bid 2 exists, then change a bid and undo it.
        click(R.string.prepare_start_bids)
        rule.onNodeWithText(text(R.string.bids_total, 0, 1)).assertExists()
        rule.onAllNodesWithText("1").onFirst().performClick()
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
        rule.onAllNodesWithText(name).onLast().assertExists()
        rule.activity.runOnUiThread {
            rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        rule.waitForIdle()
        // The chosen players come after the available profiles: in landscape the list must scroll to them.
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
