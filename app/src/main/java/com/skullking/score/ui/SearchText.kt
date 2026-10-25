package com.skullking.score.ui

import java.text.Normalizer

private val DIACRITICS = Regex("\\p{M}+")

/** Lowercase and without accents: to compare what is typed with names, whatever the case or accent. */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
