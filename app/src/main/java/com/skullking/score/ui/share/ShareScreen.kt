package com.skullking.score.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skullking.score.R
import com.skullking.score.data.export.FileGateway
import com.skullking.score.domain.model.ScoredGame
import com.skullking.score.domain.repository.GameRepository
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.ui.components.ActionBar
import com.skullking.score.ui.components.SkullScaffold
import com.skullking.score.ui.labelRes
import com.skullking.score.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PNG_QUALITY = 100

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    games: GameRepository,
    private val files: FileGateway,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val gameId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GAME_ID))
    private val _options = MutableStateFlow(ShareOptions())
    val options: StateFlow<ShareOptions> = _options

    private val scored: StateFlow<ScoredGame?> = games.observeGame(gameId)
        .filterNotNull()
        .map { GameScoreCalculator.calculate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Image rendue ; recalculée hors du thread principal à chaque changement de contenu. */
    val image: StateFlow<Bitmap?> = combine(scored.filterNotNull(), _options) { game, options -> game to options }
        .mapLatest { (game, options) ->
            if (options.isEmpty) null else ScoreImageRenderer.render(game, options, texts(game))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<ShareEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun toggle(transform: (ShareOptions) -> ShareOptions) = _options.update(transform)

    fun send() {
        val bitmap = image.value ?: return
        viewModelScope.launch {
            val uri = files.writeShareableBytes(fileName(), bitmap.toPng())
            _events.send(ShareEvent.Send(uri))
        }
    }

    fun save() {
        val bitmap = image.value ?: return
        viewModelScope.launch {
            val ok = files.saveImageToGallery(fileName(), bitmap.toPng())
            _events.send(if (ok) ShareEvent.Saved else ShareEvent.SaveFailed)
        }
    }

    private fun fileName() = "skullking-${scored.value?.game?.startedAt ?: 0L}.png"

    private suspend fun Bitmap.toPng(): ByteArray = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }.toByteArray()
    }

    private fun texts(scored: ScoredGame): ShareTexts {
        val game = scored.game
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.startedAt))
        val expansion = context.getString(
            if (game.expansionCards.isEmpty()) R.string.share_without_expansion else R.string.share_with_expansion,
        )
        return ShareTexts(
            appName = context.getString(R.string.app_name),
            subtitle = "$date · ${context.getString(game.mode.labelRes())} · $expansion",
            podiumTitle = context.getString(R.string.share_podium),
            tableTitle = context.getString(R.string.share_table),
            curveTitle = context.getString(R.string.share_curve),
            statsTitle = context.getString(R.string.share_stats),
            roundHeader = context.getString(R.string.share_round),
            totalLabel = context.getString(R.string.table_total),
            statsLines = statsLines(scored),
        )
    }

    private fun statsLines(scored: ScoredGame): List<String> {
        val rounds = scored.rounds.filter { it.isScored }
        if (rounds.isEmpty()) return emptyList()
        val names = scored.game.players.associate { it.id to it.name }
        val all = rounds.flatMap { round ->
            round.breakdowns.map { (playerId, breakdown) ->
                Triple(names[playerId].orEmpty(), breakdown, round.round.number)
            }
        }
        val best = all.maxBy { it.second.total }
        val worst = all.minBy { it.second.total }
        val lines = mutableListOf(
            context.getString(R.string.share_best_round, best.first, best.second.total, best.third),
            context.getString(R.string.share_worst_round, worst.first, worst.second.total, worst.third),
        )
        scored.game.players.forEach { player ->
            val exact = rounds.count { it.breakdowns[player.id]?.success == true }
            lines += context.getString(R.string.share_exact_bids, player.name, exact, rounds.size)
        }
        return lines
    }
}

sealed interface ShareEvent {
    data class Send(val uri: android.net.Uri) : ShareEvent
    data object Saved : ShareEvent
    data object SaveFailed : ShareEvent
}

@Composable
fun ShareScreen(onBack: () -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShareEvent.Send -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, resources.getString(R.string.share_action)))
                }

                ShareEvent.Saved -> snackbar.showSnackbar(resources.getString(R.string.share_saved))

                ShareEvent.SaveFailed -> snackbar.showSnackbar(resources.getString(R.string.share_save_failed))
            }
        }
    }

    SkullScaffold(
        title = stringResource(R.string.share_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ActionBar(
                primaryText = stringResource(R.string.share_action),
                primaryEnabled = image != null,
                onPrimary = viewModel::send,
                secondary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    {
                        OutlinedButton(
                            onClick = viewModel::save,
                            enabled = image != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.share_save)) }
                    }
                } else {
                    null
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = options.podium,
                    onClick = { viewModel.toggle { it.copy(podium = !it.podium) } },
                    label = { Text(stringResource(R.string.share_podium)) },
                )
                FilterChip(
                    selected = options.table,
                    onClick = { viewModel.toggle { it.copy(table = !it.table) } },
                    label = { Text(stringResource(R.string.share_table)) },
                )
                FilterChip(
                    selected = options.curve,
                    onClick = { viewModel.toggle { it.copy(curve = !it.curve) } },
                    label = { Text(stringResource(R.string.share_curve)) },
                )
                FilterChip(
                    selected = options.stats,
                    onClick = { viewModel.toggle { it.copy(stats = !it.stats) } },
                    label = { Text(stringResource(R.string.share_stats)) },
                )
            }
            val bitmap = image
            if (bitmap == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (options.isEmpty) {
                        Text(stringResource(R.string.share_nothing))
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}
