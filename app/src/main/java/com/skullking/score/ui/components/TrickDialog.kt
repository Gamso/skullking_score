package com.skullking.score.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skullking.score.R
import com.skullking.score.core.Bonus
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.model.TrickDraft
import com.skullking.score.ui.emoji
import com.skullking.score.ui.fullLabelRes
import com.skullking.score.ui.labelRes

/** "Destroyed trick" choice: nobody wins the trick (Kraken). */
private const val DESTROYED = -1L

private val WAGERS = listOf(0, 10, 20)
private val ADJUSTMENTS = listOf(-1, 0, 1)
private val CHIP_SPACING = Arrangement.spacedBy(6.dp)

/** Loots and Davy Jones' Lockers played during the trick being entered. */
data class TrickEvents(
    val lootPlayerIds: List<Long> = emptyList(),
    val chests: List<Pair<Long, Set<SeaMonster>>> = emptyList(),
)

private sealed interface TrickEventDialog {
    data object Winner : TrickEventDialog
    data object LootWho : TrickEventDialog
    data object ChestWho : TrickEventDialog
    data class ChestCreatures(val playerId: Long) : TrickEventDialog
}

/**
 * Live trick entry: who won it (or destroyed trick), the cards won, the card it was won with, and the
 * Loots / Davy Jones' Lockers played in it. Everything is shown at once: the user does not have to pick
 * the winner first. Pirate-specific settings (Rascal wager, Harry adjustment) only appear when they make
 * sense for the chosen pirate.
 *
 * @param initial trick to correct, or `null` for a new trick
 * @param recordedEvents events already recorded for this trick, shown as a reminder
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TrickDialog(
    trickNumber: Int,
    players: List<Player>,
    expansion: Set<ExpansionCard>,
    initial: TrickDraft?,
    onConfirm: (TrickDraft, TrickEvents) -> Unit,
    onDismiss: () -> Unit,
    recordedEvents: List<String> = emptyList(),
) {
    var choice by remember {
        mutableStateOf(if (initial != null && initial.winnerId == null) DESTROYED else initial?.winnerId)
    }
    var bonus by remember { mutableStateOf(initial?.bonus ?: Bonus.NONE) }
    var wager by remember { mutableStateOf(initial?.rascalWager ?: 0) }
    var bidAdjustment by remember { mutableStateOf(initial?.bidAdjustment ?: 0) }
    // "Other" (a numbered card) is the most common case: selected by default.
    var card by remember { mutableStateOf<WinningCard?>(initial?.winningCard ?: WinningCard.OTHER) }
    var pirate by remember { mutableStateOf(initial?.winningPirate) }
    var events by remember { mutableStateOf(TrickEvents()) }

    var pirateMenu by remember { mutableStateOf(false) }
    var eventDialog by remember { mutableStateOf<TrickEventDialog?>(null) }

    val destroyed = choice == DESTROYED
    val hasWinner = choice != null && !destroyed
    // The Rascal wager and Harry adjustment only exist if that specific pirate won the trick.
    val showWager = !destroyed && pirate == Pirate.RASCAL
    val showHarry = !destroyed && pirate == Pirate.HARRY
    // "Won with" is mandatory for a won trick, and for a pirate the user must say which one.
    val complete = destroyed || (hasWinner && card != null && (card != WinningCard.PIRATE || pirate != null))
    val names = players.associate { it.id to it.name }

    val confirm = {
        onConfirm(
            TrickDraft(
                winnerId = choice.takeIf { it != DESTROYED },
                bonus = bonus,
                rascalWager = wager,
                bidAdjustment = bidAdjustment,
                winningCard = card.takeIf { hasWinner },
                winningPirate = pirate.takeIf { hasWinner },
            ),
            events,
        )
    }

    // Full screen rather than a dialog: the whole width is used, and the validation stays in sight at the bottom.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SkullScaffold(
            title = stringResource(
                if (initial != null) R.string.trick_dialog_edit else R.string.trick_dialog_new,
                trickNumber,
            ),
            onBack = onDismiss,
            bottomBar = {
                ActionBar(
                    primaryText = stringResource(R.string.trick_validate),
                    primaryEnabled = complete,
                    onPrimary = confirm,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SubTitle(R.string.trick_who_won)
                FlowRow(horizontalArrangement = CHIP_SPACING, verticalArrangement = CHIP_SPACING) {
                    // One button opens the list of players: the screen does not grow with the table.
                    val winner = players.firstOrNull { it.id == choice }
                    CompactChip(
                        text = winner?.name ?: stringResource(R.string.trick_choose_winner),
                        selected = winner != null,
                        onClick = { eventDialog = TrickEventDialog.Winner },
                        leading = winner?.let { { PlayerAvatar(it, size = 20.dp) } },
                    )
                    // Nobody wins a destroyed trick: it is an alternative to the players.
                    CompactChip(
                        text = "💥 " + stringResource(R.string.trick_destroyed_button),
                        selected = destroyed,
                        onClick = {
                            if (destroyed) {
                                choice = null
                                card = WinningCard.OTHER
                            } else {
                                choice = DESTROYED
                                events = TrickEvents()
                                bonus = Bonus.NONE
                                wager = 0
                                bidAdjustment = 0
                                card = null
                                pirate = null
                            }
                        },
                    )
                }
                if (destroyed) {
                    Text(
                        stringResource(R.string.trick_destroyed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // A destroyed trick has no winner: no event, no winning card, no captured card. Loot and
                // Davy Jones' Locker come right under the winner: they are played during the trick.
                if (!destroyed) {
                    SubTitle(R.string.trick_events)
                    FlowRow(horizontalArrangement = CHIP_SPACING, verticalArrangement = CHIP_SPACING) {
                        CompactChip(
                            text = "💰 " + stringResource(R.string.event_loot_button),
                            selected = false,
                            onClick = { eventDialog = TrickEventDialog.LootWho },
                        )
                        if (ExpansionCard.DAVY_JONES_CHEST in expansion) {
                            CompactChip(
                                text = stringResource(R.string.event_davy_jones_button),
                                selected = false,
                                onClick = { eventDialog = TrickEventDialog.ChestWho },
                            )
                        }
                    }
                    recordedEvents.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    events.lootPlayerIds.forEachIndexed { index, id ->
                        PendingEvent(stringResource(R.string.trick_loot_by, names[id].orEmpty())) {
                            events = events.copy(
                                lootPlayerIds = events.lootPlayerIds.filterIndexed { i, _ -> i != index },
                            )
                        }
                    }
                    events.chests.forEachIndexed { index, (id, creatures) ->
                        val what = creatures.map { monster ->
                            listOfNotNull(monster.emoji(), stringResource(monster.labelRes())).joinToString(" ")
                        }.joinToString(", ")
                        PendingEvent(stringResource(R.string.event_davy_jones_line, names[id].orEmpty(), what)) {
                            events = events.copy(chests = events.chests.filterIndexed { i, _ -> i != index })
                        }
                    }

                    SubTitle(R.string.trick_won_with)
                    FlowRow(horizontalArrangement = CHIP_SPACING, verticalArrangement = CHIP_SPACING) {
                        WinningCard.entries
                            .filter { it != WinningCard.FIRST_MATE || ExpansionCard.FIRST_MATE_CON in expansion }
                            .forEach { option ->
                                Box {
                                    CompactChip(
                                        text = winningLabel(option, pirate),
                                        selected = card == option,
                                        onClick = {
                                            card = option
                                            if (option == WinningCard.PIRATE) {
                                                pirateMenu = true
                                            } else {
                                                // Only pirates have specific settings.
                                                pirate = null
                                                wager = 0
                                                bidAdjustment = 0
                                            }
                                        },
                                    )
                                    if (option == WinningCard.PIRATE) {
                                        DropdownMenu(expanded = pirateMenu, onDismissRequest = { pirateMenu = false }) {
                                            Pirate.entries
                                                .filter { it.requires == null || it.requires in expansion }
                                                .forEach { candidate ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(candidate.fullLabelRes())) },
                                                        onClick = {
                                                            pirate = candidate
                                                            pirateMenu = false
                                                            if (candidate != Pirate.RASCAL) wager = 0
                                                            if (candidate != Pirate.HARRY) bidAdjustment = 0
                                                        },
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                    }

                    if (showWager) {
                        OptionRow(R.string.trick_rascal_wager) {
                            WAGERS.forEach { value ->
                                CompactChip(
                                    text = value.toString(),
                                    selected = wager == value,
                                    onClick = { wager = value },
                                )
                            }
                        }
                    }
                    if (showHarry) {
                        OptionRow(R.string.trick_harry_title) {
                            ADJUSTMENTS.forEach { delta ->
                                CompactChip(
                                    text = when {
                                        delta > 0 -> "+1"
                                        delta < 0 -> "−1"
                                        else -> stringResource(R.string.trick_harry_none)
                                    },
                                    selected = bidAdjustment == delta,
                                    onClick = { bidAdjustment = delta },
                                )
                            }
                        }
                    }

                    SubTitle(R.string.trick_captured)
                    BonusCounterList(bonus = bonus, expansion = expansion, onChange = { bonus = it }, compact = true)
                }
            }
        }
    }

    when (val d = eventDialog) {
        null -> Unit

        TrickEventDialog.Winner -> PlayerPickerDialog(
            title = stringResource(R.string.trick_who_won),
            players = players,
            onPick = {
                choice = it.id
                if (card == null) card = WinningCard.OTHER
                eventDialog = null
            },
            onDismiss = { eventDialog = null },
        )

        TrickEventDialog.LootWho -> PlayerPickerDialog(
            title = stringResource(R.string.loot_who_played),
            players = players,
            onPick = {
                events = events.copy(lootPlayerIds = events.lootPlayerIds + it.id)
                eventDialog = null
            },
            onDismiss = { eventDialog = null },
        )

        TrickEventDialog.ChestWho -> PlayerPickerDialog(
            title = stringResource(R.string.chest_who_played),
            players = players,
            onPick = { eventDialog = TrickEventDialog.ChestCreatures(it.id) },
            onDismiss = { eventDialog = null },
        )

        is TrickEventDialog.ChestCreatures -> CreaturePickerDialog(
            available = buildList {
                add(SeaMonster.KRAKEN)
                add(SeaMonster.WHITE_WHALE)
                if (ExpansionCard.SPOTTED_RAY in expansion) add(SeaMonster.SPOTTED_RAY)
            },
            onConfirm = {
                events = events.copy(chests = events.chests + (d.playerId to it))
                eventDialog = null
            },
            onDismiss = { eventDialog = null },
        )
    }
}

/** An event added in the dialog, not recorded yet: it can still be removed. */
@Composable
private fun PendingEvent(text: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRemove) { Text(stringResource(R.string.cancel_event)) }
    }
}

@Composable
private fun SubTitle(@StringRes res: Int) {
    Text(
        stringResource(res),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** An option on a single line: its name, then its choices one after the other (wrapping if needed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(@StringRes label: Int, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

/** Chip label: for "Pirate", the chosen pirate is shown once selected. */
@Composable
private fun winningLabel(option: WinningCard, pirate: Pirate?): String {
    val base = stringResource(option.labelRes())
    return if (option == WinningCard.PIRATE && pirate != null) {
        "$base · ${stringResource(pirate.fullLabelRes())}"
    } else {
        base
    }
}

/** Events already recorded during trick [number], recalled when the trick is corrected. */
@Composable
fun trickEventLines(details: RoundDetails, number: Int, names: Map<Long, String>): List<String> = buildList {
    details.activeLoots.filter { it.trickNumber == number }.forEach {
        add(stringResource(R.string.trick_loot_by, names[it.lootPlayerId].orEmpty()))
    }
    details.activeCaptures.filter { it.trickNumber == number }.forEach { capture ->
        val what = capture.creatures.map {
            listOfNotNull(it.emoji(), stringResource(it.labelRes())).joinToString(" ")
        }.joinToString(", ")
        add(stringResource(R.string.event_davy_jones_line, names[capture.playerId].orEmpty(), what))
    }
}

/** Short label of a recorded trick: "3 · Alice · Pirate". */
@Composable
fun trickLabel(trick: Trick, names: Map<Long, String>): String {
    val winner = trick.winnerId?.let { names[it] } ?: stringResource(R.string.trick_destroyed_short)
    val with = trick.winningPirate?.let { stringResource(it.labelRes()) }
        ?: trick.winningCard?.let { stringResource(it.labelRes()) }
    return listOfNotNull("${trick.number}", winner, with).joinToString(" · ")
}
