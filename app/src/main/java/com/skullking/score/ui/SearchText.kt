package com.skullking.score.ui

import java.text.Normalizer

private val DIACRITICS = Regex("\\p{M}+")

/** Minuscules et sans accents : pour comparer ce qu'on tape à des noms, quelle que soit la casse ou l'accent. */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
