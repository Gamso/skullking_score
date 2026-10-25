package com.skullking.score.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.skullking.score.R
import com.skullking.score.core.RoundWarning
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WarningCode
import com.skullking.score.ui.labelRes

/** Texte français d'une alerte de cohérence (toujours non bloquante). */
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
