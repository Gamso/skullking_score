package com.skullking.score.domain.model

import kotlin.random.Random

/** Colors of the player badges, assigned at random when a profile is created. */
object PlayerPalette {
    val colors: List<Long> = listOf(
        0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFFAED581, 0xFF4DB6AC, 0xFF4FC3F7,
        0xFF7986CB, 0xFFBA68C8, 0xFFF06292, 0xFFA1887F, 0xFF90A4AE, 0xFFDCE775,
    )

    /** Picks a random color, avoiding those already taken by other players as long as some remain. */
    fun pick(used: Collection<Long>, random: Random = Random): Long =
        colors.filter { it !in used }.ifEmpty { colors }.random(random)
}
