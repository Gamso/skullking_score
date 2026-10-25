package com.skullking.score.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.flow.Flow

/**
 * Écran standard : barre du haut, contenu, et au plus un bouton d'action principal en bas,
 * à portée de pouce.
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
                // Sur grand écran, le contenu reste centré et lisible plutôt que tiré sur toute la largeur.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = maxContentWidth).fillMaxSize()) { content(padding) }
                }
            }
        },
    )
}

/** Largeur maximale du contenu des écrans de saisie sur tablette. */
val DEFAULT_CONTENT_WIDTH = 720.dp

/** Classes de largeur de fenêtre : téléphone portrait, téléphone paysage / petite tablette, tablette. */
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

/** Barre basse avec le bouton d'action principal (et un bouton secondaire optionnel). */
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
        // Les couleurs de la palette sont claires : l'initiale est toujours en sombre.
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

/** Compteur avec gros boutons − / + : se manipule d'une main sans viser. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..99,
    enabled: Boolean = true,
    /** Version dense pour les fenêtres de saisie. */
    compact: Boolean = false,
) {
    val button = if (compact) 34.dp else 44.dp
    val valueWidth = if (compact) 30.dp else 44.dp
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

/** Sélecteur 0…max en une ligne de pastilles, pour les annonces. */
@Composable
fun NumberSelector(
    selected: Int,
    max: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((0..max).toList()) { n ->
            FilterChip(
                selected = n == selected,
                onClick = { onSelect(n) },
                enabled = enabled,
                label = {
                    Text(
                        n.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(width = 24.dp, height = 32.dp).padding(top = 2.dp),
                    )
                },
            )
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

/** Format « +20 » / « -10 » pour les points. */
fun formatPoints(value: Int): String = if (value > 0) "+$value" else value.toString()

/** Réglage « garder l'écran allumé » ; fourni à la racine de l'application. */
val LocalKeepScreenOnSetting = compositionLocalOf { true }

/** Maintient l'écran allumé tant que cet écran est affiché (si le réglage est actif). */
@Composable
fun KeepScreenOn() {
    val enabled = LocalKeepScreenOnSetting.current
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Annuler / rétablir dans la barre du haut. */
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

/** Donneur de la manche ; un appui permet d'en désigner un autre. */
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

/** Affiche dans la barre d'état le message de chaque annulation / rétablissement. */
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
