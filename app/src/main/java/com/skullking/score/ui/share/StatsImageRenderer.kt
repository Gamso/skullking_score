package com.skullking.score.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.skullking.score.domain.model.Player

/** Already translated texts of a statistics image: the rendering does not depend on Android resources. */
data class StatsImageTexts(
    val appName: String,
    val subtitle: String,
    val playerLine: String,
    val summary: List<Pair<String, String>>,
    val recordsTitle: String,
    val records: List<Pair<String, String>>,
    val sizeTitle: String,
    val sizes: List<Pair<String, String>>,
    val trendTitle: String,
    val trendLegend: Pair<String, String>,
)

/** Data of the trend chart: score of each game and rolling average. `null` if there are too few games. */
data class StatsTrend(val totals: List<Double>, val rolling: List<Double>)

/** Statistics image of a player: same look and same fixed width as the game image. */
object StatsImageRenderer {
    private const val ROW = 50f
    private const val ROW_GAP = 24f
    private const val PLAYER_BAND = 150f
    private const val CHART_HEIGHT = 440f
    private const val LEGEND_HEIGHT = 56f
    private const val GRID_LINES = 4
    private const val LABEL_WIDTH = 90f
    private const val DASH_ON = 18f
    private const val DASH_OFF = 12f

    private fun sectionHeight(rows: Int) =
        ScoreImageRenderer.SECTION_TITLE + rows * ROW + ScoreImageRenderer.SECTION_GAP

    fun render(player: Player, trend: StatsTrend?, texts: StatsImageTexts): Bitmap {
        val chartHeight = if (trend == null) {
            0f
        } else {
            ScoreImageRenderer.SECTION_TITLE + CHART_HEIGHT + LEGEND_HEIGHT + ScoreImageRenderer.SECTION_GAP
        }
        val height = ScoreImageRenderer.MARGIN * 2 + ScoreImageRenderer.HEADER_HEIGHT + PLAYER_BAND +
            texts.summary.size * ROW + ScoreImageRenderer.SECTION_GAP +
            sectionHeight(texts.records.size) + chartHeight + sectionHeight(texts.sizes.size) +
            ScoreImageRenderer.FOOTER_HEIGHT
        val bitmap = Bitmap.createBitmap(ScoreImageRenderer.WIDTH, height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ScoreImageRenderer.BACKGROUND)
        var y = ScoreImageRenderer.drawHeader(canvas, texts.appName, texts.subtitle, ScoreImageRenderer.MARGIN)
        y = drawPlayer(canvas, player, texts.playerLine, y)
        y = drawRows(canvas, texts.summary, y) + ScoreImageRenderer.SECTION_GAP
        y = drawSection(canvas, texts.recordsTitle, texts.records, y)
        if (trend != null) y = drawTrend(canvas, trend, texts, y)
        y = drawSection(canvas, texts.sizeTitle, texts.sizes, y)
        canvas.drawText(
            texts.appName,
            ScoreImageRenderer.WIDTH / 2f,
            y + ScoreImageRenderer.FOOTER_HEIGHT - 28f,
            ScoreImageRenderer.paint(28f, ScoreImageRenderer.MUTED, align = Paint.Align.CENTER),
        )
        return bitmap
    }

    private fun drawPlayer(canvas: Canvas, player: Player, line: String, top: Float): Float {
        val margin = ScoreImageRenderer.MARGIN
        ScoreImageRenderer.drawAvatar(canvas, player, margin + 52f, top + 56f, 52f)
        canvas.drawText(
            ScoreImageRenderer.fit(player.name, 56f, ScoreImageRenderer.WIDTH - 2 * margin - 140f),
            margin + 130f,
            top + 52f,
            ScoreImageRenderer.paint(56f, ScoreImageRenderer.INK, bold = true),
        )
        canvas.drawText(line, margin + 130f, top + 98f, ScoreImageRenderer.paint(32f, ScoreImageRenderer.MUTED))
        return top + PLAYER_BAND
    }

    private fun drawRows(canvas: Canvas, rows: List<Pair<String, String>>, top: Float): Float {
        var y = top
        val margin = ScoreImageRenderer.MARGIN
        val label = ScoreImageRenderer.paint(32f)
        val value = ScoreImageRenderer.paint(32f, bold = true, align = Paint.Align.RIGHT)
        val valueMax = ScoreImageRenderer.WIDTH / 2f
        rows.forEach { (name, text) ->
            // The label takes the room the value leaves it, so it is only truncated when really necessary.
            val shownValue = ScoreImageRenderer.fit(text, 32f, valueMax)
            val labelMax = ScoreImageRenderer.WIDTH - 2 * margin - value.measureText(shownValue) - ROW_GAP
            canvas.drawText(ScoreImageRenderer.fit(name, 32f, labelMax), margin, y + 34f, label)
            canvas.drawText(shownValue, ScoreImageRenderer.WIDTH - margin, y + 34f, value)
            y += ROW
        }
        return y
    }

    private fun drawSection(canvas: Canvas, title: String, rows: List<Pair<String, String>>, top: Float): Float {
        val y = ScoreImageRenderer.drawSectionTitle(canvas, title, top)
        return drawRows(canvas, rows, y) + ScoreImageRenderer.SECTION_GAP
    }

    private fun drawTrend(canvas: Canvas, trend: StatsTrend, texts: StatsImageTexts, top: Float): Float {
        val y = ScoreImageRenderer.drawSectionTitle(canvas, texts.trendTitle, top)
        val margin = ScoreImageRenderer.MARGIN
        val left = margin + LABEL_WIDTH
        val right = ScoreImageRenderer.WIDTH - margin
        val chartTop = y + 12f
        val chartBottom = y + CHART_HEIGHT - 12f
        val values = trend.totals.map { it.toFloat() } + trend.rolling.map { it.toFloat() }
        val low = minOf(values.min(), 0f)
        val high = maxOf(values.max(), 0f).let { if (it == low) low + 1f else it }
        val count = trend.totals.size
        fun px(index: Int) = if (count > 1) left + (right - left) * index / (count - 1) else (left + right) / 2
        fun py(value: Float) = chartBottom - (value - low) / (high - low) * (chartBottom - chartTop)

        val grid = Paint().apply {
            color = ScoreImageRenderer.CARD
            strokeWidth = 2f
        }
        val label = ScoreImageRenderer.paint(24f, ScoreImageRenderer.MUTED, align = Paint.Align.RIGHT)
        for (i in 0..GRID_LINES) {
            val value = low + (high - low) * i / GRID_LINES
            canvas.drawLine(left, py(value), right, py(value), grid)
            canvas.drawText(value.toInt().toString(), left - 12f, py(value) + 8f, label)
        }
        canvas.drawLine(
            left,
            py(0f),
            right,
            py(0f),
            Paint().apply {
                color = ScoreImageRenderer.MUTED
                strokeWidth = 3f
            },
        )
        drawLine(canvas, trend.totals.map { it.toFloat() }, ScoreImageRenderer.PRIMARY, dashed = false, ::px, ::py)
        drawLine(canvas, trend.rolling.map { it.toFloat() }, ScoreImageRenderer.INK, dashed = true, ::px, ::py)

        val legendY = y + CHART_HEIGHT
        val text = ScoreImageRenderer.paint(28f)
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ScoreImageRenderer.PRIMARY
            strokeWidth = 6f
        }
        canvas.drawLine(margin, legendY + 26f, margin + 60f, legendY + 26f, solid)
        canvas.drawText(texts.trendLegend.first, margin + 76f, legendY + 36f, text)
        val dashX = margin + 76f + text.measureText(texts.trendLegend.first) + 40f
        val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ScoreImageRenderer.INK
            strokeWidth = 6f
            pathEffect = DashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)
        }
        canvas.drawLine(dashX, legendY + 26f, dashX + 60f, legendY + 26f, dashed)
        canvas.drawText(texts.trendLegend.second, dashX + 76f, legendY + 36f, text)
        return legendY + LEGEND_HEIGHT + ScoreImageRenderer.SECTION_GAP
    }

    private fun drawLine(
        canvas: Canvas,
        values: List<Float>,
        color: Int,
        dashed: Boolean,
        x: (Int) -> Float,
        y: (Float) -> Float,
    ) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)
        }
        val path = Path()
        values.forEachIndexed { index, value ->
            if (index == 0) path.moveTo(x(index), y(value)) else path.lineTo(x(index), y(value))
        }
        canvas.drawPath(path, stroke)
        if (!dashed) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            values.forEachIndexed { index, value -> canvas.drawCircle(x(index), y(value), 6f, dot) }
        }
    }
}
