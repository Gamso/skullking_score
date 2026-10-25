package com.skullking.score

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parcours de bout en bout sur l'application réelle (base de données de l'appareil de test) : les noms de joueurs
 * sont uniques pour que les tests ne dépendent pas de l'état laissé par une exécution précédente.
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
        rule.onNode(hasSetTextAction()).performTextInput(name)
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

        // Pli 1 : « winner » gagne avec une carte de couleur ; la manche d'une carte est alors complète.
        click(R.string.play_enter_trick, 1)
        rule.onAllNodesWithText(winner).onLast().performClick()
        rule.onAllNodesWithText(text(R.string.win_other)).onLast().performClick()
        click(R.string.trick_validate)
        click(R.string.play_validate_round)

        // Les deux annonces étaient à 0 : « other » réussit (+10), « winner » rate (−10).
        rule.onNodeWithContentDescription(text(R.string.a11y_score_cell, other, 1, "+10", 10)).assertExists()
        rule.onNodeWithContentDescription(text(R.string.a11y_score_cell, winner, 1, "-10", -10)).assertExists()
        rule.onNodeWithText(text(R.string.tab_curve)).assertExists()
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
        rule.onAllNodesWithText(name).onLast().assertExists()
        rule.activity.runOnUiThread {
            rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private companion object {
        const val TEN_THOUSAND = 10_000L
    }
}
