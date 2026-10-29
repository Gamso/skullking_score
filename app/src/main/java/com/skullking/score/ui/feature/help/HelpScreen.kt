package com.skullking.score.ui.feature.help

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skullking.score.R
import com.skullking.score.ui.components.SkullScaffold
import java.text.Normalizer

/** Sections whose normal view is a drawing rather than text (the text stays available for the search). */
private enum class HelpVisual { HIERARCHY, }

private data class HelpSection(@StringRes val title: Int, @StringRes val body: Int, val visual: HelpVisual? = null)

private data class HelpTab(@StringRes val label: Int, val sections: List<HelpSection>)

/** Help tabs: each tab groups the sections of a single topic. */
private val TABS = listOf(
    HelpTab(
        R.string.help_tab_cards,
        listOf(
            HelpSection(R.string.help_hierarchy_title, R.string.help_hierarchy_body, HelpVisual.HIERARCHY),
            HelpSection(R.string.help_special_title, R.string.help_special_body),
            HelpSection(R.string.help_pirates_title, R.string.help_pirates_body),
            HelpSection(R.string.help_monsters_title, R.string.help_monsters_body),
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

/** Icons insertable in the text (no emoji exists for these cards), by `{name}` token. */
private val INLINE_ICONS = mapOf(
    "ray" to R.drawable.ic_spotted_ray,
    "con" to R.drawable.ic_first_mate,
    "davy" to R.drawable.ic_davy_jones,
)

/** Lines starting with these prefixes are shown in a box: a remark (important) or a note (extra information). */
private const val REMARK_PREFIX = "!> "
private const val NOTE_PREFIX = "> "

/** Lines starting with this prefix are sub-bullets, indented under the previous item. */
private const val SUB_PREFIX = "-- "

/** Publisher page with the official rules and FAQ. */
private const val OFFICIAL_URL = "https://www.grandpabecksgames.com/pages/skull-king"

/** Bold: **text**; icon: {ray}, {con}, {davy}. */
private val TOKEN = Regex("""\*\*(.+?)\*\*|\{(ray|con|davy)\}""")

/** Built-in help: tabs by topic, and a search that directly shows the matching lines. */
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
                        SectionCard(stringResource(section.title)) {
                            when (section.visual) {
                                HelpVisual.HIERARCHY -> HierarchyChart()
                                null -> HelpBody(stringResource(section.body))
                            }
                        }
                    }
                    if (selectedTab == TABS.lastIndex) {
                        val uriHandler = LocalUriHandler.current
                        TextButton(onClick = { uriHandler.openUri(OFFICIAL_URL) }) {
                            Text(stringResource(R.string.help_official_link))
                        }
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

/** Results: per section, the lines containing the search (the whole section if its title matches). */
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
                lines.forEach { HelpLine(it, query) }
            }
        }
    }
}

/** Text without the formatting tokens, for the search. */
private fun plainText(line: String): String =
    TOKEN.replace(line.removePrefix(REMARK_PREFIX).removePrefix(NOTE_PREFIX)) { it.groupValues[1] }

/** Lowercase and without accents, character by character: the length is preserved to locate occurrences. */
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

/** A section body: one block per line, with the lines starting with "> " shown as notes. */
@Composable
private fun HelpBody(body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.split("\n").filter { it.isNotBlank() }.forEach { HelpLine(it) }
    }
}

@Composable
private fun HelpLine(line: String, query: String = "") {
    when {
        line.startsWith(REMARK_PREFIX) -> Callout(CalloutKind.REMARK) {
            HelpText(line.removePrefix(REMARK_PREFIX), query)
        }

        line.startsWith(NOTE_PREFIX) -> Callout(CalloutKind.NOTE) { HelpText(line.removePrefix(NOTE_PREFIX), query) }

        line.startsWith(SUB_PREFIX) -> Box(modifier = Modifier.padding(start = 16.dp)) {
            HelpText("◦ " + line.removePrefix(SUB_PREFIX), query)
        }

        else -> HelpText(line, query)
    }
}

private enum class CalloutKind { REMARK, NOTE }

/** Box with an accent bar: yellow for a remark (read carefully), purple for a note (extra information). */
@Composable
private fun Callout(kind: CalloutKind, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val remark = kind == CalloutKind.REMARK
    val dark = isSystemInDarkTheme()
    // Fixed tones, clearly different from the card background and from each other: yellow remark, teal note.
    val container = when {
        remark -> colors.tertiaryContainer
        dark -> Color(0xFF123C45)
        else -> Color(0xFFCFE8EA)
    }
    val onContainer = when {
        remark -> colors.onTertiaryContainer
        dark -> Color(0xFFD7F2F4)
        else -> Color(0xFF10222E)
    }
    val accent = when {
        remark -> colors.tertiary
        dark -> Color(0xFF6FD0D8)
        else -> Color(0xFF1F6F78)
    }
    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (remark) "⚠️ " else "ℹ️ ") +
                        stringResource(if (remark) R.string.help_remark else R.string.help_note),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                content()
            }
        }
    }
}

private data class Rank(
    val emoji: String?,
    @DrawableRes val icon: Int?,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val special: Boolean,
    val emojiSize: Int = 20,
)

private val RANKS = listOf(
    Rank("💀", null, R.string.hier_skull_king, R.string.hier_skull_king_sub, true),
    Rank(null, R.drawable.ic_first_mate, R.string.hier_con, R.string.hier_con_sub, true),
    Rank("🏴‍☠️", null, R.string.hier_pirates, R.string.hier_pirates_sub, true),
    Rank("🧜‍♀️", null, R.string.hier_mermaids, R.string.hier_mermaids_sub, true),
    Rank("⚫", null, R.string.hier_black, R.string.hier_black_sub, false),
    Rank("🟨🟪🟩", null, R.string.hier_suits, R.string.hier_suits_sub, false, emojiSize = 11),
    Rank("🏳️", null, R.string.hier_escape, R.string.hier_escape_sub, false),
)

private const val SKULL_KING_ROW = 0
private const val CON_ROW = 1
private const val MERMAID_ROW = 3

/** The card hierarchy like the official card: strongest first, a token per card, a mermaid-to-Skull King arrow. */
@Composable
private fun HierarchyChart() {
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val arrow = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.surfaceContainerLowest
    val tokenSpecial = MaterialTheme.colorScheme.secondaryContainer
    val tokenPlain = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val mermaid = centers[MERMAID_ROW]
                if (mermaid != null) {
                    // The mermaid beats the Skull King and the First Mate Con.
                    centers[SKULL_KING_ROW]?.let { drawBeatsArrow(arrow, mermaid, it, 24.dp.toPx()) }
                    centers[CON_ROW]?.let { drawBeatsArrow(arrow, mermaid, it, 12.dp.toPx()) }
                }
            },
    ) {
        RANKS.forEachIndexed { index, rank ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 ==
                            0
                        ) {
                            band.copy(alpha = 0.7f)
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(8.dp),
                    )
                    .onGloballyPositioned { centers[index] = it.positionInParent().y + it.size.height / 2f }
                    .padding(start = 26.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (rank.special) tokenSpecial else tokenPlain,
                        )
                        .border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    rank.emoji?.let { Text(it, fontSize = rank.emojiSize.sp) }
                    rank.icon?.let {
                        Image(painterResource(it), contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(rank.title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(rank.subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Curved arrow in the left margin, from the token at height [from] up to the token at height [to]. */
private fun DrawScope.drawBeatsArrow(color: Color, from: Float, to: Float, bulge: Float) {
    val endX = 26.dp.toPx()
    val tip = Offset(endX, to + 4.dp.toPx())
    val path = Path().apply {
        moveTo(endX, from)
        cubicTo(endX - bulge, from - 10.dp.toPx(), endX - bulge, to + 10.dp.toPx(), tip.x, tip.y)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    drawLine(color, tip, Offset(tip.x - 6.dp.toPx(), tip.y + 7.dp.toPx()), strokeWidth = 2.dp.toPx())
    drawLine(color, tip, Offset(tip.x + 5.dp.toPx(), tip.y + 8.dp.toPx()), strokeWidth = 2.dp.toPx())
}
