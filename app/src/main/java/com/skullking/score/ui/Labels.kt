package com.skullking.score.ui

import androidx.annotation.StringRes
import com.skullking.score.R
import com.skullking.score.core.BonusType
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard

@StringRes
fun ScoringMode.labelRes(): Int = when (this) {
    ScoringMode.CLASSIC -> R.string.mode_classic
    ScoringMode.RASCAL -> R.string.mode_rascal
    ScoringMode.CANNONBALL -> R.string.mode_cannonball
}

@StringRes
fun ScoringMode.shortLabelRes(): Int = when (this) {
    ScoringMode.CLASSIC -> R.string.mode_classic_short
    ScoringMode.RASCAL -> R.string.mode_rascal_short
    ScoringMode.CANNONBALL -> R.string.mode_cannonball_short
}

@StringRes
fun ScoringMode.descriptionRes(): Int = when (this) {
    ScoringMode.CLASSIC -> R.string.mode_classic_desc
    ScoringMode.RASCAL -> R.string.mode_rascal_desc
    ScoringMode.CANNONBALL -> R.string.mode_cannonball_desc
}

@StringRes
fun SeaMonster.labelRes(): Int = when (this) {
    SeaMonster.KRAKEN -> R.string.monster_kraken
    SeaMonster.WHITE_WHALE -> R.string.monster_white_whale
    SeaMonster.SPOTTED_RAY -> R.string.monster_spotted_ray
}

@StringRes
fun ExpansionCard.labelRes(): Int = when (this) {
    ExpansionCard.SEVENS -> R.string.card_sevens
    ExpansionCard.EIGHTS -> R.string.card_eights
    ExpansionCard.ZERO_FOURTEEN -> R.string.card_zero_fourteen
    ExpansionCard.WILD_15 -> R.string.card_wild_15
    ExpansionCard.MARY_THORNE -> R.string.card_mary_thorne
    ExpansionCard.FIRST_MATE_CON -> R.string.card_first_mate_con
    ExpansionCard.DAVY_JONES_CHEST -> R.string.card_davy_jones
    ExpansionCard.PLANK -> R.string.card_plank
    ExpansionCard.SPOTTED_RAY -> R.string.card_spotted_ray
    ExpansionCard.LAST_SALVO -> R.string.card_last_salvo
}

@StringRes
fun BonusType.labelRes(): Int = when (this) {
    BonusType.COLORED_14 -> R.string.bonus_colored_14
    BonusType.BLACK_14 -> R.string.bonus_black_14
    BonusType.MERMAID_BY_PIRATE -> R.string.bonus_mermaid_by_pirate
    BonusType.PIRATE_BY_SKULL_KING -> R.string.bonus_pirate_by_skull_king
    BonusType.SKULL_KING_BY_MERMAID -> R.string.bonus_skull_king_by_mermaid
    BonusType.CON_CAPTURED -> R.string.bonus_con_captured
    BonusType.LOOT -> R.string.bonus_loot
    BonusType.DAVY_JONES -> R.string.bonus_davy_jones
    BonusType.EIGHT -> R.string.bonus_eight
    BonusType.SEVEN -> R.string.bonus_seven
}

/** Emoji of the creature; the ray has none (see [com.skullking.score.ui.components.MonsterIcon]). */
fun SeaMonster.emoji(): String? = when (this) {
    SeaMonster.KRAKEN -> "🐙"
    SeaMonster.WHITE_WHALE -> "🐳"
    SeaMonster.SPOTTED_RAY -> null
}

@StringRes
fun WinningCard.labelRes(): Int = when (this) {
    WinningCard.MERMAID -> R.string.win_mermaid
    WinningCard.SKULL_KING -> R.string.win_skull_king
    WinningCard.FIRST_MATE -> R.string.win_first_mate
    WinningCard.PIRATE -> R.string.win_pirate
    WinningCard.OTHER -> R.string.win_other
}

@StringRes
fun Pirate.labelRes(): Int = when (this) {
    Pirate.ROSIE -> R.string.pirate_rosie
    Pirate.RASCAL -> R.string.pirate_rascal
    Pirate.WILL -> R.string.pirate_will
    Pirate.JUANITA -> R.string.pirate_juanita
    Pirate.HARRY -> R.string.pirate_harry
    Pirate.TIGRESS -> R.string.pirate_tigress
    Pirate.MARY_THORNE -> R.string.pirate_mary_thorne
}
