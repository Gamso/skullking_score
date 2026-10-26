package com.skullking.score.ui.feature.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.DeckRules
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.ScoringMode
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.PlayerRepository
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.PlayerEditDialog
import com.skullking.score.ui.components.PlayerLabel
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.descriptionRes
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.normalizeForSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val available: List<Player> = emptyList(),
    /** Chosen players, in table order. */
    val selected: List<Player> = emptyList(),
    val mode: ScoringMode = ScoringMode.CLASSIC,
    val expansionEnabled: Boolean = false,
    val chosenCards: Set<ExpansionCard> = emptySet(),
    val davyJonesRequiresSuccess: Boolean = true,
    /** Seat of the dealer of the first round within [selected]. */
    val firstDealerSeat: Int = 0,
) {
    /** Cards actually used: some are removed with 2 players. */
    val effectiveCards: Set<ExpansionCard>
        get() = if (expansionEnabled) chosenCards.filter { it.availableFor(selected.size) }.toSet() else emptySet()

    @get:StringRes
    val hint: Int?
        get() = when {
            selected.size < DeckRules.MIN_PLAYERS -> R.string.setup_hint_min_players
            selected.size > DeckRules.MAX_PLAYERS_BASE && effectiveCards.isEmpty() -> R.string.setup_hint_nine_players
            selected.size > DeckRules.MAX_PLAYERS_EXPANSION -> R.string.setup_hint_max_players
            else -> null
        }

    val canStart: Boolean get() = hint == null
}

private data class SetupInput(
    val selectedIds: List<Long> = emptyList(),
    val mode: ScoringMode = ScoringMode.CLASSIC,
    val expansionEnabled: Boolean = false,
    val chosenCards: Set<ExpansionCard> = emptySet(),
    val davyJonesRequiresSuccess: Boolean = true,
    val firstDealerSeat: Int = 0,
)

@HiltViewModel
class SetupViewModel @Inject constructor(private val players: PlayerRepository, private val games: GameRepository) :
    ViewModel() {

    private val input = MutableStateFlow(SetupInput())

    val state: StateFlow<SetupUiState> = combine(players.observePlayers(), input) { all, input ->
        val active = all.filter { !it.archived }
        val byId = active.associateBy { it.id }
        val selected = input.selectedIds.mapNotNull { byId[it] }
        SetupUiState(
            available = active.filter { it.id !in input.selectedIds },
            selected = selected,
            mode = input.mode,
            expansionEnabled = input.expansionEnabled,
            chosenCards = input.chosenCards,
            davyJonesRequiresSuccess = input.davyJonesRequiresSuccess,
            firstDealerSeat = input.firstDealerSeat.coerceIn(0, maxOf(selected.size - 1, 0)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun addPlayer(player: Player) = input.update { it.copy(selectedIds = it.selectedIds + player.id) }

    fun removePlayer(player: Player) = input.update { it.copy(selectedIds = it.selectedIds - player.id) }

    fun drawDealer() = input.update {
        if (it.selectedIds.isEmpty()) it else it.copy(firstDealerSeat = Random.nextInt(it.selectedIds.size))
    }

    fun setDealer(player: Player) = input.update { current ->
        current.copy(firstDealerSeat = current.selectedIds.indexOf(player.id).coerceAtLeast(0))
    }

    fun movePlayer(player: Player, delta: Int) = input.update { current ->
        val ids = current.selectedIds.toMutableList()
        val from = ids.indexOf(player.id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) {
            current
        } else {
            ids.add(to, ids.removeAt(from))
            current.copy(selectedIds = ids)
        }
    }

    fun createPlayer(name: String) {
        viewModelScope.launch {
            val id = players.createPlayer(name)
            input.update { it.copy(selectedIds = it.selectedIds + id) }
        }
    }

    fun setMode(mode: ScoringMode) = input.update { it.copy(mode = mode) }

    fun setExpansionEnabled(enabled: Boolean) = input.update {
        // First activation: all cards are offered, to be unchecked one by one.
        it.copy(
            expansionEnabled = enabled,
            chosenCards = if (enabled && it.chosenCards.isEmpty()) ExpansionCard.entries.toSet() else it.chosenCards,
        )
    }

    fun setCard(card: ExpansionCard, enabled: Boolean) = input.update {
        it.copy(chosenCards = if (enabled) it.chosenCards + card else it.chosenCards - card)
    }

    fun setDavyJonesRequiresSuccess(value: Boolean) = input.update { it.copy(davyJonesRequiresSuccess = value) }

    fun start(onCreated: (Long) -> Unit) {
        val current = state.value
        if (!current.canStart) return
        viewModelScope.launch {
            val id = games.createGame(
                playerIds = current.selected.map { it.id },
                mode = current.mode,
                expansionCards = current.effectiveCards,
                davyJonesRequiresSuccess = current.davyJonesRequiresSuccess,
                firstDealerSeat = current.firstDealerSeat,
            )
            onCreated(id)
        }
    }
}

@Suppress("LongMethod")
@Composable
fun SetupScreen(onBack: () -> Unit, onGameCreated: (Long) -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    SkullScaffold(
        title = stringResource(R.string.setup_title),
        onBack = onBack,
        bottomBar = {
            Column {
                state.hint?.let {
                    Text(
                        stringResource(it),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ActionBar(
                    primaryText = stringResource(R.string.setup_start),
                    primaryEnabled = state.canStart,
                    onPrimary = { viewModel.start(onGameCreated) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle(stringResource(R.string.setup_players_order, state.selected.size)) }
            if (state.selected.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.setup_dealer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = viewModel::drawDealer) {
                            Text(stringResource(R.string.setup_draw_dealer))
                        }
                    }
                }
            }
            itemsIndexed(state.selected, key = { _, p -> "sel-${p.id}" }) { index, player ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PlayerLabel(player, modifier = Modifier.weight(1f))
                        if (state.selected.size > 1) {
                            val isDealer = index == state.firstDealerSeat
                            TextButton(onClick = { viewModel.setDealer(player) }) {
                                Text(
                                    if (isDealer) "🃏 " + stringResource(R.string.setup_dealer_badge) else "🃏",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isDealer) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    fontWeight = if (isDealer) FontWeight.Bold else null,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.movePlayer(player, -1) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { viewModel.movePlayer(player, 1) }) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_down),
                            )
                        }
                        IconButton(onClick = { viewModel.removePlayer(player) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.setup_available_players)) }
            item {
                AvailablePlayers(
                    players = state.available,
                    onAdd = viewModel::addPlayer,
                    onCreate = { creating = true },
                )
            }

            item { SectionTitle(stringResource(R.string.setup_mode)) }
            items(ScoringMode.entries.toList()) { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setMode(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.mode == mode, onClick = { viewModel.setMode(mode) })
                    Column {
                        Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(mode.descriptionRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.setup_expansion)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.setup_expansion_switch),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.setup_expansion_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.expansionEnabled, onCheckedChange = viewModel::setExpansionEnabled)
                }
            }
            if (state.expansionEnabled) {
                items(ExpansionCard.entries.toList()) { card ->
                    val available = card.availableFor(state.selected.size)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(card.labelRes()))
                            if (!available) {
                                Text(
                                    stringResource(R.string.setup_card_removed_two_players),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = available && card in state.chosenCards,
                            enabled = available,
                            onCheckedChange = { viewModel.setCard(card, it) },
                        )
                    }
                }
                if (ExpansionCard.DAVY_JONES_CHEST in state.effectiveCards) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.setup_dj_requires_success),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.setup_dj_requires_success_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.davyJonesRequiresSuccess,
                                onCheckedChange = viewModel::setDavyJonesRequiresSuccess,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        PlayerEditDialog(
            initial = null,
            onConfirm = { name ->
                viewModel.createPlayer(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

private const val SEARCH_THRESHOLD = 8

/** Available players as compact chips (a tap adds them), with a search when the list is long. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailablePlayers(players: List<Player>, onAdd: (Player) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = remember(query) { normalizeForSearch(query.trim()) }
    val shown = remember(players, needle) { players.filter { normalizeForSearch(it.name).contains(needle) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (players.size > SEARCH_THRESHOLD) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.setup_search_players)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { player ->
                AssistChip(
                    onClick = { onAdd(player) },
                    label = { Text(player.name) },
                    leadingIcon = { PlayerAvatar(player, size = 24.dp) },
                )
            }
            AssistChip(
                onClick = onCreate,
                label = { Text(stringResource(R.string.player_new)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
