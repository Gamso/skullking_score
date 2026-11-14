package com.gamso.skullkingscore.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.ui.theme.ScoreNegative

/**
 * Bids of the round, collapsible. The header recalls the total bid against the number of tricks; each
 * player shows "tricks / bid", colored once the bid is made or exceeded.
 */
@Composable
fun BidsReminder(players: List<Player>, details: RoundDetails) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val totalBid = details.entries.sumOf { it.effectiveBid }
    val cards = details.round.cardsDealt
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.play_bids_reminder),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.play_bids_summary, totalBid, cards) + bidBalance(totalBid - cards),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        }
        AnimatedVisibility(visible = expanded) {
            // Two players per line: the whole table fits in a few lines.
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                players.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { player ->
                            val entry = details.entry(player.id)
                            BidCell(
                                player = player,
                                bid = entry?.effectiveBid ?: 0,
                                tricks = entry?.tricks ?: 0,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun bidBalance(delta: Int): String = when {
    delta > 0 -> " · " + stringResource(R.string.play_bids_over, delta)
    delta < 0 -> " · " + stringResource(R.string.play_bids_under, -delta)
    else -> ""
}

@Composable
private fun BidCell(player: Player, bid: Int, tricks: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player, size = 24.dp)
        Text(
            player.name,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$tricks / $bid",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                tricks > bid -> ScoreNegative
                tricks == bid -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
