package com.gamso.skullkingscore.ui.feature

import androidx.lifecycle.SavedStateHandle
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.domain.stats.ExpansionFilter
import com.gamso.skullkingscore.domain.stats.PlayerCountFilter
import com.gamso.skullkingscore.domain.stats.StatsFilter
import com.gamso.skullkingscore.ui.feature.setup.SetupInput
import com.gamso.skullkingscore.ui.feature.setup.restoreSetupInput
import com.gamso.skullkingscore.ui.feature.setup.saveSetupInput
import com.gamso.skullkingscore.ui.feature.stats.DuelSelection
import com.gamso.skullkingscore.ui.feature.stats.restoreDuelSelection
import com.gamso.skullkingscore.ui.feature.stats.restoreStatsFilter
import com.gamso.skullkingscore.ui.feature.stats.saveDuelSelection
import com.gamso.skullkingscore.ui.feature.stats.saveStatsFilter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The setup inputs survive a process death: what is saved in the handle comes back in a new handle. */
class SavedInputsTest {

    /** A new handle built from the saved values only, as after a process death. */
    private fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any?>(it) })

    /** Only types a Bundle accepts as such: no data class, no enum, no set. */
    private fun SavedStateHandle.assertBundleCompatible() = keys().forEach { key ->
        val value = get<Any?>(key)
        assertTrue(
            value == null || value is String || value is Int || value is Long || value is Boolean ||
                value is LongArray || value is ArrayList<*>,
            "$key holds a ${value?.javaClass}",
        )
    }

    @Test
    fun setupInputIsRestored() {
        val input = SetupInput(
            selectedIds = listOf(4L, 1L, 9L),
            mode = ScoringMode.RASCAL,
            expansionEnabled = true,
            chosenCards = setOf(ExpansionCard.DAVY_JONES_CHEST, ExpansionCard.SPOTTED_RAY),
            firstDealerSeat = 2,
        )
        val handle = SavedStateHandle().apply { saveSetupInput(input) }
        handle.assertBundleCompatible()
        assertEquals(input, handle.afterProcessDeath().restoreSetupInput())
    }

    @Test
    fun nothingSavedRestoresNothing() {
        assertNull(SavedStateHandle().restoreSetupInput())
        assertNull(SavedStateHandle().restoreDuelSelection())
        assertNull(SavedStateHandle().restoreStatsFilter())
    }

    @Test
    fun duelSelectionIsRestored() {
        listOf(DuelSelection(), DuelSelection(ScoringMode.CANNONBALL, first = 3L, second = 5L)).forEach { selection ->
            val handle = SavedStateHandle().apply { saveDuelSelection(selection) }
            handle.assertBundleCompatible()
            assertEquals(selection, handle.afterProcessDeath().restoreDuelSelection())
        }
    }

    @Test
    fun statsFilterIsRestored() {
        val filter = StatsFilter(
            mode = ScoringMode.RASCAL,
            playerCount = PlayerCountFilter.FOUR_FIVE,
            expansion = ExpansionFilter.WITH,
            presentPlayerIds = setOf(2L, 6L),
        )
        val handle = SavedStateHandle().apply { saveStatsFilter(filter) }
        handle.assertBundleCompatible()
        assertEquals(filter, handle.afterProcessDeath().restoreStatsFilter())
    }
}
