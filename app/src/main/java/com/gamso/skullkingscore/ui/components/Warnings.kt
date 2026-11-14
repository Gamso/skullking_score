package com.gamso.skullkingscore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.core.RoundWarning
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.core.WarningCode
import com.gamso.skullkingscore.ui.labelRes

/** Lists the [warnings] of a round about to be validated: validate it anyway ([onConfirm]), or go back to fix it. */
@Composable
fun ConfirmWarningsDialog(warnings: List<RoundWarning>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.play_warnings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warnings.forEach { Text(it.message(), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.play_validate_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.play_fix)) }
        },
    )
}

/** French text of a consistency warning (never blocking). */
@Composable
fun RoundWarning.message(): String {
    val a = args.getOrElse(0) { 0 }
    val b = args.getOrElse(1) { 0 }
    return when (code) {
        WarningCode.TRICKS_OVER -> stringResource(R.string.warn_tricks_over, a, b)

        WarningCode.TRICKS_UNDER -> stringResource(R.string.warn_tricks_under, a, b)

        WarningCode.CARDS_EXCEED_DECK -> stringResource(R.string.warn_cards_exceed_deck, a, b)

        WarningCode.TOO_MANY_COLORED_14 -> stringResource(R.string.warn_too_many_colored_14, a, b)

        WarningCode.TOO_MANY_BLACK_14 -> stringResource(R.string.warn_too_many_black_14, a, b)

        WarningCode.TOO_MANY_SKULL_KING -> stringResource(R.string.warn_too_many_skull_king, a, b)

        WarningCode.TOO_MANY_CON -> stringResource(R.string.warn_too_many_con, a, b)

        WarningCode.TOO_MANY_SEVENS -> stringResource(R.string.warn_too_many_sevens, a, b)

        WarningCode.TOO_MANY_EIGHTS -> stringResource(R.string.warn_too_many_eights, a, b)

        WarningCode.TOO_MANY_MERMAIDS -> stringResource(R.string.warn_too_many_mermaids, a, b)

        WarningCode.TOO_MANY_PIRATES -> stringResource(R.string.warn_too_many_pirates, a, b)

        WarningCode.TOO_MANY_LOOT -> stringResource(R.string.warn_too_many_loot, a, b)

        WarningCode.DUPLICATE_MONSTER -> {
            val names = args.mapNotNull { SeaMonster.entries.getOrNull(it) }
                .map { stringResource(it.labelRes()) }
                .joinToString(", ")
            stringResource(R.string.warn_duplicate_monster, names)
        }
    }
}
