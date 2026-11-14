package com.gamso.skullkingscore.ui.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.BonusType
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.RoundWarning
import com.gamso.skullkingscore.core.ScoreBreakdown
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.ScoredGame
import com.gamso.skullkingscore.domain.model.ScoredRound
import com.gamso.skullkingscore.domain.model.TrickDraft
import com.gamso.skullkingscore.domain.repository.GameRepository
import com.gamso.skullkingscore.domain.scoring.GameScoreCalculator
import com.gamso.skullkingscore.domain.usecase.EditRoundUseCase
import com.gamso.skullkingscore.ui.components.ActionBar
import com.gamso.skullkingscore.ui.components.BonusCounterList
import com.gamso.skullkingscore.ui.components.CompactChip
import com.gamso.skullkingscore.ui.components.DeltaStepper
import com.gamso.skullkingscore.ui.components.KeepScreenOn
import com.gamso.skullkingscore.ui.components.LiveEventsPanel
import com.gamso.skullkingscore.ui.components.PlayerAvatar
import com.gamso.skullkingscore.ui.components.SectionTitle
import com.gamso.skullkingscore.ui.components.SkullScaffold
import com.gamso.skullkingscore.ui.components.TrickDialog
import com.gamso.skullkingscore.ui.components.TrickEvents
import com.gamso.skullkingscore.ui.components.WarningCard
import com.gamso.skullkingscore.ui.components.formatPoints
import com.gamso.skullkingscore.ui.components.message
import com.gamso.skullkingscore.ui.components.trickEventLines
import com.gamso.skullkingscore.ui.components.trickLabel
import com.gamso.skullkingscore.ui.labelRes
import com.gamso.skullkingscore.ui.navigation.Routes
import com.gamso.skullkingscore.ui.theme.ScoreNegative
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

    private val cards: Int? get() = state.value?.round?.round?.cardsDealt

    /** Adds [step] to the stored tricks won, inside the update: fast taps all count. */
    fun stepTricks(playerId: Long, step: Int) {
        val cards = cards ?: return
        update(playerId) { it.copy(tricks = (it.tricks + step).coerceIn(0, cards)) }
    }

    /** Bid correction after the fact (Harry the Giant: +/-1), [step] added to the stored bid. */
    fun stepFinalBid(playerId: Long, step: Int) {
        val cards = cards ?: return
        update(playerId) {
            val bid = (it.effectiveBid + step).coerceIn(0, cards)
            it.copy(finalBid = if (bid == it.bid) null else bid)
        }
    }

    fun setBonus(playerId: Long, transform: (Bonus) -> Bonus) = update(playerId) {
        it.copy(bonus = transform(it.bonus))
    }

    fun setCannonball(playerId: Long, enabled: Boolean) = update(playerId) { it.copy(cannonball = enabled) }

    fun setWager(playerId: Long, wager: Int) = update(playerId) { it.copy(rascalWager = wager) }

    fun stepDiscarded(step: Int) {
        viewModelScope.launch {
            edit.updateRound(gameId, roundId) {
                it.copy(discardedTricks = (it.discardedTricks + step).coerceIn(0, it.cardsDealt))
            }
        }
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

    /** Corrects trick [number] (and records the events added in the dialog) like during play. */
    fun recordTrick(number: Int, draft: TrickDraft, events: TrickEvents) {
        viewModelScope.launch {
            edit.recordTrick(gameId, roundId, number, draft, events.lootPlayerIds, events.chests)
        }
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
    var confirmWarnings by rememberSaveable { mutableStateOf(false) }
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
            // "Round", then one chip per player, wrapping: every page is reachable without scrolling sideways.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // The warning is already visible on the round page: the badge reminds it from the player pages.
                val warning = if (current.warnings.isEmpty() || tab == 0) "" else " ⚠️"
                CompactChip(
                    text = stringResource(R.string.results_tab_round) + warning,
                    selected = tab == 0,
                    onClick = { selectedTab = 0 },
                )
                game.players.forEachIndexed { index, player ->
                    CompactChip(
                        text = player.name,
                        selected = tab == index + 1,
                        onClick = { selectedTab = index + 1 },
                        leading = { PlayerAvatar(player, size = 20.dp) },
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * Round page: the tricks entered during play (a tap corrects one with the same dialog), or the discarded tricks
 * for a round without them; then the consistency warnings and the events.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundPage(state: ResultsUiState, viewModel: ResultsViewModel) {
    val details = state.round.details
    val game = state.scored.game
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    if (state.isEdit) {
        Text(
            stringResource(R.string.results_editing_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (details.tricks.isNotEmpty()) {
        SectionTitle(stringResource(R.string.play_tricks_title))
        Text(
            stringResource(R.string.results_tap_trick),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val names = game.players.associate { it.id to it.name }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            details.tricks.forEach { trick ->
                SuggestionChip(onClick = { editing = trick.number }, label = { Text(trickLabel(trick, names)) })
            }
        }
    } else {
        DiscardedTricks(details.round.discardedTricks, details.round.cardsDealt, viewModel::stepDiscarded)
    }

    state.warnings.forEach { WarningCard(it.message()) }

    // With tricks, events are added in their trick: the log is only shown when there is something in it.
    val hasEvents = details.loots.isNotEmpty() || details.captures.isNotEmpty()
    if (details.tricks.isEmpty() || hasEvents) {
        SectionTitle(stringResource(R.string.play_live_events))
        LiveEventsPanel(
            game = game,
            round = details,
            onAddLoot = viewModel::addLoot,
            onAddDavyJones = viewModel::addDavyJones,
            onCancelLoot = viewModel::cancelLoot,
            onCancelDavyJones = viewModel::cancelDavyJones,
            // With tricks, the events are added in the trick they were played in.
            showButtons = details.tricks.isEmpty(),
        )
    }

    editing?.let { number ->
        val existing = details.tricks.firstOrNull { it.number == number } ?: return@let
        TrickDialog(
            trickNumber = number,
            players = game.players,
            expansion = game.expansionCards,
            initial = TrickDraft(
                existing.winnerId,
                existing.bonus,
                existing.rascalWager,
                existing.bidAdjustment,
                existing.winningCard,
                existing.winningPirate,
            ),
            recordedEvents = trickEventLines(details, number, game.players.associate { it.id to it.name }),
            isLastTrick = number == details.round.cardsDealt,
            lootsPlayed = details.activeLoots.size,
            chestsPlayed = details.activeCaptures.size,
            eventPlayerIds = details.eventPlayerIds(number),
            chestPlayerIds = details.chestPlayerIds(number),
            onConfirm = { draft, events ->
                viewModel.recordTrick(number, draft, events)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Tricks nobody won, entered by hand for a round recorded without its tricks. */
@Composable
private fun DiscardedTricks(count: Int, cards: Int, onStep: (Int) -> Unit) {
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
        DeltaStepper(value = count, onStep = onStep, range = 0..cards)
    }
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
    val gap = entry.tricks - entry.effectiveBid
    // Header: the bid status replaces the "bid · tricks" reminder, already visible in the counters below.
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player, size = 32.dp)
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    gap == 0 -> "✓ " + stringResource(R.string.results_bid_made)
                    gap > 0 -> "✗ " + stringResource(R.string.results_bid_missed_over, gap)
                    else -> "✗ " + stringResource(R.string.results_bid_missed_under, -gap)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (gap == 0) MaterialTheme.colorScheme.primary else ScoreNegative,
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
    // Bid then tricks, side by side: the same order as at the table. The bid stays editable (Harry the Giant).
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledStepper(
            label = stringResource(R.string.results_final_bid),
            value = entry.effectiveBid,
            range = 0..cards,
            modifier = Modifier.weight(1f),
        ) { viewModel.stepFinalBid(player.id, it) }
        LabeledStepper(
            label = stringResource(R.string.results_tricks),
            value = entry.tricks,
            range = 0..cards,
            modifier = Modifier.weight(1f),
        ) { viewModel.stepTricks(player.id, it) }
    }
    if (entry.effectiveBid != entry.bid) {
        Text(
            stringResource(R.string.results_initial_bid, entry.bid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BonusCounterList(
            bonus = entry.bonus,
            expansion = expansion,
            onChange = { transform -> viewModel.setBonus(player.id, transform) },
            compact = true,
        )
        breakdown.bonusLines
            .filter { it.type == BonusType.LOOT || it.type == BonusType.DAVY_JONES }
            .forEach {
                Text(
                    stringResource(
                        R.string.results_bonus_count,
                        stringResource(it.type.labelRes()),
                        it.count,
                        formatPoints(it.points),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.results_rascal_wager),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        listOf(0, 10, 20).forEach { wager ->
            CompactChip(
                text = wager.toString(),
                selected = entry.rascalWager == wager,
                onClick = { viewModel.setWager(player.id, wager) },
            )
        }
    }
}

/** Small label above a compact counter (sending its step): two of them fit on one line. */
@Composable
private fun LabeledStepper(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onStep: (Int) -> Unit,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        DeltaStepper(value = value, onStep = onStep, range = range, compact = true)
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
