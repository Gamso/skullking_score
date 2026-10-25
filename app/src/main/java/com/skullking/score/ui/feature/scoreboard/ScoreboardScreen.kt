package com.skullking.score.ui.feature.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.DeckRules
import com.skullking.score.data.backup.AutoBackupManager
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound
import com.skullking.score.domain.model.nextStep
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.usecase.EditRoundUseCase
import com.skullking.score.domain.usecase.FinishGameUseCase
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.KeepScreenOn
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.WidthClass
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.components.rememberWidthClass
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.navigation.Routes
import com.skullking.score.ui.theme.ScoreNegative
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScoreboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    games: GameRepository,
    private val edit: EditRoundUseCase,
    private val finishGame: FinishGameUseCase,
    private val autoBackup: AutoBackupManager,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))

    val state: StateFlow<ScoredGame?> = games.observeGame(gameId)
        .filterNotNull()
        .map { GameScoreCalculator.calculate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            finishGame(gameId)
            autoBackup.run()
            onDone()
        }
    }

    /** Reopens the last validated round to correct its results. */
    fun reopenLastRound(onReopened: (roundId: Long) -> Unit) {
        viewModelScope.launch {
            edit.reopenLastRound(gameId)?.let(onReopened)
        }
    }
}

private val ROUND_COLUMN_WIDTH = 64.dp
private val PLAYER_COLUMN_WIDTH = 80.dp
private val DETAIL_PANEL_WIDTH = 340.dp

@Composable
fun ScoreboardScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onNavigate: (route: String) -> Unit,
    onEditRound: (roundId: Long) -> Unit,
    onFinished: () -> Unit,
    onShare: () -> Unit,
    viewModel: ScoreboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scored = state
    var selected by remember { mutableStateOf<Pair<ScoredRound, Player>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sidePanel = rememberWidthClass() == WidthClass.EXPANDED

    val game = scored?.game
    val inProgress = game?.status == GameStatus.IN_PROGRESS
    if (inProgress) KeepScreenOn()
    val lastRound = scored?.rounds?.lastOrNull()
    val hasPendingRound = lastRound != null && !lastRound.isScored

    SkullScaffold(
        title = stringResource(R.string.scoreboard),
        onBack = onBack,
        maxContentWidth = Dp.Unspecified,
        actions = {
            if (!inProgress && game != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_action))
                }
            }
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help)) }
            if (inProgress) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (lastRound?.isScored == true) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scoreboard_reopen_last)) },
                            onClick = {
                                menuOpen = false
                                viewModel.reopenLastRound(onEditRound)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scoreboard_finish)) },
                        onClick = {
                            menuOpen = false
                            confirmFinish = true
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (scored != null && game != null) {
                when {
                    !inProgress -> ActionBar(
                        primaryText = stringResource(R.string.scoreboard_see_podium),
                        onPrimary = { onNavigate(Routes.end(game.id)) },
                    )

                    hasPendingRound -> ActionBar(
                        primaryText = stringResource(R.string.scoreboard_resume_round, lastRound.round.number),
                        onPrimary = { onNavigate(Routes.forStep(game.id, scored.details.nextStep())) },
                    )

                    scored.rounds.size >= DeckRules.PLANNED_ROUNDS -> ActionBar(
                        primaryText = stringResource(R.string.scoreboard_extra_round),
                        onPrimary = { onNavigate(Routes.prepare(game.id)) },
                        secondary = {
                            OutlinedButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.scoreboard_finish))
                            }
                        },
                    )

                    else -> ActionBar(
                        primaryText = stringResource(R.string.scoreboard_next_round),
                        onPrimary = { onNavigate(Routes.prepare(game.id)) },
                    )
                }
            }
        },
    ) { padding ->
        if (scored == null) return@SkullScaffold
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_table)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_curve)) })
                }
                if (tab == 0) {
                    ScoreTable(scored, onCell = { round, player -> selected = round to player })
                } else {
                    ScoreCurve(scored)
                }
            }
            val detail = selected
            if (sidePanel && detail != null) {
                RoundDetailPanel(
                    round = detail.first,
                    player = detail.second,
                    canEdit = game?.status == GameStatus.IN_PROGRESS,
                    onCorrect = {
                        selected = null
                        onEditRound(detail.first.round.id)
                    },
                    onDismiss = { selected = null },
                    modifier = Modifier
                        .width(DETAIL_PANEL_WIDTH)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    selected?.takeIf { !sidePanel }?.let { (round, player) ->
        RoundDetailDialog(
            round = round,
            player = player,
            canEdit = game?.status == GameStatus.IN_PROGRESS,
            onCorrect = {
                selected = null
                onEditRound(round.round.id)
            },
            onDismiss = { selected = null },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.scoreboard_finish_title)) },
            text = { Text(stringResource(R.string.scoreboard_finish_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish(onFinished)
                }) { Text(stringResource(R.string.scoreboard_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ScoreTable(scored: ScoredGame, onCell: (ScoredRound, Player) -> Unit) {
    val players = scored.game.players
    val horizontal = rememberScrollState()
    val bestTotal = scored.totals.values.maxOrNull()

    // Few players: the columns share the width; otherwise fixed width and horizontal scrolling.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val playerWidth = maxOf(PLAYER_COLUMN_WIDTH, (maxWidth - ROUND_COLUMN_WIDTH) / players.size)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.horizontalScroll(horizontal)) {
                Box(Modifier.width(ROUND_COLUMN_WIDTH).height(60.dp))
                players.forEach { player ->
                    Column(
                        modifier = Modifier.width(playerWidth).height(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PlayerAvatar(player, size = 28.dp)
                        Text(
                            player.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(horizontal)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.table_total),
                    modifier = Modifier.width(ROUND_COLUMN_WIDTH).padding(start = 12.dp),
                    fontWeight = FontWeight.Bold,
                )
                players.forEach { player ->
                    val total = scored.totals[player.id] ?: 0
                    Text(
                        text = total.toString(),
                        modifier = Modifier.width(playerWidth).padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (total == bestTotal) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            HorizontalDivider(thickness = 2.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(horizontal),
            ) {
                scored.rounds.forEachIndexed { index, round ->
                    Row(
                        modifier = Modifier.background(
                            if (index % 2 ==
                                1
                            ) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                Color.Transparent
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.width(ROUND_COLUMN_WIDTH).padding(start = 12.dp)) {
                            Text(round.round.number.toString(), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.table_cards_short, round.round.cardsDealt) +
                                    if (round.round.isExtra) " +" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        players.forEach { player ->
                            ScoreCell(round, player, playerWidth, onCell)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ScoreCell(round: ScoredRound, player: Player, width: Dp, onCell: (ScoredRound, Player) -> Unit) {
    val breakdown = round.breakdowns[player.id]
    val description = if (round.isScored && breakdown != null) {
        stringResource(
            R.string.a11y_score_cell,
            player.name,
            round.round.number,
            formatPoints(breakdown.total),
            round.cumulative[player.id] ?: 0,
        )
    } else {
        stringResource(R.string.a11y_score_cell_pending, player.name, round.round.number)
    }
    Column(
        modifier = Modifier
            .width(width)
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = description }
            .clickable(enabled = round.isScored) { onCell(round, player) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (round.isScored && breakdown != null) {
            Text(
                formatPoints(breakdown.total),
                fontWeight = FontWeight.Bold,
                color = if (breakdown.success) MaterialTheme.colorScheme.onSurface else ScoreNegative,
            )
            Text(
                (round.cumulative[player.id] ?: 0).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Calculation detail when a cell is tapped, with access to the correction of the round. */
@Composable
private fun RoundDetailDialog(
    round: ScoredRound,
    player: Player,
    canEdit: Boolean,
    onCorrect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_title, player.name, round.round.number)) },
        text = { RoundBreakdownBody(round, player) },
        confirmButton = {
            if (canEdit) {
                TextButton(onClick = onCorrect) { Text(stringResource(R.string.detail_correct)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (canEdit) TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/** Same detail, shown in a panel next to the table on tablets. */
@Composable
private fun RoundDetailPanel(
    round: ScoredRound,
    player: Player,
    canEdit: Boolean,
    onCorrect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.detail_title, player.name, round.round.number),
                style = MaterialTheme.typography.titleMedium,
            )
            RoundBreakdownBody(round, player)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                if (canEdit) TextButton(onClick = onCorrect) { Text(stringResource(R.string.detail_correct)) }
            }
        }
    }
}

@Composable
private fun RoundBreakdownBody(round: ScoredRound, player: Player) {
    val entry = round.details.entry(player.id)
    val breakdown = round.breakdowns[player.id]
    if (entry != null && breakdown != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_bid_tricks, entry.bid, entry.effectiveBid, entry.tricks),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.cannonball) {
                Text(
                    "💣 " + stringResource(R.string.detail_cannonball),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            DetailRow(
                label = stringResource(
                    if (breakdown.success) R.string.detail_base_success else R.string.detail_base_failure,
                ),
                value = formatPoints(breakdown.base),
            )
            breakdown.bonusLines.forEach { line ->
                DetailRow(
                    label = "${stringResource(line.type.labelRes())} × ${line.count}",
                    value = formatPoints(line.points),
                    struck = breakdown.bonusDiscarded && breakdown.bonusApplied == 0,
                )
            }
            if (breakdown.bonusDiscarded) {
                Text(
                    stringResource(R.string.detail_bonus_lost),
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoreNegative,
                )
            }
            if (breakdown.rascalPoints != 0) {
                DetailRow(stringResource(R.string.detail_rascal), formatPoints(breakdown.rascalPoints))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DetailRow(stringResource(R.string.detail_total), formatPoints(breakdown.total), bold = true)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, struck: Boolean = false, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            fontWeight = if (bold) FontWeight.Bold else null,
            color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            fontWeight = if (bold) FontWeight.Bold else null,
            color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
