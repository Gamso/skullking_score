package com.gamso.skullkingscore.ui.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.SeaMonster

/*
 * Savers of the entries in progress, so that `rememberSaveable` keeps them across a rotation or a process death.
 * Every value is reduced to Bundle-compatible types: ints, longs, strings and lists of them, enums as their names.
 */

/** The constant of [E] named [name], or null for an unknown name (a constant renamed since it was saved). */
internal inline fun <reified E : Enum<E>> enumByName(name: String?): E? =
    enumValues<E>().firstOrNull { it.name == name }

/** The eight captured-card counters, in declaration order. */
internal val BonusSaver: Saver<Bonus, IntArray> = Saver(
    save = {
        intArrayOf(
            it.colored14,
            it.black14,
            it.mermaidsByPirate,
            it.piratesBySkullKing,
            it.skullKingByMermaid,
            it.conCaptured,
            it.eights,
            it.sevens,
        )
    },
    restore = {
        @Suppress("MagicNumber")
        Bonus(
            colored14 = it[0],
            black14 = it[1],
            mermaidsByPirate = it[2],
            piratesBySkullKing = it[3],
            skullKingByMermaid = it[4],
            conCaptured = it[5],
            eights = it[6],
            sevens = it[7],
        )
    },
)

/** A set of strings, as a list. */
internal val StringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

/** A set of sea creatures, as the list of their names. */
internal val SeaMonsterSetSaver: Saver<Set<SeaMonster>, ArrayList<String>> = Saver(
    save = { set -> ArrayList(set.map { it.name }) },
    restore = { names -> names.mapNotNull { enumByName<SeaMonster>(it) }.toSet() },
)

/**
 * Pending Loots and Davy Jones' Lockers, as parallel lists: the Loot players, the Locker players and, for each
 * Locker, the names of its creatures joined by commas.
 */
internal val TrickEventsSaver: Saver<TrickEvents, Any> = listSaver<TrickEvents, Any>(
    save = { events ->
        listOf(
            events.lootPlayerIds.toLongArray(),
            events.chests.map { it.first }.toLongArray(),
            ArrayList(events.chests.map { (_, creatures) -> creatures.joinToString(",") { it.name } }),
        )
    },
    restore = { saved ->
        val chestIds = saved[1] as LongArray

        @Suppress("UNCHECKED_CAST")
        val creatures = saved[2] as List<String>
        TrickEvents(
            lootPlayerIds = (saved[0] as LongArray).toList(),
            chests = chestIds.mapIndexed { index, id ->
                id to creatures[index].split(",").mapNotNull { enumByName<SeaMonster>(it) }.toSet()
            },
        )
    },
)

/** The picker open in the trick dialog: its kind, and the player for the creatures of a Locker. */
internal val TrickEventDialogSaver: Saver<TrickEventDialog?, Any> = listSaver<TrickEventDialog?, Any>(
    save = { dialog ->
        when (dialog) {
            null -> emptyList()
            TrickEventDialog.Winner -> listOf(WINNER)
            TrickEventDialog.LootWho -> listOf(LOOT_WHO)
            TrickEventDialog.ChestWho -> listOf(CHEST_WHO)
            is TrickEventDialog.ChestCreatures -> listOf(CHEST_CREATURES, dialog.playerId)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            WINNER -> TrickEventDialog.Winner
            LOOT_WHO -> TrickEventDialog.LootWho
            CHEST_WHO -> TrickEventDialog.ChestWho
            CHEST_CREATURES -> TrickEventDialog.ChestCreatures(saved[1] as Long)
            else -> null
        }
    },
)

/** The picker open in the live events panel: its kind, and the player it is about. */
internal val EventDialogSaver: Saver<EventDialog?, Any> = listSaver<EventDialog?, Any>(
    save = { dialog ->
        when (dialog) {
            null -> emptyList()
            EventDialog.LootWho -> listOf(LOOT_WHO)
            is EventDialog.LootWinner -> listOf(LOOT_WINNER, dialog.lootPlayerId)
            EventDialog.ChestWho -> listOf(CHEST_WHO)
            is EventDialog.ChestCreatures -> listOf(CHEST_CREATURES, dialog.playerId)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            LOOT_WHO -> EventDialog.LootWho
            LOOT_WINNER -> EventDialog.LootWinner(saved[1] as Long)
            CHEST_WHO -> EventDialog.ChestWho
            CHEST_CREATURES -> EventDialog.ChestCreatures(saved[1] as Long)
            else -> null
        }
    },
)

private const val WINNER = "winner"
private const val LOOT_WHO = "lootWho"
private const val LOOT_WINNER = "lootWinner"
private const val CHEST_WHO = "chestWho"
private const val CHEST_CREATURES = "chestCreatures"
