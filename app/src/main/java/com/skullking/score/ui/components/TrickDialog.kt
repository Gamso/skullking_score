package com.skullking.score.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.skullking.score.R
import com.skullking.score.core.Bonus
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.WinningCard
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.TrickDraft
import com.skullking.score.ui.labelRes

/** "Destroyed trick" choice: nobody wins the trick (Kraken). */
private const val DESTROYED = -1L

private val WAGERS = listOf(0, 10, 20)
private val ADJUSTMENTS = listOf(-1, 0, 1)

/**
 * Live trick entry: who won it (or destroyed trick), the cards won, and optionally the card it
 * was won with. Pirate-specific settings (Rascal wager, Harry adjustment) only appear
 * when they make sense for the chosen pirate.
 *
 * @param initial trick to correct, or `null` for a new trick
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TrickDialog(
    trickNumber: Int,
    players: List<Player>,
    expansion: Set<ExpansionCard>,
    initial: TrickDraft?,
    onConfirm: (TrickDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember {
        mutableStateOf(if (initial != null && initial.winnerId == null) DESTROYED else initial?.winnerId)
    }
    var bonus by remember { mutableStateOf(initial?.bonus ?: Bonus.NONE) }
    var wager by remember { mutableStateOf(initial?.rascalWager ?: 0) }
    var bidAdjustment by remember { mutableStateOf(initial?.bidAdjustment ?: 0) }
    var card by remember { mutableStateOf(initial?.winningCard) }
    var pirate by remember { mutableStateOf(initial?.winningPirate) }

    var pirateMenu by remember { mutableStateOf(false) }

    val hasWinner = choice != null && choice != DESTROYED
    // The Rascal wager and Harry adjustment only exist if that specific pirate won the trick.
    val showWager = hasWinner && pirate == Pirate.RASCAL
    val showHarry = hasWinner && pirate == Pirate.HARRY
    // "Won with" is mandatory for a won trick, and for a pirate the user must say which one.
    val complete = choice == DESTROYED || (hasWinner && card != null && (card != WinningCard.PIRATE || pirate != null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial != null) R.string.trick_dialog_edit else R.string.trick_dialog_new,
                    trickNumber,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SubTitle(R.string.trick_who_won)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    players.forEach { player ->
                        FilterChip(
                            selected = choice == player.id,
                            // A second tap deselects: "destroyed trick" can then be chosen.
                            onClick = { choice = if (choice == player.id) null else player.id },
                            label = { Text(player.name) },
                            leadingIcon = { PlayerAvatar(player, size = 22.dp) },
                        )
                    }
                    // Meaningless once a winner is chosen: offered only while no player is selected.
                    if (choice == null || choice == DESTROYED) {
                        FilterChip(
                            selected = choice == DESTROYED,
                            onClick = {
                                if (choice == DESTROYED) {
                                    choice = null
                                } else {
                                    choice = DESTROYED
                                    bonus = Bonus.NONE
                                    wager = 0
                                    bidAdjustment = 0
                                    card = null
                                    pirate = null
                                }
                            },
                            label = { Text("💥 " + stringResource(R.string.trick_destroyed)) },
                        )
                    }
                }

                if (hasWinner) {
                    SubTitle(R.string.trick_won_with)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WinningCard.entries
                            .filter { it != WinningCard.FIRST_MATE || ExpansionCard.FIRST_MATE_CON in expansion }
                            .forEach { option ->
                                Box {
                                    FilterChip(
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
                                        label = { Text(winningLabel(option, pirate)) },
                                    )
                                    if (option == WinningCard.PIRATE) {
                                        DropdownMenu(expanded = pirateMenu, onDismissRequest = { pirateMenu = false }) {
                                            Pirate.entries
                                                .filter { it.requires == null || it.requires in expansion }
                                                .forEach { candidate ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(candidate.labelRes())) },
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
                                FilterChip(
                                    selected = wager == value,
                                    onClick = { wager = value },
                                    label = { Text(value.toString()) },
                                )
                            }
                        }
                    }
                    if (showHarry) {
                        OptionRow(R.string.trick_harry_title) {
                            ADJUSTMENTS.forEach { delta ->
                                FilterChip(
                                    selected = bidAdjustment == delta,
                                    onClick = { bidAdjustment = delta },
                                    label = {
                                        Text(
                                            when {
                                                delta > 0 -> "+1"
                                                delta < 0 -> "−1"
                                                else -> stringResource(R.string.trick_harry_none)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    SubTitle(R.string.trick_captured)
                    BonusCounterList(bonus = bonus, expansion = expansion, onChange = { bonus = it }, compact = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        TrickDraft(
                            winnerId = choice.takeIf { it != DESTROYED },
                            bonus = bonus,
                            rascalWager = wager,
                            bidAdjustment = bidAdjustment,
                            winningCard = card,
                            winningPirate = pirate,
                        ),
                    )
                },
                enabled = complete,
            ) { Text(stringResource(R.string.trick_validate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
    return if (option == WinningCard.PIRATE && pirate != null) "$base · ${stringResource(pirate.labelRes())}" else base
}
