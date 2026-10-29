package com.skullking.score.ui.components

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skullking.score.R
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.undo.UndoAction
import com.skullking.score.domain.undo.UndoKind
import com.skullking.score.domain.undo.UndoState
import java.util.WeakHashMap
import kotlinx.coroutines.flow.Flow

/**
 * Standard screen: top bar, content, and at most one main action button at the bottom,
 * within thumb reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkullScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    maxContentWidth: Dp = DEFAULT_CONTENT_WIDTH,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = { actions() },
            )
        },
        bottomBar = bottomBar,
        content = { padding ->
            if (maxContentWidth == Dp.Unspecified) {
                content(padding)
            } else {
                // On large screens the content stays centered and readable rather than stretched to the full width.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = maxContentWidth).fillMaxSize()) { content(padding) }
                }
            }
        },
    )
}

/** Maximum content width of the entry screens on tablets. */
val DEFAULT_CONTENT_WIDTH = 720.dp

/** Window width classes: phone in portrait, phone in landscape / small tablet, tablet. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840

@Composable
fun rememberWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < MEDIUM_WIDTH_DP -> WidthClass.COMPACT
        width < EXPANDED_WIDTH_DP -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

/** Bottom bar with the main action button (and an optional secondary button). */
@Composable
fun ActionBar(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondary: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = modifier, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            secondary?.invoke()
        }
    }
}

@Composable
fun PlayerAvatar(player: Player, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(player.color)),
        contentAlignment = Alignment.Center,
    ) {
        // The palette colors are light: the initial is always dark.
        Text(
            text = player.name.trim().firstOrNull()?.uppercase() ?: "?",
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1B),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PlayerLabel(player: Player, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(player)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Counter with big - / + buttons: usable with one hand without aiming. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..99,
    enabled: Boolean = true,
    /** Dense version for entry dialogs. */
    compact: Boolean = false,
) {
    val button = if (compact) 34.dp else 44.dp
    val valueWidth = if (compact) 30.dp else 44.dp
    // Compact: no 48 dp touch padding around the buttons, the lines stay close together.
    val touch = if (compact) Dp.Unspecified else LocalMinimumInteractiveComponentSize.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides touch) {
        StepperRow(value, onValueChange, modifier, range, enabled, compact, button, valueWidth)
    }
}

@Suppress("LongParameterList")
@Composable
private fun StepperRow(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier,
    range: IntRange,
    enabled: Boolean,
    compact: Boolean,
    button: Dp,
    valueWidth: Dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
            enabled = enabled && value > range.first,
            modifier = Modifier.size(button),
        ) {
            Text("−", fontSize = if (compact) 18.sp else 22.sp)
        }
        Text(
            text = value.toString(),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp).width(valueWidth),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
            enabled = enabled && value < range.last,
            modifier = Modifier.size(button),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

/**
 * Selectable choice denser than a chip (36 dp high, no 48 dp touch padding): for screens and dialogs with many
 * options, such as bids or trick entry.
 */
@Composable
fun CompactChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            selected = selected,
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = modifier.height(36.dp).widthIn(min = 36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                leading?.invoke()
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun WarningCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

/** "+20" / "-10" format for points. */
fun formatPoints(value: Int): String = if (value > 0) "+$value" else value.toString()

/** "Keep the screen on" setting; provided at the application root. */
val LocalKeepScreenOnSetting = compositionLocalOf { true }

/**
 * Screens asking to keep the screen on, per view. During a navigation transition the incoming screen
 * starts before the outgoing one is disposed: a plain on / off flag would be switched off by the
 * outgoing screen right after the incoming one switched it on.
 */
private val keepScreenOnRequests = WeakHashMap<View, Int>()

/** Keeps the screen on while this screen is displayed (if the setting is enabled). */
@Composable
fun KeepScreenOn() {
    val enabled = LocalKeepScreenOnSetting.current
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (enabled) {
            keepScreenOnRequests[view] = (keepScreenOnRequests[view] ?: 0) + 1
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                val remaining = (keepScreenOnRequests[view] ?: 1) - 1
                if (remaining > 0) keepScreenOnRequests[view] = remaining else keepScreenOnRequests.remove(view)
                view.keepScreenOn = remaining > 0
            }
        }
    }
}

/** Undo / redo in the top bar. */
@Composable
fun UndoRedoActions(state: UndoState, onUndo: () -> Unit, onRedo: () -> Unit) {
    IconButton(onClick = onUndo, enabled = state.canUndo) {
        Text("↶", style = MaterialTheme.typography.titleLarge)
    }
    IconButton(onClick = onRedo, enabled = state.canRedo) {
        Text("↷", style = MaterialTheme.typography.titleLarge)
    }
}

data class UndoNotice(val action: UndoAction, val redone: Boolean)

/** Dealer of the round; a tap lets the user pick another one. */
@Composable
fun DealerLine(
    game: Game,
    roundNumber: Int,
    onChoose: (Player) -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val dealer = game.dealerFor(roundNumber) ?: return
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { open = true }) {
            Text(
                "🃏 " + stringResource(R.string.dealer_label, dealer.name),
                style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                fontWeight = if (prominent) FontWeight.Bold else null,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            game.players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    onClick = {
                        open = false
                        onChoose(player)
                    },
                )
            }
        }
    }
}

/** Shows in the snackbar the message of each undo / redo. */
@Composable
fun UndoNotices(notices: Flow<UndoNotice>, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(notices) {
        notices.collect { notice ->
            val what = resources.getString(notice.action.kind.labelRes(), notice.action.detail)
            val text = resources.getString(if (notice.redone) R.string.redone_prefix else R.string.undone_prefix, what)
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
}

private fun UndoKind.labelRes(): Int = when (this) {
    UndoKind.BID -> R.string.undo_bid
    UndoKind.CANNONBALL -> R.string.undo_cannonball
    UndoKind.TRICK -> R.string.undo_trick
    UndoKind.TRICK_DELETED -> R.string.undo_trick_deleted
}
