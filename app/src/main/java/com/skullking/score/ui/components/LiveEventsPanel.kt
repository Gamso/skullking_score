package com.skullking.score.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.skullking.score.R
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.SeaMonster
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.ui.emoji
import com.skullking.score.ui.labelRes

private sealed interface EventDialog {
    data object LootWho : EventDialog
    data class LootWinner(val lootPlayerId: Long) : EventDialog
    data object ChestWho : EventDialog
    data class ChestCreatures(val playerId: Long) : EventDialog
}

private sealed interface LogItem {
    val text: String
    val createdAt: Long
    val cancelled: Boolean

    data class Loot(
        val id: Long,
        override val text: String,
        override val createdAt: Long,
        override val cancelled: Boolean,
    ) : LogItem

    data class Chest(
        val id: Long,
        override val text: String,
        override val createdAt: Long,
        override val cancelled: Boolean,
    ) : LogItem
}

/**
 * Live events: a Loot is recorded in two taps (who played it, who won the trick), as is a Davy Jones'
 * Locker (who played it, which creatures). The log keeps a trace of cancelled events.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
fun LiveEventsPanel(
    game: Game,
    round: RoundDetails,
    onAddLoot: (lootPlayerId: Long, trickWinnerId: Long?) -> Unit,
    onAddDavyJones: (playerId: Long, creatures: Set<SeaMonster>) -> Unit,
    onCancelLoot: (Long) -> Unit,
    onCancelDavyJones: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** False during play: the Loot winner is filled in when the trick is entered. */
    askWinner: Boolean = true,
    /** False when the events are entered elsewhere (trick dialog): only the log is shown. */
    showButtons: Boolean = true,
) {
    var dialog by remember { mutableStateOf<EventDialog?>(null) }
    val hasChest = ExpansionCard.DAVY_JONES_CHEST in game.expansionCards
    val names = game.players.associate { it.id to it.name }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showButtons) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { dialog = EventDialog.LootWho },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) { Text(stringResource(R.string.event_loot_button), style = MaterialTheme.typography.titleMedium) }
                if (hasChest) {
                    Button(
                        onClick = { dialog = EventDialog.ChestWho },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Text(
                            stringResource(R.string.event_davy_jones_button),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        val unknown = stringResource(R.string.unknown_player)
        val cancelledLabel = stringResource(R.string.event_cancelled)
        val items = buildList {
            round.loots.forEach {
                val prefix = it.trickNumber?.let { n -> stringResource(R.string.event_trick_prefix, n) }.orEmpty()
                val player = names[it.lootPlayerId] ?: unknown
                val text = if (it.trickWinnerId == null) {
                    stringResource(R.string.event_loot_pending, player)
                } else {
                    stringResource(R.string.event_loot_line, player, names[it.trickWinnerId] ?: unknown)
                }
                add(LogItem.Loot(it.id, prefix + text, it.createdAt, !it.isActive))
            }
            round.captures.forEach { capture ->
                val creatures = capture.creatures.map {
                    listOfNotNull(it.emoji(), stringResource(it.labelRes())).joinToString(" ")
                }.joinToString(", ")
                val prefix = capture.trickNumber?.let { n -> stringResource(R.string.event_trick_prefix, n) }.orEmpty()
                val line = stringResource(R.string.event_davy_jones_line, names[capture.playerId] ?: unknown, creatures)
                add(LogItem.Chest(capture.id, prefix + line, capture.createdAt, !capture.isActive))
            }
        }.sortedBy { it.createdAt }

        Text(
            stringResource(R.string.event_log),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.event_log_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (item.cancelled) "${item.text} ($cancelledLabel)" else item.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.cancelled) TextDecoration.LineThrough else null,
                    color = if (item.cancelled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (!item.cancelled) {
                    TextButton(
                        onClick = {
                            when (item) {
                                is LogItem.Loot -> onCancelLoot(item.id)
                                is LogItem.Chest -> onCancelDavyJones(item.id)
                            }
                        },
                    ) { Text(stringResource(R.string.cancel_event)) }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit

        EventDialog.LootWho -> PlayerPickerDialog(
            title = stringResource(R.string.loot_who_played),
            players = game.players,
            onPick = {
                if (askWinner) {
                    dialog = EventDialog.LootWinner(it.id)
                } else {
                    onAddLoot(it.id, null)
                    dialog = null
                }
            },
            onDismiss = { dialog = null },
        )

        is EventDialog.LootWinner -> PlayerPickerDialog(
            title = stringResource(R.string.loot_who_won),
            players = game.players,
            onPick = {
                onAddLoot(d.lootPlayerId, it.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        EventDialog.ChestWho -> PlayerPickerDialog(
            title = stringResource(R.string.chest_who_played),
            players = game.players,
            onPick = { dialog = EventDialog.ChestCreatures(it.id) },
            onDismiss = { dialog = null },
        )

        is EventDialog.ChestCreatures -> CreaturePickerDialog(
            available = buildList {
                add(SeaMonster.KRAKEN)
                add(SeaMonster.WHITE_WHALE)
                if (ExpansionCard.SPOTTED_RAY in game.expansionCards) add(SeaMonster.SPOTTED_RAY)
            },
            onConfirm = {
                onAddDavyJones(d.playerId, it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
internal fun PlayerPickerDialog(title: String, players: List<Player>, onPick: (Player) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                players.forEach { player ->
                    OutlinedButton(
                        onClick = { onPick(player) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        PlayerAvatar(player, size = 32.dp)
                        Text(player.name, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun CreaturePickerDialog(
    available: List<SeaMonster>,
    onConfirm: (Set<SeaMonster>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<SeaMonster>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chest_which_creatures)) },
        text = {
            Column {
                available.forEach { monster ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = monster in selected,
                            onCheckedChange = { checked ->
                                selected =
                                    if (checked) selected + monster else selected - monster
                            },
                        )
                        MonsterIcon(monster)
                        Text(
                            stringResource(monster.labelRes()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Icon of a sea creature: an emoji, or a drawn icon for the ray (no emoji exists). */
@Composable
fun MonsterIcon(monster: SeaMonster, modifier: Modifier = Modifier) {
    val emoji = monster.emoji()
    if (emoji != null) {
        Text(emoji, modifier = modifier, style = MaterialTheme.typography.headlineSmall)
    } else {
        Image(
            painter = painterResource(R.drawable.ic_spotted_ray),
            contentDescription = null,
            modifier = modifier.size(28.dp),
        )
    }
}
