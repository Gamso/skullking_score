package com.skullking.score.ui.feature.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.skullking.score.domain.repository.SavedSetup
import com.skullking.score.domain.repository.SettingsRepository
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
    val firstDealerSeat: Int = 0,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val games: GameRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    // Starts from the previous game's mode and expansion if the setting is on.
    private val input = MutableStateFlow(
        settings.settings.value.takeIf { it.rememberSetup }?.lastSetup?.let {
            SetupInput(
                mode = it.mode,
                expansionEnabled = it.expansionEnabled,
                chosenCards = it.cards,
            )
        } ?: SetupInput(),
    )

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
        val from = current.selectedIds.indexOf(player.id)
        current.reordered(from, from + delta)
    }

    /** Drag and drop: player [playerId] takes the place of player [targetId]. */
    fun movePlayerTo(playerId: Long, targetId: Long) = input.update { current ->
        current.reordered(current.selectedIds.indexOf(playerId), current.selectedIds.indexOf(targetId))
    }

    /** Moves the player at [from] to [to]; the first dealer stays the same player. */
    private fun SetupInput.reordered(from: Int, to: Int): SetupInput {
        val ids = selectedIds.toMutableList()
        if (from < 0 || to !in ids.indices) return this
        val dealer = ids.getOrNull(firstDealerSeat)
        ids.add(to, ids.removeAt(from))
        return copy(selectedIds = ids, firstDealerSeat = ids.indexOf(dealer).coerceAtLeast(0))
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

    fun start(onCreated: (Long) -> Unit) {
        val current = state.value
        if (!current.canStart) return
        val chosen = input.value
        settings.update {
            it.copy(
                lastSetup = SavedSetup(
                    mode = current.mode,
                    expansionEnabled = chosen.expansionEnabled,
                    cards = chosen.chosenCards,
                ),
            )
        }
        viewModelScope.launch {
            val id = games.createGame(
                playerIds = current.selected.map { it.id },
                mode = current.mode,
                expansionCards = current.effectiveCards,
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
        val listState = rememberLazyListState()
        val reorder = remember(listState) { PlayerReorderState(listState) }
        val dealerId = state.selected.getOrNull(state.firstDealerSeat)?.id
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Profiles first: they stay in sight however many players are already chosen.
            item { SectionTitle(stringResource(R.string.setup_available_players)) }
            item {
                AvailablePlayers(
                    players = state.available,
                    onAdd = viewModel::addPlayer,
                    onCreate = { creating = true },
                )
            }

            item { SectionTitle(stringResource(R.string.setup_players_order, state.selected.size)) }
            if (state.selected.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.setup_dealer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::drawDealer) {
                                Text(stringResource(R.string.setup_draw_dealer))
                            }
                            ChooseDealerButton(state.selected, onChoose = viewModel::setDealer)
                        }
                    }
                }
            }
            items(state.selected, key = { PlayerReorderState.key(it.id) }) { player ->
                val dragging = reorder.draggedId == player.id
                val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
                    change.consume()
                    reorder.drag(amount.y)?.let { target -> viewModel.movePlayerTo(player.id, target) }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = reorder.offsetOf(player.id)
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                        }
                        // Long press anywhere on the line, or immediate drag on the handle.
                        .pointerInput(player.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { reorder.start(player.id) },
                                onDragEnd = reorder::stop,
                                onDragCancel = reorder::stop,
                                onDrag = onDrag,
                            )
                        },
                ) {
                    val isDealer = player.id == dealerId && state.selected.size > 1
                    val dealerDescription = stringResource(R.string.setup_dealer_badge)
                    Row(
                        modifier = Modifier.padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.setup_drag_handle),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .pointerInput(player.id) {
                                    detectDragGestures(
                                        onDragStart = { reorder.start(player.id) },
                                        onDragEnd = reorder::stop,
                                        onDragCancel = reorder::stop,
                                        onDrag = onDrag,
                                    )
                                }
                                .padding(12.dp),
                        )
                        // The dealer is chosen with the buttons above; the line only shows the icon, so the
                        // name keeps its room.
                        PlayerAvatar(player, size = 32.dp)
                        Text(
                            player.name,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDealer) {
                            Text(
                                "🃏",
                                modifier = Modifier.semantics {
                                    contentDescription = dealerDescription
                                },
                            )
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

/**
 * Drag and drop of the chosen players in the [LazyColumn]: the dragged line follows the finger and takes the place
 * of the line it covers. Its shift is computed from the layout, so it stays under the finger after each move.
 */
private class PlayerReorderState(private val listState: LazyListState) {
    var draggedId by mutableStateOf<Long?>(null)
        private set
    private var startOffset by mutableFloatStateOf(0f)
    private var distance by mutableFloatStateOf(0f)

    /** Index the dragged line must reach before another move: the list updates asynchronously. */
    private var pendingIndex: Int? = null

    private fun info(id: Long) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key(id) }

    fun offsetOf(id: Long): Float =
        if (id != draggedId) 0f else info(id)?.let { startOffset + distance - it.offset } ?: 0f

    fun start(id: Long) {
        draggedId = id
        startOffset = info(id)?.offset?.toFloat() ?: 0f
        distance = 0f
        pendingIndex = null
    }

    fun stop() {
        draggedId = null
        distance = 0f
        pendingIndex = null
    }

    /** Follows the finger; returns the id of the player whose place the dragged line now takes, if any. */
    fun drag(dy: Float): Long? {
        distance += dy
        val current = draggedId?.let(::info)
        // Wait for the previous move to reach the layout before moving again.
        if (current == null || (pendingIndex != null && current.index != pendingIndex)) return null
        pendingIndex = null
        val center = startOffset + distance + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != current.key &&
                (it.key as? String)?.startsWith(PREFIX) == true &&
                center > it.offset &&
                center < it.offset + it.size
        }
        pendingIndex = target?.index
        return (target?.key as? String)?.removePrefix(PREFIX)?.toLong()
    }

    companion object {
        private const val PREFIX = "sel-"

        fun key(id: Long) = "$PREFIX$id"
    }
}

/** Manual choice of the first dealer, next to the random draw. */
@Composable
private fun ChooseDealerButton(players: List<Player>, onChoose: (Player) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.setup_choose_dealer)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    leadingIcon = { PlayerAvatar(player, size = 24.dp) },
                    onClick = {
                        open = false
                        onChoose(player)
                    },
                )
            }
        }
    }
}
