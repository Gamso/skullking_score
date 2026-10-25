package com.skullking.score.ui.feature.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.WinningCard
import com.skullking.score.data.export.FileGateway
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.PlayerRepository
import com.skullking.score.domain.stats.CardStats
import com.skullking.score.domain.stats.CardStatsCalculator
import com.skullking.score.domain.stats.ExpansionComparison
import com.skullking.score.domain.stats.ExpansionFilter
import com.skullking.score.domain.stats.PlayerCountFilter
import com.skullking.score.domain.stats.PlayerStats
import com.skullking.score.domain.stats.RoundRecord
import com.skullking.score.domain.stats.RoundSize
import com.skullking.score.domain.stats.StatsCalculator
import com.skullking.score.domain.stats.StatsFilter
import com.skullking.score.domain.stats.StatsOverview
import com.skullking.score.ui.components.ChartSeries
import com.skullking.score.ui.components.LineChart
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.normalizeForSearch
import com.skullking.score.ui.share.StatsImageRenderer
import com.skullking.score.ui.share.StatsImageTextsFactory
import com.skullking.score.ui.share.StatsTrend
import com.skullking.score.ui.shortLabelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUiState(
    val filter: StatsFilter = StatsFilter(),
    val stats: List<PlayerStats> = emptyList(),
    val overview: StatsOverview = StatsOverview(0),
    val cards: CardStats? = null,
    val expansion: ExpansionComparison? = null,
    val players: List<Player> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    games: GameRepository,
    playerRepository: PlayerRepository,
    private val files: FileGateway,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _events = Channel<Uri>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val filter = MutableStateFlow(StatsFilter())

    val state: StateFlow<StatsUiState> = combine(
        games.observeAllGames(),
        filter,
        playerRepository.observePlayers(),
    ) { all, current, players ->
        val scored = StatsCalculator.select(all, current)
        // The expansion comparison ignores the expansion filter, otherwise one of the two sides would be empty.
        val allExpansions = StatsCalculator.select(all, current.copy(expansion = ExpansionFilter.ALL))
        StatsUiState(
            filter = current,
            stats = StatsCalculator.computeFrom(scored),
            overview = StatsCalculator.overview(scored),
            cards = CardStatsCalculator.compute(scored),
            expansion = CardStatsCalculator.expansionComparison(allExpansions),
            players = players.filter { !it.archived },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setMode(mode: ScoringMode) = filter.update { it.copy(mode = mode) }

    /** Generates a player's statistics image (with the current filters) and sends it to the Android share sheet. */
    fun sharePlayer(stats: PlayerStats) {
        val current = state.value
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                StatsImageRenderer.render(
                    stats.player,
                    trendOf(stats),
                    StatsImageTextsFactory.create(context, stats, current.filter, current.overview.gamesCount),
                )
            }
            val bytes = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().also {
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
                }.toByteArray()
            }
            _events.send(files.writeShareableBytes("skullking-stats-${stats.player.id}.png", bytes))
        }
    }

    fun setPlayerCount(value: PlayerCountFilter) = filter.update { it.copy(playerCount = value) }

    fun setExpansion(value: ExpansionFilter) = filter.update { it.copy(expansion = value) }

    fun togglePresent(player: Player) = filter.update {
        val ids = if (player.id in
            it.presentPlayerIds
        ) {
            it.presentPlayerIds - player.id
        } else {
            it.presentPlayerIds + player.id
        }
        it.copy(presentPlayerIds = ids)
    }
}

internal fun Double.percent(): String = "${(this * 100).roundToInt()} %"

private const val PNG_QUALITY = 100

/** Draws the trend from three games onwards. */
internal fun trendOf(stats: PlayerStats): StatsTrend? =
    if (stats.gameTotals.size >= MIN_TREND_GAMES) StatsTrend(stats.gameTotals, stats.rollingAverage()) else null

@Composable
fun StatsScreen(onBack: () -> Unit, onDuel: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_action)
    val visible = remember(state.stats, query) {
        val needle = normalizeForSearch(query.trim())
        state.stats.filter { normalizeForSearch(it.player.name).contains(needle) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }

    SkullScaffold(
        title = stringResource(R.string.home_stats),
        onBack = onBack,
        maxContentWidth = Dp.Unspecified,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(STATS_MIN_WIDTH),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = {
                        tab = 0
                    }, text = { Text(stringResource(R.string.stats_tab_players)) })
                    Tab(selected = tab == 1, onClick = {
                        tab = 1
                    }, text = { Text(stringResource(R.string.stats_tab_cards)) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatsFilters(state, viewModel, query, onQuery = { query = it }, onDuel = onDuel, showSearch = tab == 0)
            }
            if (tab == 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val cards = state.cards
                    val expansion = state.expansion
                    if (cards != null && expansion != null) CardStatsContent(cards, expansion)
                }
                return@LazyVerticalGrid
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(if (state.stats.isEmpty()) R.string.stats_empty else R.string.stats_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visible, key = { it.player.id }) { stats ->
                PlayerStatsCard(
                    stats = stats,
                    expanded = expandedId == stats.player.id,
                    onToggle = { expandedId = if (expandedId == stats.player.id) null else stats.player.id },
                    onShare = { viewModel.sharePlayer(stats) },
                )
            }
        }
    }
}

@Composable
private fun pluralStringResourceGames(count: Int): String = androidx.compose.ui.res.pluralStringResource(
    R.plurals.stats_games_count,
    count,
    count,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsFilters(
    state: StatsUiState,
    viewModel: StatsViewModel,
    query: String,
    onQuery: (String) -> Unit,
    onDuel: () -> Unit,
    showSearch: Boolean,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val active = (if (state.filter.playerCount != PlayerCountFilter.ALL) 1 else 0) +
        (if (state.filter.expansion != ExpansionFilter.ALL) 1 else 0) +
        state.filter.presentPlayerIds.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScoringMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.filter.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = { Text(stringResource(mode.shortLabelRes())) },
                )
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.stats_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { open = !open }) {
                Text(
                    stringResource(R.string.stats_more_filters) + if (active > 0) " ($active)" else "",
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                )
            }
            Text(
                pluralStringResourceGames(state.overview.gamesCount),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(onClick = onDuel) { Text(stringResource(R.string.duel_title)) }
        }
        if (open) {
            Text(
                stringResource(R.string.stats_mode_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerCountFilter.entries.forEach { value ->
                    FilterChip(
                        selected = state.filter.playerCount == value,
                        onClick = { viewModel.setPlayerCount(value) },
                        label = { Text(stringResource(value.labelRes())) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpansionFilter.entries.forEach { value ->
                    FilterChip(
                        selected = state.filter.expansion == value,
                        onClick = { viewModel.setExpansion(value) },
                        label = { Text(stringResource(value.labelRes())) },
                    )
                }
            }
            if (state.players.isNotEmpty()) {
                Text(stringResource(R.string.stats_filter_present), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.players.forEach { player ->
                        FilterChip(
                            selected = player.id in state.filter.presentPlayerIds,
                            onClick = { viewModel.togglePresent(player) },
                            label = { Text(player.name) },
                            leadingIcon = { PlayerAvatar(player, size = 22.dp) },
                        )
                    }
                }
            }
        }
    }
}

internal fun PlayerCountFilter.labelRes(): Int = when (this) {
    PlayerCountFilter.ALL -> R.string.filter_all_sizes
    PlayerCountFilter.TWO_THREE -> R.string.filter_players_2_3
    PlayerCountFilter.FOUR_FIVE -> R.string.filter_players_4_5
    PlayerCountFilter.SIX_PLUS -> R.string.filter_players_6_plus
}

internal fun ExpansionFilter.labelRes(): Int = when (this) {
    ExpansionFilter.ALL -> R.string.filter_expansion_all
    ExpansionFilter.WITH -> R.string.filter_expansion_with
    ExpansionFilter.WITHOUT -> R.string.filter_expansion_without
}

internal fun RoundSize.labelRes(): Int = when (this) {
    RoundSize.SMALL -> R.string.size_small
    RoundSize.MEDIUM -> R.string.size_medium
    RoundSize.LARGE -> R.string.size_large
    RoundSize.HUGE -> R.string.size_huge
}

@Composable
private fun recordText(record: RoundRecord?): String {
    if (record == null) return "–"
    val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(record.gameStartedAt))
    return stringResource(R.string.stats_record_round, formatPoints(record.points), record.roundNumber, date)
}

@Composable
private fun PlayerStatsCard(stats: PlayerStats, expanded: Boolean, onToggle: () -> Unit, onShare: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAvatar(stats.player)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stats.player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.stats_games_wins,
                            stats.gamesPlayed,
                            stats.wins,
                            stats.winRate.percent(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.stats_share_player, stats.player.name),
                    )
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            StatLine(R.string.stats_average, "%.1f".format(locale, stats.averageScore))
            StatLine(R.string.stats_best, stats.bestScore.toString())
            StatLine(R.string.stats_worst, stats.worstScore.toString())
            StatLine(R.string.stats_bid_success, stats.bidSuccessRate.percent())
            StatLine(R.string.stats_zero_success, stats.zeroBidSuccessRate?.percent() ?: "–")

            if (expanded) {
                ExpandedStats(stats, locale)
            }
        }
    }
}

@Composable
private fun ExpandedStats(stats: PlayerStats, locale: java.util.Locale) {
    SectionTitle(stringResource(R.string.stats_records_title))
    TextLine(R.string.stats_record_best_round, recordText(stats.bestRound))
    TextLine(R.string.stats_record_worst_round, recordText(stats.worstRound))
    StatLine(R.string.stats_record_exact_streak, stats.longestExactStreak.toString())
    StatLine(R.string.stats_record_win_streak, stats.longestWinStreak.toString())
    if (stats.zeroBids > 0) StatLine(R.string.stats_record_zero_streak, stats.longestZeroStreak.toString())
    StatLine(R.string.stats_record_bonus, formatPoints(stats.biggestBonusRound))

    SectionTitle(stringResource(R.string.stats_trend_title))
    if (stats.gameTotals.size < MIN_TREND_GAMES) {
        Text(
            stringResource(R.string.stats_trend_not_enough, MIN_TREND_GAMES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val color = MaterialTheme.colorScheme.primary
        val rolling = stats.rollingAverage()
        LineChart(
            series = listOf(
                ChartSeries(
                    stringResource(R.string.stats_trend_games),
                    color,
                    stats.gameTotals.map { it.toFloat() },
                ),
                ChartSeries(
                    stringResource(R.string.stats_trend_average),
                    MaterialTheme.colorScheme.onSurface,
                    rolling.map { it.toFloat() },
                    dashed = true,
                ),
            ),
            description = stringResource(
                R.string.stats_trend_description,
                stats.player.name,
                stats.gameTotals.first(),
                stats.gameTotals.last(),
                stats.gameTotals.size,
            ),
        )
        Text(
            stringResource(R.string.stats_trend_legend, PlayerStats.ROLLING_WINDOW),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionTitle(stringResource(R.string.stats_size_title))
    RoundSize.entries.forEach { size ->
        val rate = stats.successBySize[size]
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(size.labelRes()))
            Text(
                rate?.rate?.let { "${it.percent()} (${rate.successes}/${rate.attempts})" } ?: "–",
                fontWeight = FontWeight.Medium,
            )
        }
    }
    val bias = stats.averageBidBias
    val tendency = when {
        abs(bias) < BALANCED_BIAS -> stringResource(R.string.stats_bias_balanced)
        bias > 0 -> stringResource(R.string.stats_bias_under, "%.1f".format(locale, bias))
        else -> stringResource(R.string.stats_bias_over, "%.1f".format(locale, -bias))
    }
    StatLine(R.string.stats_bias, tendency)
    Text(
        stringResource(R.string.stats_bias_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SectionTitle(stringResource(R.string.stats_bonus_title))
    val bonus = stats.bonusPoints.filterValues { it != 0 }
    if (bonus.isEmpty()) {
        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    bonus.entries.sortedBy { it.key.ordinal }.forEach { (type, points) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(type.labelRes()))
            Text(formatPoints(points), fontWeight = FontWeight.Medium)
        }
    }

    SectionTitle(stringResource(R.string.stats_wins_with_title))
    stats.favoriteCard?.let { card ->
        val pirate = stats.favoritePirate.takeIf { card == WinningCard.PIRATE }
        val name = stringResource(card.labelRes()) + pirate?.let { " · " + stringResource(it.labelRes()) }.orEmpty()
        StatLine(R.string.stats_favorite_card, name)
    }
    Text(
        stringResource(R.string.stats_tricks_coverage, stats.gamesWithTricks, stats.gamesPlayed),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    WinsByCard(stats)
    if (stats.rascalWagers.attempts > 0) {
        StatLine(
            R.string.stats_rascal_wagers,
            "${stats.rascalWagers.successes}/${stats.rascalWagers.attempts}",
        )
    }
    if (stats.harryAdjustments.attempts > 0) {
        StatLine(
            R.string.stats_harry_adjustments,
            "${stats.harryAdjustments.successes}/${stats.harryAdjustments.attempts}",
        )
    }

    SectionTitle(stringResource(R.string.stats_monthly_title))
    val format = DateTimeFormatter.ofPattern("MMM yyyy", locale)
    stats.monthly.forEach { point ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(point.month.format(format))
                Text(
                    stringResource(
                        R.string.stats_monthly_line,
                        point.games,
                        point.averageScore.roundToInt(),
                        point.bidSuccessRate.percent(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LinearProgressIndicator(
                progress = { point.bidSuccessRate.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
        }
    }

    SectionTitle(stringResource(R.string.stats_head_to_head_title))
    if (stats.headToHead.isEmpty()) {
        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    stats.headToHead.forEach { h2h ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(h2h.opponent.name)
            Text(stringResource(R.string.stats_head_to_head_line, h2h.wins, h2h.losses, h2h.commonGames))
        }
    }
}

@Composable
private fun WinsByCard(stats: PlayerStats) {
    WinningCard.entries.forEach { card ->
        val count = stats.winsByCard[card] ?: 0
        if (count > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(card.labelRes()))
                Text(count.toString(), fontWeight = FontWeight.Medium)
            }
        }
        if (card == WinningCard.PIRATE) {
            stats.winsByPirate.entries.sortedByDescending { it.value }.forEach { (pirate, n) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(pirate.labelRes()), style = MaterialTheme.typography.bodySmall)
                    Text(n.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatLine(labelRes: Int, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Label followed by a long value (round and date) on the line below. */
@Composable
private fun TextLine(labelRes: Int, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

internal const val MIN_TREND_GAMES = 3
private const val BALANCED_BIAS = 0.25
private val STATS_MIN_WIDTH = 380.dp
