package com.skullking.score.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Une courbe : une valeur par abscisse (manche ou partie). */
data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
    /** Tracé en pointillés, sans points : pour une moyenne ou une référence. */
    val dashed: Boolean = false,
)

private const val GRID_LINES = 4
private const val DASH_ON = 8f
private const val DASH_OFF = 6f
private const val LABEL_WIDTH_DP = 44f

/**
 * Courbes dessinées en Compose, sans bibliothèque externe. Un appui sélectionne l'abscisse la plus proche ;
 * [description] résume la tendance pour TalkBack.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    description: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val count = series.maxOfOrNull { it.values.size } ?: 0
    val all = series.flatMap { it.values }
    val low = minOf(all.minOrNull() ?: 0f, 0f)
    val high = maxOf(all.maxOrNull() ?: 0f, 0f).let { if (it == low) low + 1f else it }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description }
            .pointerInput(count) {
                detectTapGestures { tap ->
                    if (count > 0) {
                        val left = LABEL_WIDTH_DP.dp.toPx()
                        val step = if (count > 1) (size.width - left) / (count - 1) else 0f
                        val index = if (step == 0f) 0 else ((tap.x - left) / step).roundToInt()
                        onSelect(index.coerceIn(0, count - 1))
                    }
                }
            },
    ) {
        val left = LABEL_WIDTH_DP.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val width = size.width - left
        fun x(index: Int) = left + if (count > 1) width * index / (count - 1) else width / 2
        fun y(value: Float) = bottom - (value - low) / (high - low) * (bottom - top)

        for (i in 0..GRID_LINES) {
            val value = low + (high - low) * i / GRID_LINES
            val yy = y(value)
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(value.roundToInt().toString(), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2f))
        }
        drawLine(axis, Offset(left, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.5.dp.toPx())

        selectedIndex?.takeIf { it in 0 until count }?.let { index ->
            drawLine(axis, Offset(x(index), top), Offset(x(index), bottom), strokeWidth = 1.dp.toPx())
        }

        series.forEach { s ->
            val path = Path()
            s.values.forEachIndexed { index, value ->
                if (index == 0) path.moveTo(x(index), y(value)) else path.lineTo(x(index), y(value))
            }
            val stroke = if (s.dashed) {
                Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(DASH_ON, DASH_OFF).map {
                            it.dp.toPx()
                        }.toFloatArray(),
                    ),
                )
            } else {
                Stroke(width = 2.5.dp.toPx())
            }
            drawPath(path, s.color, style = stroke)
            if (s.dashed) return@forEach
            s.values.forEachIndexed { index, value ->
                val selected = index == selectedIndex
                drawCircle(
                    s.color,
                    radius = if (selected) 5.dp.toPx() else 3.dp.toPx(),
                    center = Offset(x(index), y(value)),
                )
            }
        }
        // Léger rappel du nombre d'abscisses, au cas où une série est vide.
        if (count == 0) drawRect(grid, Offset(left, top), Size(width, bottom - top), style = Stroke(1.dp.toPx()))
    }
}

/** Texte d'une abscisse : valeurs de chaque série à cet index, pour l'affichage sous le graphique. */
fun seriesAt(series: List<ChartSeries>, index: Int): List<Pair<ChartSeries, Float>> =
    series.mapNotNull { s -> s.values.getOrNull(index)?.let { s to it } }.sortedByDescending { it.second }
