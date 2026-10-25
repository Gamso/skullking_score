package com.skullking.score.ui.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.Bonus
import com.skullking.score.core.BonusType
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.RoundWarning
import com.skullking.score.core.ScoreBreakdown
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.model.ScoredRound
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.usecase.EditRoundUseCase
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.BonusCounterList
import com.skullking.score.ui.components.KeepScreenOn
import com.skullking.score.ui.components.LiveEventsPanel
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.Stepper
import com.skullking.score.ui.components.WarningCard
import com.skullking.score.ui.components.formatPoints
import com.skullking.score.ui.components.message
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

data class ResultsUiState(val scored: ScoredGame, val round: ScoredRound, val warnings: List<RoundWarning>) {
    /** Round already validated: it is being corrected. */
    val isEdit: Boolean get() = round.round.phase == RoundPhase.SCORED
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    games: GameRepository,
    private val edit: EditRoundUseCase,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))
    private val roundId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_ROUND_ID))

    val state: StateFlow<ResultsUiState?> = games.observeGame(gameId)
        .filterNotNull()
        .map { details ->
            val scored = GameScoreCalculator.calculate(details)
            scored.round(roundId)?.let { round ->
                ResultsUiState(scored, round, GameScoreCalculator.warnings(scored.game, round.details))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun update(playerId: Long, transform: (PlayerRound) -> PlayerRound) {
        viewModelScope.launch { edit.updateEntry(gameId, roundId, playerId, transform) }
    }

    fun setTricks(playerId: Long, tricks: Int) = update(playerId) { it.copy(tricks = tricks) }

    /** Bid correction after the fact (Harry the Giant: +/-1). */
    fun setFinalBid(playerId: Long, bid: Int) = update(playerId) {
        it.copy(finalBid = if (bid == it.bid) null else bid)
    }

    fun setBonus(playerId: Long, transform: (Bonus) -> Bonus) = update(playerId) {
        it.copy(bonus = transform(it.bonus))
    }

    fun setCannonball(playerId: Long, enabled: Boolean) = update(playerId) { it.copy(cannonball = enabled) }

    fun setWager(playerId: Long, wager: Int) = update(playerId) { it.copy(rascalWager = wager) }

    fun setDiscarded(count: Int) {
        viewModelScope.launch { edit.updateRound(gameId, roundId) { it.copy(discardedTricks = count) } }
    }

    fun addLoot(lootPlayerId: Long, trickWinnerId: Long?) {
        viewModelScope.launch { edit.addLoot(gameId, roundId, lootPlayerId, trickWinnerId) }
    }

    fun addDavyJones(playerId: Long, creatures: Set<SeaMonster>) {
        viewModelScope.launch { edit.addDavyJones(gameId, roundId, playerId, creatures) }
    }

    fun cancelLoot(id: Long) {
        viewModelScope.launch { edit.cancelLoot(gameId, id) }
    }

    fun cancelDavyJones(id: Long) {
        viewModelScope.launch { edit.cancelDavyJones(gameId, id) }
    }

    fun validate(onDone: () -> Unit) {
        viewModelScope.launch {
            edit.setPhase(gameId, roundId, RoundPhase.SCORED)
            onDone()
        }
    }
}

/**
 * Manual entry of a round's results: a first page for the round (discarded tricks, live events,
 * warnings), then one tab per player, to avoid scrolling a long list.
 */
@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onValidated: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmWarnings by remember { mutableStateOf(false) }
    KeepScreenOn()

    SkullScaffold(
        title = if (current == null) {
            stringResource(R.string.app_name)
        } else {
            stringResource(R.string.results_title, current.round.round.number)
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help)) }
        },
        bottomBar = {
            ActionBar(
                primaryText = stringResource(
                    if (current?.isEdit == true) R.string.results_save_correction else R.string.results_validate,
                ),
                primaryEnabled = current != null,
                onPrimary = {
                    if (current?.warnings.isNullOrEmpty()) viewModel.validate(onValidated) else confirmWarnings = true
                },
            )
        },
    ) { padding ->
        if (current == null) return@SkullScaffold
        val game = current.scored.game
        val details = current.round.details
        val tab = selectedTab.coerceIn(0, game.players.size)

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                Tab(
                    selected = tab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        // The warning is already visible here: the badge reminds it from the player tabs.
                        val warning = if (current.warnings.isEmpty() || tab == 0) "" else " ⚠️"
                        Text(stringResource(R.string.results_tab_round) + warning)
                    },
                )
                game.players.forEachIndexed { index, player ->
                    Tab(
                        selected = tab == index + 1,
                        onClick = { selectedTab = index + 1 },
                        text = { Text(player.name, maxLines = 1) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) {
                    RoundPage(current, viewModel)
                } else {
                    val player = game.players[tab - 1]
                    val entry = details.entry(player.id)
                    val breakdown = current.round.breakdowns[player.id]
                    if (entry != null && breakdown != null) {
                        PlayerPage(
                            player = player,
                            entry = entry,
                            breakdown = breakdown,
                            cards = details.round.cardsDealt,
                            expansion = game.expansionCards,
                            cannonballAvailable = game.mode == ScoringMode.CANNONBALL,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }

    if (confirmWarnings && current != null) {
        AlertDialog(
            onDismissRequest = { confirmWarnings = false },
            title = { Text(stringResource(R.string.play_warnings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.warnings.forEach { Text(it.message(), style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWarnings = false
                    viewModel.validate(onValidated)
                }) { Text(stringResource(R.string.play_validate_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWarnings = false }) { Text(stringResource(R.string.play_fix)) }
            },
        )
    }
}

/** Round page: discarded tricks, consistency warnings and live events. */
@Composable
private fun RoundPage(state: ResultsUiState, viewModel: ResultsViewModel) {
    val details = state.round.details
    if (details.tricks.isNotEmpty()) {
        Text(
            stringResource(R.string.results_tricks_prefilled),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.isEdit) {
        Text(
            stringResource(R.string.results_editing_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionTitle(stringResource(R.string.results_discarded_title))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.results_discarded_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.results_discarded_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Stepper(
            value = details.round.discardedTricks,
            onValueChange = viewModel::setDiscarded,
            range = 0..details.round.cardsDealt,
        )
    }

    state.warnings.forEach { WarningCard(it.message()) }

    SectionTitle(stringResource(R.string.play_live_events))
    LiveEventsPanel(
        game = state.scored.game,
        round = details,
        onAddLoot = viewModel::addLoot,
        onAddDavyJones = viewModel::addDavyJones,
        onCancelLoot = viewModel::cancelLoot,
        onCancelDavyJones = viewModel::cancelDavyJones,
    )
}

/** Player page: tricks, final bid, cards won, Rascal wager (and Cannonball if enabled). */
@Composable
private fun PlayerPage(
    player: Player,
    entry: PlayerRound,
    breakdown: ScoreBreakdown,
    cards: Int,
    expansion: Set<ExpansionCard>,
    cannonballAvailable: Boolean,
    viewModel: ResultsViewModel,
) {
    val failed = !breakdown.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.results_summary, entry.effectiveBid, entry.tricks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Red if the bid is missed
        Text(
            formatPoints(breakdown.total),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (failed) ScoreNegative else MaterialTheme.colorScheme.onSurface,
        )
    }

    if (cannonballAvailable) {
        CannonballRow(checked = entry.cannonball, onChange = { viewModel.setCannonball(player.id, it) })
    }
    LabeledStepper(stringResource(R.string.results_tricks), entry.tricks, 0..cards) {
        viewModel.setTricks(player.id, it)
    }
    LabeledStepper(stringResource(R.string.results_final_bid), entry.effectiveBid, 0..cards) {
        viewModel.setFinalBid(player.id, it)
    }

    SectionTitle(stringResource(R.string.results_bonus_title))
    if (breakdown.bonusDiscarded) {
        Text(
            stringResource(R.string.results_bonus_ignored),
            style = MaterialTheme.typography.bodySmall,
            color = ScoreNegative,
        )
    }
    // Bonus entered on a missed bid: kept but greyed out, counted as 0.
    Column(
        modifier = Modifier.alpha(if (failed) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BonusCounterList(
            bonus = entry.bonus,
            expansion = expansion,
            onChange = { updated -> viewModel.setBonus(player.id) { updated } },
        )
        breakdown.bonusLines
            .filter { it.type == BonusType.LOOT || it.type == BonusType.DAVY_JONES }
            .forEach {
                Text(
                    "${stringResource(it.type.labelRes())} : ${it.count} (${formatPoints(it.points)})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
    }

    Text(stringResource(R.string.results_rascal_wager), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 10, 20).forEach { wager ->
            FilterChip(
                selected = entry.rascalWager == wager,
                onClick = { viewModel.setWager(player.id, wager) },
                label = { Text(wager.toString()) },
            )
        }
    }
}

@Composable
private fun LabeledStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Stepper(value = value, onValueChange = onChange, range = range)
    }
}

@Composable
private fun CannonballRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("💣 " + stringResource(R.string.results_cannonball), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.results_cannonball_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
