package com.gamso.skullkingscore

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Smoke tests: each one plays a short game through the screens, then opens a screen that no other test reaches
 * (statistics, history, round correction, sharing, help, players) and checks that it shows its content. They catch
 * a screen that crashes, such as a format that does not accept a half point. Each test runs on its own empty
 * in-memory database and settings ([TestDataModule]), and waits for the screen to show the result of a write.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class SmokeTest {

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

    /** A one-card round between [winner] and [other], both bidding 0: [winner] takes the trick. Ends on the scoreboard. */
    private fun playOneRound(winner: String, other: String) {
        click(R.string.home_new_game)
        createPlayer(winner)
        createPlayer(other)
        click(R.string.setup_start)
        click(R.string.prepare_start_bids)
        click(R.string.bids_validate)
        click(R.string.trick_choose_winner)
        // The trick screen sits over the round screen: its player buttons are the last ones.
        rule.onAllNodesWithText(winner).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)
    }

    /** [playOneRound], then the game is ended: ends on the podium, where [other] wins with their zero bid. */
    private fun playAGame(winner: String, other: String) {
        playOneRound(winner, other)
        rule.waitUntilExactlyOneExists(hasContentDescription(text(R.string.more)), TIMEOUT_MS)
        rule.onNodeWithContentDescription(text(R.string.more)).performClick()
        click(R.string.scoreboard_finish)
        click(R.string.scoreboard_finish)
        expect(hasText(text(R.string.end_winner, other)))
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
        playAGame(ANA, BEA)
        backHome()

        click(R.string.home_stats)
        rule.onNode(hasText(text(R.string.stats_search)) and hasSetTextAction()).performTextInput(ANA)
        // The statistics are computed in the background: wait for the player's card.
        expect(hasContentDescription(text(R.string.stats_share_player, ANA)))
        // The search field holds the name too: the player's card is the last node with it.
        rule.onAllNodesWithText(ANA).onLast().assertExists()
        click(R.string.stats_tab_cards)
        rule.onNodeWithText(text(R.string.cards_by_card_title)).assertExists()

        click(R.string.stats_tab_players)
        click(R.string.duel_title)
        // The players are listed twice (first and second player): on a small screen they are below the fold.
        rule.onAllNodesWithText(ANA).onFirst().performScrollTo().performClick()
        rule.onAllNodesWithText(BEA).onLast().performScrollTo().performClick()
        // One game together is below the minimum for a head-to-head: the screen says so.
        expect(hasText(text(R.string.duel_not_enough)))
    }

    @Test
    fun aFinishedGameOpensFromTheHistoryWithItsScoreboardAndSharing() {
        playAGame(ANA, BEA)
        backHome()

        click(R.string.home_history)
        // The only game is listed: its card shows its winner, tapping it expands the game.
        rule.waitUntilAtLeastOneExists(hasText(BEA, substring = true), TIMEOUT_MS)
        rule.onAllNodesWithText(BEA, substring = true).onFirst().performClick()
        click(R.string.history_open)
        expect(hasText(text(R.string.end_winner, BEA)))

        click(R.string.end_see_scoreboard)
        click(R.string.tab_table)
        click(R.string.tab_curve)
        expect(hasText(text(R.string.curve_axis_rounds, 1)))
        rule.onAllNodesWithContentDescription(text(R.string.share_action)).onLast().performClick()
        // The image is drawn in the background: wait for its preview (slow on the CI emulator).
        rule.waitUntil(IMAGE_TIMEOUT_MS) {
            rule.onAllNodesWithContentDescription(text(R.string.share_preview)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aRoundCanBeCorrectedFromTheScoreboard() {
        playOneRound(ANA, BEA)

        click(R.string.tab_table)
        val cell = hasContentDescription(text(R.string.a11y_score_cell, BEA, 1, "+10", 10))
        expect(cell)
        rule.onNode(cell).performClick()
        click(R.string.detail_correct)
        expect(hasText(text(R.string.results_title, 1)))
        expect(hasText(text(R.string.results_tap_trick)))
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
        click(R.string.home_players)
        createPlayer(ANA)
        rule.onNodeWithText(ANA).performClick()
        rule.onNodeWithText(text(R.string.player_edit)).assertExists()
        click(R.string.cancel)
        expect(hasText(ANA) and !hasSetTextAction())
    }

    private companion object {
        const val ANA = "Ana"
        const val BEA = "Bea"
        const val MAX_BACK = 6
        const val TIMEOUT_MS = 5_000L

        const val IMAGE_TIMEOUT_MS = 20_000L
    }
}
