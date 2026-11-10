package com.gamso.skullkingscore

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests: each one plays a short game through the screens, then opens a screen that no other test reaches
 * (statistics, history, round correction, sharing, help, players) and checks that it shows its content. They catch
 * a screen that crashes, such as a format that does not accept a half point.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any): String = rule.activity.getString(id, *args)

    private fun click(id: Int, vararg args: Any) {
        rule.onNodeWithText(text(id, *args)).performClick()
    }

    private fun uniqueName(prefix: String) = prefix + (System.currentTimeMillis() % TEN_THOUSAND)

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

    /** A one-card round between [winner] and [other], both bidding 0: [winner] takes the trick. Ends on the scoreboard. */
    private fun playOneRound(winner: String, other: String) {
        click(R.string.home_new_game)
        createPlayer(winner)
        createPlayer(other)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
        click(R.string.trick_choose_winner)
        rule.onAllNodesWithText(winner).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)
    }

    /** [playOneRound], then the game is ended: ends on the podium, where [other] wins with their zero bid. */
    private fun playAGame(winner: String, other: String) {
        playOneRound(winner, other)
        rule.onNodeWithContentDescription(text(R.string.more)).performClick()
        click(R.string.scoreboard_finish)
        click(R.string.scoreboard_finish)
        rule.onNodeWithText(text(R.string.end_winner, other)).assertExists()
    }

    /** Back to the home screen, whatever the depth of the current screen. */
    private fun backHome() {
        repeat(MAX_BACK) {
            if (rule.onAllNodesWithText(text(R.string.home_history)).fetchSemanticsNodes().isNotEmpty()) return
            Espresso.pressBack()
            rule.waitForIdle()
        }
    }

    @Test
    fun statisticsTabsAndHeadToHeadOpen() {
        val first = uniqueName("Sam")
        val second = uniqueName("Tia")
        playAGame(first, second)
        backHome()

        click(R.string.home_stats)
        rule.onNode(hasText(text(R.string.stats_search)) and hasSetTextAction()).performTextInput(first)
        // The search field holds the name too: the player's card is the last node with it.
        rule.onAllNodesWithText(first).onLast().assertExists()
        rule.onNodeWithContentDescription(text(R.string.stats_share_player, first)).assertExists()
        click(R.string.stats_tab_cards)
        rule.onNodeWithText(text(R.string.cards_by_card_title)).assertExists()

        click(R.string.stats_tab_players)
        click(R.string.duel_title)
        // The players are listed twice (first and second player): on a small screen they are below the fold.
        rule.onAllNodesWithText(first).onFirst().performScrollTo().performClick()
        rule.onAllNodesWithText(second).onLast().performScrollTo().performClick()
        // One game together is below the minimum for a head-to-head: the screen says so.
        rule.onNodeWithText(text(R.string.duel_not_enough)).assertExists()
    }

    @Test
    fun aFinishedGameOpensFromTheHistoryWithItsScoreboardAndSharing() {
        val first = uniqueName("Ugo")
        val second = uniqueName("Val")
        playAGame(first, second)
        backHome()

        click(R.string.home_history)
        rule.onNode(hasText(text(R.string.history_search)) and hasSetTextAction()).performTextInput(first)
        // The card of the game shows its winner: tapping it expands the game.
        rule.onAllNodesWithText(second, substring = true).onFirst().performClick()
        click(R.string.history_open)
        rule.onNodeWithText(text(R.string.end_winner, second)).assertExists()

        click(R.string.end_see_scoreboard)
        click(R.string.tab_table)
        click(R.string.tab_curve)
        rule.onNodeWithText(text(R.string.curve_axis_rounds, 1)).assertExists()
        rule.onAllNodesWithContentDescription(text(R.string.share_action)).onLast().performClick()
        // The image is drawn in the background: wait for its preview (slow on the CI emulator).
        rule.waitUntil(IMAGE_TIMEOUT_MS) {
            rule.onAllNodesWithContentDescription(text(R.string.share_preview)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aRoundCanBeCorrectedFromTheScoreboard() {
        val first = uniqueName("Wes")
        val second = uniqueName("Xia")
        playOneRound(first, second)

        click(R.string.tab_table)
        rule.onNodeWithContentDescription(text(R.string.a11y_score_cell, second, 1, "+10", 10)).performClick()
        click(R.string.detail_correct)
        rule.onNodeWithText(text(R.string.results_title, 1)).assertExists()
        rule.onNodeWithText(text(R.string.results_tap_trick)).assertExists()
    }

    @Test
    fun theOpenSourceLicensesOpenFromAbout() {
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        // About is at the bottom of the settings: scroll to the entry.
        rule.onNodeWithText(text(R.string.licenses_title)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.licenses_intro)).assertExists()
    }

    @Test
    fun everyHelpTabOpensAndTheSearchFindsTheKraken() {
        rule.onNodeWithContentDescription(text(R.string.help)).performClick()
        rule.onNodeWithText(text(R.string.help_hierarchy_title)).assertExists()
        click(R.string.help_tab_scoring)
        rule.onNodeWithText(text(R.string.help_points_title)).assertExists()
        click(R.string.help_tab_expansion)
        rule.onNodeWithText(text(R.string.help_expansion_title)).assertExists()
        click(R.string.help_tab_faq)
        rule.onNodeWithText(text(R.string.help_official_link)).assertExists()

        rule.onNode(hasText(text(R.string.help_search)) and hasSetTextAction()).performTextInput("Kraken")
        rule.onNodeWithText(text(R.string.help_no_results, "Kraken")).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.help_special_title)).assertExists()
    }

    @Test
    fun aPlayerIsCreatedAndOpensForEditingFromThePlayersScreen() {
        val name = uniqueName("Yan")
        click(R.string.home_players)
        createPlayer(name, inSetup = false)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        rule.onNodeWithText(name).performClick()
        rule.onNodeWithText(text(R.string.player_edit)).assertExists()
        click(R.string.cancel)
        rule.onNodeWithText(name).assertExists()
    }

    private companion object {
        const val TEN_THOUSAND = 10_000L
        const val MAX_BACK = 6

        const val IMAGE_TIMEOUT_MS = 20_000L
    }
}
