package com.skullking.score.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.skullking.score.R
import com.skullking.score.core.Bonus
import com.skullking.score.core.CaptureRules
import com.skullking.score.core.DeckRules
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
 * sense for the chosen pirate, or for the First Mate Con, who may use the powers of the pirates he captures.
 * On the last trick of the round, only Harry the Giant's power can be used.
 *
 * @param initial trick to correct, or `null` for a new trick
 * @param recordedEvents events already recorded for this trick, shown as a reminder
 * @param onPrevious back goes to the previous trick when set, otherwise it closes the screen
 * @param lootsPlayed Loots already recorded in the round: the deck holds only two
 * @param chestsPlayed Davy Jones' Lockers already recorded in the round: the deck holds only one
 * @param eventPlayerIds players who already played a Loot or a Locker in this trick: one card per player
 * @param chestPlayerIds players who already played a Locker in this trick: a Locker never wins the trick
 * @param header shown above the entry, such as the bids against the tricks won
 * @param roundState content of the round state dialog, opened from the top bar
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
    isLastTrick: Boolean = false,
    lootsPlayed: Int = 0,
    chestsPlayed: Int = 0,
    eventPlayerIds: Set<Long> = emptySet(),
    chestPlayerIds: Set<Long> = emptySet(),
    onPrevious: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    roundState: (@Composable () -> Unit)? = null,
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
    var showRoundState by remember { mutableStateOf(false) }

    val destroyed = choice == DESTROYED
    val hasWinner = choice != null && !destroyed
    // The Rascal wager and Harry adjustment only exist if that pirate won the trick, or if the First Mate Con
    // won it and chooses to use the powers of the pirates he captured. Only Harry's power works on the last trick.
    val firstMate = !destroyed && card == WinningCard.FIRST_MATE
    val showWager = !destroyed && !isLastTrick && (pirate == Pirate.RASCAL || firstMate)
    val showHarry = !destroyed && (pirate == Pirate.HARRY || firstMate)
    // "Won with" is mandatory for a won trick, and for a pirate the user must say which one.
    val complete = destroyed || (hasWinner && card != null && (card != WinningCard.PIRATE || pirate != null))
    val names = players.associate { it.id to it.name }
    // The cards won are limited to those the winning card can capture.
    val allowedBonus = CaptureRules.allowed(card.takeIf { hasWinner })
    // One card per player in a trick: whoever played a Loot or a Locker plays nothing else, and the Locker never wins.
    val busy = eventPlayerIds + events.lootPlayerIds + events.chests.map { it.first }
    val chestPlayers = chestPlayerIds + events.chests.map { it.first }

    val confirm = {
        onConfirm(
            TrickDraft(
                winnerId = choice.takeIf { it != DESTROYED },
                bonus = bonus.keepOnly(allowedBonus, expansion),
                rascalWager = if (showWager) wager else 0,
                bidAdjustment = if (showHarry) bidAdjustment else 0,
                winningCard = card.takeIf { hasWinner },
                winningPirate = pirate.takeIf { hasWinner },
            ),
            events,
        )
    }

    // Full screen rather than a dialog: the whole width is used, and the validation stays in sight at the bottom.
    val back = onPrevious ?: onDismiss
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The dialog has its own window: its status bar icons must follow the theme, like the activity's.
        val view = LocalView.current
        val lightBars = !isSystemInDarkTheme()
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBars
            }
        }
        SkullScaffold(
            title = stringResource(
                if (initial != null) R.string.trick_dialog_edit else R.string.trick_dialog_new,
                trickNumber,
            ),
            onBack = back,
            actions = {
                if (roundState != null) {
                    IconButton(onClick = { showRoundState = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.trick_round_state),
                        )
                    }
                }
            },
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
                header?.invoke()
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
                            enabled = lootsPlayed + events.lootPlayerIds.size < DeckRules.MAX_LOOT,
                        )
                        if (ExpansionCard.DAVY_JONES_CHEST in expansion) {
                            CompactChip(
                                text = stringResource(R.string.event_davy_jones_button),
                                selected = false,
                                onClick = { eventDialog = TrickEventDialog.ChestWho },
                                enabled = chestsPlayed + events.chests.size < DeckRules.MAX_DAVY_JONES,
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

                    if (firstMate && (showWager || showHarry)) {
                        Text(
                            stringResource(R.string.trick_con_powers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    BonusCounterList(
                        bonus = bonus,
                        expansion = expansion,
                        onChange = { bonus = it },
                        compact = true,
                        allowed = allowedBonus,
                    )
                }
            }
        }
    }

    if (showRoundState && roundState != null) {
        AlertDialog(
            onDismissRequest = { showRoundState = false },
            title = { Text(stringResource(R.string.trick_round_state)) },
            text = { roundState() },
            confirmButton = {
                TextButton(onClick = { showRoundState = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    when (val d = eventDialog) {
        null -> Unit

        TrickEventDialog.Winner -> PlayerPickerDialog(
            title = stringResource(R.string.trick_who_won),
            players = players.filter { it.id !in chestPlayers },
            onPick = {
                choice = it.id
                if (card == null) card = WinningCard.OTHER
                eventDialog = null
            },
            onDismiss = { eventDialog = null },
        )

        TrickEventDialog.LootWho -> PlayerPickerDialog(
            title = stringResource(R.string.loot_who_played),
            players = players.filter { it.id !in busy },
            onPick = {
                events = events.copy(lootPlayerIds = events.lootPlayerIds + it.id)
                eventDialog = null
            },
            onDismiss = { eventDialog = null },
        )

        TrickEventDialog.ChestWho -> PlayerPickerDialog(
            title = stringResource(R.string.chest_who_played),
            players = players.filter { it.id !in busy && it.id != choice },
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
