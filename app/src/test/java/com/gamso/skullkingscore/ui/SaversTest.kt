package com.gamso.skullkingscore.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.ui.components.BonusSaver
import com.gamso.skullkingscore.ui.components.EventDialog
import com.gamso.skullkingscore.ui.components.EventDialogSaver
import com.gamso.skullkingscore.ui.components.SeaMonsterSetSaver
import com.gamso.skullkingscore.ui.components.StringSetSaver
import com.gamso.skullkingscore.ui.components.TrickEventDialog
import com.gamso.skullkingscore.ui.components.TrickEventDialogSaver
import com.gamso.skullkingscore.ui.components.TrickEvents
import com.gamso.skullkingscore.ui.components.TrickEventsSaver
import com.gamso.skullkingscore.ui.components.enumByName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.Test

class SaversTest {
    private val scope = SaverScope { true }

    private fun <T, S : Any> Saver<T, S>.roundTrip(value: T): T? {
        val saved = with(this) { scope.save(value) } ?: return null
        return restore(saved)
    }

    @Test
    fun bonusKeepsItsEightCounters() {
        val bonus = Bonus(
            colored14 = 1,
            black14 = 2,
            mermaidsByPirate = 3,
            piratesBySkullKing = 4,
            skullKingByMermaid = 5,
            conCaptured = 6,
            eights = 7,
            sevens = 8,
        )
        assertIs<IntArray>(with(BonusSaver) { scope.save(bonus) })
        assertEquals(bonus, BonusSaver.roundTrip(bonus))
        assertEquals(Bonus.NONE, BonusSaver.roundTrip(Bonus.NONE))
    }

    @Test
    fun pendingEventsKeepTheirPlayersAndCreatures() {
        val events = TrickEvents(
            lootPlayerIds = listOf(3L, 7L),
            chests = listOf(
                5L to setOf(SeaMonster.KRAKEN, SeaMonster.SPOTTED_RAY),
                9L to setOf(SeaMonster.WHITE_WHALE),
            ),
        )
        assertEquals(events, TrickEventsSaver.roundTrip(events))
        assertEquals(TrickEvents(), TrickEventsSaver.roundTrip(TrickEvents()))
    }

    @Test
    fun aChestWithoutCreatureStaysEmpty() {
        val events = TrickEvents(chests = listOf(5L to emptySet()))
        assertEquals(events, TrickEventsSaver.roundTrip(events))
    }

    @Test
    fun theOpenTrickPickerIsRestored() {
        listOf(
            null,
            TrickEventDialog.Winner,
            TrickEventDialog.LootWho,
            TrickEventDialog.ChestWho,
            TrickEventDialog.ChestCreatures(42L),
        ).forEach { assertEquals(it, TrickEventDialogSaver.roundTrip(it)) }
    }

    @Test
    fun theOpenEventPickerIsRestored() {
        listOf(
            null,
            EventDialog.LootWho,
            EventDialog.LootWinner(4L),
            EventDialog.ChestWho,
            EventDialog.ChestCreatures(8L),
        ).forEach { assertEquals(it, EventDialogSaver.roundTrip(it)) }
    }

    @Test
    fun setsAreSavedAsLists() {
        val creatures = setOf(SeaMonster.WHITE_WHALE, SeaMonster.KRAKEN)
        assertEquals(arrayListOf("WHITE_WHALE", "KRAKEN"), with(SeaMonsterSetSaver) { scope.save(creatures) })
        assertEquals(creatures, SeaMonsterSetSaver.roundTrip(creatures))
        assertEquals(setOf("a", "b"), StringSetSaver.roundTrip(setOf("a", "b")))
    }

    @Test
    fun anUnknownEnumNameIsIgnored() {
        assertEquals(SeaMonster.KRAKEN, enumByName<SeaMonster>("KRAKEN"))
        assertNull(enumByName<SeaMonster>("MEGALODON"))
        assertNull(enumByName<SeaMonster>(null))
    }
}
