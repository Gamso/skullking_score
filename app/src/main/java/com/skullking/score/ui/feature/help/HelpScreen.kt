package com.skullking.score.ui.feature.help

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.skullking.score.R
import com.skullking.score.ui.components.SkullScaffold
import java.text.Normalizer

private data class HelpSection(@StringRes val title: Int, @StringRes val body: Int)

private data class HelpTab(@StringRes val label: Int, val sections: List<HelpSection>)

/** Onglets de l'aide : chaque onglet regroupe les rubriques d'un même sujet. */
private val TABS = listOf(
    HelpTab(
        R.string.help_tab_cards,
        listOf(
            HelpSection(R.string.help_hierarchy_title, R.string.help_hierarchy_body),
            HelpSection(R.string.help_special_title, R.string.help_special_body),
        ),
    ),
    HelpTab(
        R.string.help_tab_scoring,
        listOf(
            HelpSection(R.string.help_points_title, R.string.help_points_body),
            HelpSection(R.string.help_bonus_title, R.string.help_bonus_body),
            HelpSection(R.string.help_modes_title, R.string.help_modes_body),
        ),
    ),
    HelpTab(
        R.string.help_tab_expansion,
        listOf(HelpSection(R.string.help_expansion_title, R.string.help_expansion_body)),
    ),
    HelpTab(R.string.help_tab_faq, listOf(HelpSection(R.string.help_faq_title, R.string.help_faq_body))),
)

/** Icônes insérables dans le texte (aucun emoji n'existe pour ces cartes), par jeton `{nom}`. */
private val INLINE_ICONS = mapOf(
    "ray" to R.drawable.ic_spotted_ray,
    "con" to R.drawable.ic_first_mate,
    "davy" to R.drawable.ic_davy_jones,
)

/** Gras : **texte** ; icône : {ray}, {con}, {davy}. */
private val TOKEN = Regex("""\*\*(.+?)\*\*|\{(ray|con|davy)\}""")

/** Aide intégrée : onglets par thème, et recherche qui affiche directement les lignes correspondantes. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val searching = query.isNotBlank()

    SkullScaffold(title = stringResource(R.string.help), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.help_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.help_search_clear))
                        }
                    }
                },
                singleLine = true,
            )
            if (!searching) {
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                    TABS.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (searching) {
                    SearchResults(query.trim())
                } else {
                    TABS[selectedTab].sections.forEach { section ->
                        SectionCard(stringResource(section.title)) { HelpText(stringResource(section.body)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/** Résultats : par rubrique, les lignes contenant la recherche (toute la rubrique si son titre correspond). */
@Composable
private fun SearchResults(query: String) {
    val found = TABS.flatMap { it.sections }.mapNotNull { section ->
        val title = stringResource(section.title)
        val lines = stringResource(section.body).split("\n").filter { it.isNotBlank() }
        val matching = if (matches(title, query)) lines else lines.filter { matches(plainText(it), query) }
        if (matching.isEmpty()) null else title to matching
    }
    if (found.isEmpty()) {
        Text(
            stringResource(R.string.help_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    found.forEach { (title, lines) ->
        SectionCard(title) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { HelpText(it, query) }
            }
        }
    }
}

/** Texte sans les jetons de mise en forme, pour la recherche. */
private fun plainText(line: String): String = TOKEN.replace(line) { it.groupValues[1] }

/** Minuscules et sans accents, caractère par caractère : la longueur est conservée pour situer les occurrences. */
private fun String.folded(): String = map { c ->
    Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar()
}.joinToString("")

private fun matches(text: String, query: String): Boolean = text.folded().contains(query.folded())

private fun AnnotatedString.Builder.appendHighlighted(
    segment: String,
    query: String,
    bold: Boolean,
    highlight: SpanStyle,
) {
    val base = if (bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
    val needle = query.folded()
    val haystack = segment.folded()
    var from = 0
    while (needle.isNotEmpty()) {
        val found = haystack.indexOf(needle, from)
        if (found < 0) break
        withStyle(base) { append(segment.substring(from, found)) }
        withStyle(base.merge(highlight)) { append(segment.substring(found, found + needle.length)) }
        from = found + needle.length
    }
    withStyle(base) { append(segment.substring(from)) }
}

@Composable
private fun HelpText(text: String, query: String = "") {
    val highlight = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer)
    val annotated = buildAnnotatedString {
        var last = 0
        TOKEN.findAll(text).forEach { match ->
            appendHighlighted(text.substring(last, match.range.first), query, bold = false, highlight = highlight)
            val icon = match.groupValues[2]
            if (icon.isNotEmpty()) {
                appendInlineContent(icon, icon)
            } else {
                appendHighlighted(match.groupValues[1], query, bold = true, highlight = highlight)
            }
            last = match.range.last + 1
        }
        appendHighlighted(text.substring(last), query, bold = false, highlight = highlight)
    }
    val inline = INLINE_ICONS.mapValues { (_, drawable) ->
        InlineTextContent(Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter)) {
            Image(painterResource(drawable), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
    Text(annotated, inlineContent = inline, style = MaterialTheme.typography.bodyMedium)
}
