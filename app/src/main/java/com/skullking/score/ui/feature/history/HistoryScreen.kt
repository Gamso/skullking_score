package com.skullking.score.ui.feature.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.core.ScoringMode
import com.skullking.score.data.export.FileGateway
import com.skullking.score.domain.export.CsvExporter
import com.skullking.score.domain.export.ExportFilter
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.GameSummary
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.nextStep
import com.skullking.score.domain.repository.BackupRepository
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.repository.ImportOptions
import com.skullking.score.domain.repository.ImportPreview
import com.skullking.score.domain.repository.InvalidBackupException
import com.skullking.score.domain.repository.NewerBackupException
import com.skullking.score.domain.repository.PlayerRepository
import com.skullking.score.domain.stats.StatsPeriod
import com.skullking.score.ui.components.PlayerAvatar
import com.skullking.score.ui.components.SectionTitle
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.components.formatScore
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.navigation.Routes
import com.skullking.score.ui.normalizeForSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExportFormat { CSV, JSON }

data class ExportFile(val uri: Uri, val mimeType: String)

data class PendingImport(val json: String, val preview: ImportPreview)

data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val games: GameRepository,
    players: PlayerRepository,
    private val backup: BackupRepository,
    private val files: FileGateway,
) : ViewModel() {

    val summaries: StateFlow<List<GameSummary>> =
        games.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val players: StateFlow<List<Player>> =
        players.observePlayers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun open(gameId: Long, onRoute: (String) -> Unit) {
        viewModelScope.launch {
            val details = games.getGame(gameId) ?: return@launch
            onRoute(Routes.forStep(gameId, details.nextStep()))
        }
    }

    fun delete(gameId: Long) {
        viewModelScope.launch { games.deleteGame(gameId) }
    }

    /** Prepares the file to share; `null` if the filter keeps no game. */
    suspend fun prepareExport(format: ExportFormat, filter: ExportFilter): ExportFile? {
        val selected = filter.apply(games.getAllGames())
        if (selected.isEmpty()) {
            _messages.emit(UiMessage(R.string.export_nothing))
            return null
        }
        val stamp = LocalDate.now().toString()
        return when (format) {
            ExportFormat.CSV -> ExportFile(
                files.writeShareable(
                    "skullking-$stamp.csv",
                    CsvExporter.export(selected, filter.playerId),
                    withBom = true,
                ),
                "text/csv",
            )

            ExportFormat.JSON -> ExportFile(
                files.writeShareable("skullking-$stamp.json", backup.exportJson(selected), withBom = false),
                "application/json",
            )
        }
    }

    fun loadImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = files.readText(uri)
                _pendingImport.value = PendingImport(json, backup.previewImport(json))
            } catch (ignored: NewerBackupException) {
                _messages.emit(UiMessage(R.string.import_too_new))
            } catch (e: InvalidBackupException) {
                _messages.emit(UiMessage(R.string.import_invalid))
            } catch (e: java.io.IOException) {
                _messages.emit(UiMessage(R.string.import_invalid))
            }
        }
    }

    fun confirmImport(options: ImportOptions) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            try {
                val result = backup.importJson(pending.json, options)
                _messages.emit(
                    UiMessage(
                        R.string.import_done_detail,
                        listOf(result.importedGames, result.skippedGames, result.mergedPlayers, result.createdPlayers),
                    ),
                )
            } catch (ignored: NewerBackupException) {
                _messages.emit(UiMessage(R.string.import_too_new))
            } catch (e: InvalidBackupException) {
                _messages.emit(UiMessage(R.string.import_invalid))
            }
        }
    }

    fun dismissImport() {
        _pendingImport.value = null
    }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (route: String) -> Unit,
    onShare: (gameId: Long) -> Unit,
    startImport: Boolean = false,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val players by viewModel.players.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GameSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var modeFilter by rememberSaveable { mutableStateOf<ScoringMode?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadImport(uri)
    }

    LaunchedEffect(startImport) {
        if (startImport) importLauncher.launch(arrayOf("*/*"))
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(resources.getString(message.res, *message.args.toTypedArray()))
        }
    }

    SkullScaffold(
        title = stringResource(R.string.home_history),
        onBack = onBack,
        maxContentWidth = Dp.Unspecified,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            TextButton(onClick = {
                importLauncher.launch(arrayOf("*/*"))
            }) { Text(stringResource(R.string.import_action)) }
            IconButton(onClick = { exporting = true }) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export_action))
            }
        },
    ) { padding ->
        val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
        val dates = remember(summaries) {
            summaries.associate { it.game.id to dateFormat.format(Date(it.game.startedAt)) }
        }
        val modeLabels = ScoringMode.entries.associateWith { resources.getString(it.labelRes()) }
        val visible = remember(summaries, query, modeFilter) {
            val needle = normalizeForSearch(query.trim())
            summaries.filter { summary ->
                val haystack = normalizeForSearch(
                    (
                        summary.game.players.map { it.name } +
                            listOf(dates[summary.game.id].orEmpty(), modeLabels[summary.game.mode].orEmpty())
                        ).joinToString(" "),
                )
                (modeFilter == null || summary.game.mode == modeFilter) && haystack.contains(needle)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_MIN_WIDTH),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summaries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HistoryFilters(
                        query = query,
                        onQuery = { query = it },
                        modeFilter = modeFilter,
                        onMode = { modeFilter = it },
                        modeLabels = modeLabels,
                    )
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.history_no_match),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            gridItems(visible, key = { it.game.id }) { summary ->
                GameCard(
                    summary = summary,
                    date = dates[summary.game.id].orEmpty(),
                    onOpen = { viewModel.open(summary.game.id, onOpen) },
                    onShare = { onShare(summary.game.id) },
                    onDelete = { deleting = summary },
                )
            }
        }
    }

    if (exporting) {
        ExportDialog(
            players = players,
            onDismiss = { exporting = false },
            onExport = { format, filter ->
                exporting = false
                scope.launch {
                    viewModel.prepareExport(format, filter)?.let { share(context, it) }
                }
            },
        )
    }

    deleting?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(summary.game.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingImport?.let { pending ->
        ImportDialog(
            preview = pending.preview,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport,
        )
    }
}

private fun share(context: Context, file: ExportFile) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_action)))
}

@Composable
private fun HistoryFilters(
    query: String,
    onQuery: (String) -> Unit,
    modeFilter: ScoringMode?,
    onMode: (ScoringMode?) -> Unit,
    modeLabels: Map<ScoringMode, String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.history_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
            },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = modeFilter == null,
                onClick = { onMode(null) },
                label = { Text(stringResource(R.string.history_filter_all)) },
            )
            ScoringMode.entries.forEach { mode ->
                FilterChip(
                    selected = modeFilter == mode,
                    onClick = { onMode(if (modeFilter == mode) null else mode) },
                    label = { Text(modeLabels.getValue(mode)) },
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    summary: GameSummary,
    date: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val game = summary.game
    var expanded by rememberSaveable(game.id) { mutableStateOf(false) }
    val finished = game.status == GameStatus.FINISHED
    val expandedState = stringResource(if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed)
    val winnerIds = summary.winners.map { it.id }.toSet()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = expandedState
                        role = Role.Button
                    }
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            if (finished) R.string.history_status_finished else R.string.history_status_in_progress,
                            stringResource(game.mode.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (finished && summary.winners.isNotEmpty()) {
                        Text(
                            "🏆 " + summary.winners.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    game.players.sortedByDescending { summary.totals[it.id] ?: 0.0 }.forEach { player ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            PlayerAvatar(player, size = 28.dp)
                            Text(
                                text = (if (player.id in winnerIds && finished) "🏆 " else "") + player.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                            Text(formatScore(summary.totals[player.id] ?: 0.0), fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                            if (finished) {
                                IconButton(onClick = onShare) {
                                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_action))
                                }
                            }
                        }
                        Button(onClick = onOpen) { Text(stringResource(R.string.history_open)) }
                    }
                }
            }
        }
    }
}

private val GRID_MIN_WIDTH = 360.dp

@Composable
private fun ExportDialog(
    players: List<Player>,
    onDismiss: () -> Unit,
    onExport: (ExportFormat, ExportFilter) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.CSV) }
    var period by remember { mutableStateOf(StatsPeriod.ALL) }
    var playerId by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.export_format))
                ExportFormat.entries.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { format = f },
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f })
                        Column {
                            Text(f.name, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(
                                    if (f ==
                                        ExportFormat.CSV
                                    ) {
                                        R.string.export_csv_desc
                                    } else {
                                        R.string.export_json_desc
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SectionTitle(stringResource(R.string.export_period))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsPeriod.entries.reversed().forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(stringResource(periodLabel(p))) },
                        )
                    }
                }
                SectionTitle(stringResource(R.string.export_player))
                Column {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text(
                            players.firstOrNull { it.id == playerId }?.name
                                ?: stringResource(R.string.export_all_players),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_all_players)) },
                            onClick = {
                                playerId = null
                                menuOpen = false
                            },
                        )
                        players.forEach { player ->
                            DropdownMenuItem(
                                text = { Text(player.name) },
                                onClick = {
                                    playerId = player.id
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(format, ExportFilter(period, playerId)) }) {
                Text(stringResource(R.string.export_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@StringRes
internal fun periodLabel(period: StatsPeriod): Int = when (period) {
    StatsPeriod.MONTH -> R.string.period_month
    StatsPeriod.YEAR -> R.string.period_year
    StatsPeriod.ALL -> R.string.period_all
}

/** Import preview: the whole file is imported; only players with the same name can be chosen. */
@Composable
private fun ImportDialog(preview: ImportPreview, onConfirm: (ImportOptions) -> Unit, onDismiss: () -> Unit) {
    var merged by remember(preview) { mutableStateOf(preview.nameConflicts.map { it.importedUuid }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text(
                        stringResource(
                            R.string.import_preview,
                            preview.newGames,
                            preview.alreadyPresentGames,
                            preview.newPlayers,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (preview.nameConflicts.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.import_homonyms)) }
                    items(preview.nameConflicts, key = { "c-" + it.importedUuid }) { conflict ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(
                                    if (conflict.importedUuid in
                                        merged
                                    ) {
                                        R.string.import_merge
                                    } else {
                                        R.string.import_create
                                    },
                                    conflict.name,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = conflict.importedUuid in merged,
                                onCheckedChange = {
                                    merged = if (it) merged + conflict.importedUuid else merged - conflict.importedUuid
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ImportOptions(mergePlayerUuids = merged)) },
                enabled = preview.newGames > 0,
            ) { Text(stringResource(R.string.import_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
