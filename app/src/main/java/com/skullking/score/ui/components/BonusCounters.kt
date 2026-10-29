package com.skullking.score.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skullking.score.R
import com.skullking.score.core.Bonus
import com.skullking.score.core.BonusType
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.RuleSet

/** A counter of captured cards: its bonus type (for the points), how to read and write it in a [Bonus]. */
data class BonusCounter(
    @StringRes val label: Int,
    val type: BonusType,
    val read: (Bonus) -> Int,
    val write: (Bonus, Int) -> Bonus,
)

/** Counters offered depending on the enabled expansion cards (a counter exists only if its card is in play). */
fun bonusCounters(expansion: Set<ExpansionCard>): List<BonusCounter> = buildList {
    add(
        BonusCounter(
            R.string.bonus_colored_14,
            BonusType.COLORED_14,
            { it.colored14 },
            { b, v -> b.copy(colored14 = v) },
        ),
    )
    add(
        BonusCounter(
            R.string.bonus_black_14,
            BonusType.BLACK_14,
            { it.black14 },
            { b, v -> b.copy(black14 = v) },
        ),
    )
    add(
        BonusCounter(
            R.string.bonus_mermaid_by_pirate,
            BonusType.MERMAID_BY_PIRATE,
            {
                it.mermaidsByPirate
            },
            { b, v -> b.copy(mermaidsByPirate = v) },
        ),
    )
    add(
        BonusCounter(
            R.string.bonus_pirate_by_skull_king,
            BonusType.PIRATE_BY_SKULL_KING,
            { it.piratesBySkullKing },
            { b, v -> b.copy(piratesBySkullKing = v) },
        ),
    )
    add(
        BonusCounter(
            R.string.bonus_skull_king_by_mermaid,
            BonusType.SKULL_KING_BY_MERMAID,
            { it.skullKingByMermaid },
            { b, v -> b.copy(skullKingByMermaid = v) },
        ),
    )
    if (ExpansionCard.FIRST_MATE_CON in expansion) {
        add(
            BonusCounter(
                R.string.bonus_con_captured,
                BonusType.CON_CAPTURED,
                { it.conCaptured },
                { b, v -> b.copy(conCaptured = v) },
            ),
        )
    }
    if (ExpansionCard.EIGHTS in expansion) {
        add(
            BonusCounter(
                R.string.bonus_eight,
                BonusType.EIGHT,
                { it.eights },
                { b, v -> b.copy(eights = v) },
            ),
        )
    }
    if (ExpansionCard.SEVENS in expansion) {
        add(
            BonusCounter(
                R.string.bonus_seven,
                BonusType.SEVEN,
                { it.sevens },
                { b, v -> b.copy(sevens = v) },
            ),
        )
    }
}

private const val MAX_COUNT = 20

/** List of captured-card counters, one stepper per line. */
@Composable
fun BonusCounterList(
    bonus: Bonus,
    expansion: Set<ExpansionCard>,
    onChange: (Bonus) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val rules = remember { RuleSet() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
        bonusCounters(expansion).forEach { counter ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // The value of each card is recalled next to it: "8 (+5)", "7 (-5)".
                Text(
                    stringResource(counter.label) + " (" + formatPoints(rules.valueOf(counter.type)) + ")",
                    modifier = Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                )
                Stepper(
                    value = counter.read(bonus),
                    onValueChange = { onChange(counter.write(bonus, it)) },
                    range = 0..MAX_COUNT,
                    compact = compact,
                )
            }
        }
    }
}
