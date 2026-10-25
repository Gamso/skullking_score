package com.skullking.score.domain.model

import kotlin.random.Random

/** Couleurs des pastilles de joueurs, attribuées au hasard à la création d'un profil. */
object PlayerPalette {
    val colors: List<Long> = listOf(
        0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFFAED581, 0xFF4DB6AC, 0xFF4FC3F7,
        0xFF7986CB, 0xFFBA68C8, 0xFFF06292, 0xFFA1887F, 0xFF90A4AE, 0xFFDCE775,
    )

    /** Tire une couleur au hasard, en évitant celles déjà prises par d'autres joueurs tant qu'il en reste. */
    fun pick(used: Collection<Long>, random: Random = Random): Long =
        colors.filter { it !in used }.ifEmpty { colors }.random(random)
}
