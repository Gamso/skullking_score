package com.skullking.score.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.ScoredGame

data class ShareOptions(
    val podium: Boolean = true,
    val table: Boolean = true,
    val curve: Boolean = false,
    val stats: Boolean = false,
) {
    val isEmpty: Boolean get() = !podium && !table && !curve && !stats
}

/** Textes déjà traduits : le rendu ne dépend pas des ressources Android. */
data class ShareTexts(
    val appName: String,
    val subtitle: String,
    val podiumTitle: String,
    val tableTitle: String,
    val curveTitle: String,
    val statsTitle: String,
    val roundHeader: String,
    val totalLabel: String,
    val statsLines: List<String>,
)

/**
 * Dessine le résultat d'une partie en image PNG, hors écran : largeur fixe de 1080 px, thème clair, indépendant
 * de la taille et de l'orientation de l'écran.
 */
object ScoreImageRenderer {
    const val WIDTH = 1080

    internal const val MARGIN = 48f
    internal const val BACKGROUND = 0xFFFAF6EC.toInt()
    internal const val INK = 0xFF12263A.toInt()
    internal const val MUTED = 0xFF5B6B7B.toInt()
    internal const val PRIMARY = 0xFF1F6F78.toInt()
    private const val GOLD = 0xFFC9A44A.toInt()
    internal const val CARD = 0xFFEFE6D2.toInt()
    private const val STRIPE = 0xFFF1E9D8.toInt()
    private const val NEGATIVE = 0xFFB3261E.toInt()
    private const val AVATAR_INK = 0xFF1B1B1B.toInt()
    private const val PODIUM_ROW = 104f
    private const val ROUND_ROW = 84f
    private const val MEDALS = 3
    internal const val HEADER_HEIGHT = 150f
    internal const val SECTION_TITLE = 64f
    internal const val SECTION_GAP = 40f
    private const val GAP = 14f
    private const val TABLE_HEADER = 100f
    private const val TOTAL_ROW = 84f
    private const val STAT_ROW = 52f
    internal const val FOOTER_HEIGHT = 70f
    private const val CURVE_HEIGHT = 520f
    private const val LEGEND_ROW = 48f
    private const val GRID_LINES = 4
    private const val CHART_LABEL_WIDTH = 90f
    private const val CHART_DARKEN = 0.65f
    private const val LEGEND_DOT = 11f
    private const val LEGEND_GAP = 34f

    internal fun paint(size: Float, color: Int = INK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color = color
            this.textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    fun render(scored: ScoredGame, options: ShareOptions, texts: ShareTexts): Bitmap {
        val players = scored.game.players
        val scoredRounds = scored.rounds.count { it.isScored }
        val podiumHeight = if (options.podium) SECTION_TITLE + players.size * (PODIUM_ROW + GAP) + SECTION_GAP else 0f
        val tableHeight = if (options.table) {
            SECTION_TITLE + TABLE_HEADER + TOTAL_ROW + scoredRounds * ROUND_ROW + SECTION_GAP
        } else {
            0f
        }
        val curveHeight = if (options.curve) {
            SECTION_TITLE + CURVE_HEIGHT + legendRows(players, WIDTH - 2 * MARGIN).size * LEGEND_ROW + SECTION_GAP
        } else {
            0f
        }
        val statsHeight = if (options.stats) SECTION_TITLE + texts.statsLines.size * STAT_ROW + SECTION_GAP else 0f
        val height = MARGIN * 2 + HEADER_HEIGHT + podiumHeight + tableHeight + curveHeight + statsHeight + FOOTER_HEIGHT
        val bitmap = Bitmap.createBitmap(WIDTH, height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        var y = MARGIN
        y = drawHeader(canvas, texts.appName, texts.subtitle, y)
        if (options.podium) y = drawPodium(canvas, scored, texts, y)
        if (options.table) y = drawTable(canvas, scored, texts, y)
        if (options.curve) y = drawCurve(canvas, scored, texts, y)
        if (options.stats) y = drawStats(canvas, texts, y)
        canvas.drawText(
            texts.appName,
            WIDTH / 2f,
            y + FOOTER_HEIGHT - 28f,
            paint(28f, MUTED, align = Paint.Align.CENTER),
        )
        return bitmap
    }

    internal fun drawHeader(canvas: Canvas, appName: String, subtitle: String, top: Float): Float {
        canvas.drawText("🏴‍☠️ $appName", MARGIN, top + 58f, paint(56f, INK, bold = true))
        canvas.drawText(subtitle, MARGIN, top + 112f, paint(34f, MUTED))
        return top + HEADER_HEIGHT
    }

    internal fun drawSectionTitle(canvas: Canvas, title: String, top: Float): Float {
        canvas.drawText(title, MARGIN, top + 40f, paint(38f, PRIMARY, bold = true))
        return top + SECTION_TITLE
    }

    internal fun drawAvatar(canvas: Canvas, player: Player, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = player.color.toInt() })
        val initial = player.name.trim().firstOrNull()?.uppercase() ?: "?"
        canvas.drawText(initial, cx, cy + radius * 0.36f, paint(radius * 1.05f, AVATAR_INK, true, Paint.Align.CENTER))
    }

    private fun drawPodium(canvas: Canvas, scored: ScoredGame, texts: ShareTexts, top: Float): Float {
        var y = drawSectionTitle(canvas, texts.podiumTitle, top)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        scored.ranking.forEach { entry ->
            fill.color = if (entry.rank == 1) GOLD else CARD
            canvas.drawRoundRect(RectF(MARGIN, y, WIDTH - MARGIN, y + PODIUM_ROW), 28f, 28f, fill)
            val medal = when (entry.rank) {
                1 -> "🥇"
                2 -> "🥈"
                MEDALS -> "🥉"
                else -> "${entry.rank}."
            }
            val cy = y + PODIUM_ROW / 2
            canvas.drawText(medal, MARGIN + 28f, cy + 16f, paint(48f))
            drawAvatar(canvas, entry.player, MARGIN + 170f, cy, 34f)
            canvas.drawText(entry.player.name, MARGIN + 230f, cy + 15f, paint(44f, INK, bold = entry.rank == 1))
            canvas.drawText(
                entry.total.toString(),
                WIDTH - MARGIN - 32f,
                cy + 18f,
                paint(54f, if (entry.total < 0) NEGATIVE else INK, bold = true, align = Paint.Align.RIGHT),
            )
            y += PODIUM_ROW + GAP
        }
        return y + SECTION_GAP
    }

    private fun drawTable(canvas: Canvas, scored: ScoredGame, texts: ShareTexts, top: Float): Float {
        var y = drawSectionTitle(canvas, texts.tableTitle, top)
        val players = scored.game.players
        val roundCol = 140f
        val colWidth = (WIDTH - 2 * MARGIN - roundCol) / players.size
        fun columnCenter(index: Int) = MARGIN + roundCol + colWidth * index + colWidth / 2

        val nameSize = if (players.size > 6) 22f else 26f
        players.forEachIndexed { index, player ->
            drawAvatar(canvas, player, columnCenter(index), y + 28f, 24f)
            canvas.drawText(
                fit(player.name, nameSize, colWidth - 8f),
                columnCenter(index),
                y + 82f,
                paint(nameSize, MUTED, align = Paint.Align.CENTER),
            )
        }
        canvas.drawText(texts.roundHeader, MARGIN + 8f, y + 62f, paint(26f, MUTED))
        y += TABLE_HEADER

        canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + TOTAL_ROW, Paint().apply { color = CARD })
        canvas.drawText(texts.totalLabel, MARGIN + 8f, y + 56f, paint(32f, INK, bold = true))
        val best = scored.totals.values.maxOrNull()
        players.forEachIndexed { index, player ->
            val total = scored.totals[player.id] ?: 0
            canvas.drawText(
                total.toString(),
                columnCenter(index),
                y + 58f,
                paint(
                    if (players.size > 7) 36f else 42f,
                    if (total == best) PRIMARY else INK,
                    bold = true,
                    align = Paint.Align.CENTER,
                ),
            )
        }
        y += TOTAL_ROW

        val stripe = Paint().apply { color = STRIPE }
        scored.rounds.filter { it.isScored }.forEachIndexed { rowIndex, round ->
            if (rowIndex % 2 == 1) canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + ROUND_ROW, stripe)
            canvas.drawText(round.round.number.toString(), MARGIN + 8f, y + 40f, paint(34f, INK, bold = true))
            canvas.drawText(
                "${round.round.cardsDealt} c." + if (round.round.isExtra) " +" else "",
                MARGIN + 8f,
                y + 70f,
                paint(22f, MUTED),
            )
            players.forEachIndexed { index, player ->
                val breakdown = round.breakdowns[player.id] ?: return@forEachIndexed
                val points = (if (breakdown.total > 0) "+" else "") + breakdown.total
                canvas.drawText(
                    points,
                    columnCenter(index),
                    y + 40f,
                    paint(32f, if (breakdown.success) INK else NEGATIVE, bold = true, align = Paint.Align.CENTER),
                )
                canvas.drawText(
                    (round.cumulative[player.id] ?: 0).toString(),
                    columnCenter(index),
                    y + 70f,
                    paint(22f, MUTED, align = Paint.Align.CENTER),
                )
            }
            y += ROUND_ROW
        }
        return y + SECTION_GAP
    }

    /** Couleur de joueur assombrie : les pastilles sont claires et se liraient mal en trait sur fond clair. */
    private fun lineColor(player: Player): Int {
        val argb = player.color.toInt()
        val r = ((argb shr 16 and 0xFF) * CHART_DARKEN).toInt()
        val g = ((argb shr 8 and 0xFF) * CHART_DARKEN).toInt()
        val b = ((argb and 0xFF) * CHART_DARKEN).toInt()
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    /** Légende répartie sur plusieurs lignes pour tenir dans [maxWidth]. */
    private fun legendRows(players: List<Player>, maxWidth: Float): List<List<Player>> {
        val p = paint(28f)
        val rows = mutableListOf<MutableList<Player>>()
        var used = 0f
        players.forEach { player ->
            val width = LEGEND_DOT * 2 + 14f + p.measureText(player.name) + LEGEND_GAP
            if (rows.isEmpty() || used + width > maxWidth) {
                rows += mutableListOf<Player>()
                used = 0f
            }
            rows.last() += player
            used += width
        }
        return rows
    }

    private fun drawLegend(canvas: Canvas, players: List<Player>, top: Float): Float {
        var y = top
        val text = paint(28f)
        legendRows(players, WIDTH - 2 * MARGIN).forEach { row ->
            var x = MARGIN
            row.forEach { player ->
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor(player) }
                canvas.drawCircle(x + LEGEND_DOT, y + 26f, LEGEND_DOT, dot)
                canvas.drawText(player.name, x + LEGEND_DOT * 2 + 14f, y + 36f, text)
                x += LEGEND_DOT * 2 + 14f + text.measureText(player.name) + LEGEND_GAP
            }
            y += LEGEND_ROW
        }
        return y
    }

    private fun drawCurve(canvas: Canvas, scored: ScoredGame, texts: ShareTexts, top: Float): Float {
        var y = drawSectionTitle(canvas, texts.curveTitle, top)
        val rounds = scored.rounds.filter { it.isScored }
        val players = scored.game.players
        val left = MARGIN + CHART_LABEL_WIDTH
        val right = WIDTH - MARGIN
        val chartTop = y + 12f
        val chartBottom = y + CURVE_HEIGHT - 12f
        val values = players.flatMap { player -> rounds.map { (it.cumulative[player.id] ?: 0).toFloat() } }
        val low = minOf(values.minOrNull() ?: 0f, 0f)
        val high = (values.maxOrNull() ?: 0f).coerceAtLeast(0f).let { if (it == low) low + 1f else it }
        fun px(index: Int) = if (rounds.size >
            1
        ) {
            left + (right - left) * index / (rounds.size - 1)
        } else {
            (left + right) / 2
        }
        fun py(value: Float) = chartBottom - (value - low) / (high - low) * (chartBottom - chartTop)

        val grid = Paint().apply {
            color = CARD
            strokeWidth = 2f
        }
        val label = paint(24f, MUTED, align = Paint.Align.RIGHT)
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
                color = MUTED
                strokeWidth = 3f
            },
        )
        players.forEach { player ->
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor(player)
                strokeWidth = 5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor(player) }
            val path = android.graphics.Path()
            rounds.forEachIndexed { index, round ->
                val value = (round.cumulative[player.id] ?: 0).toFloat()
                if (index == 0) path.moveTo(px(index), py(value)) else path.lineTo(px(index), py(value))
            }
            canvas.drawPath(path, stroke)
            rounds.forEachIndexed { index, round ->
                canvas.drawCircle(px(index), py((round.cumulative[player.id] ?: 0).toFloat()), 6f, dot)
            }
        }
        y = drawLegend(canvas, players, y + CURVE_HEIGHT)
        return y + SECTION_GAP
    }

    private fun drawStats(canvas: Canvas, texts: ShareTexts, top: Float): Float {
        var y = drawSectionTitle(canvas, texts.statsTitle, top)
        texts.statsLines.forEach { line ->
            canvas.drawText(fit(line, 32f, WIDTH - 2 * MARGIN), MARGIN, y + 36f, paint(32f, INK))
            y += STAT_ROW
        }
        return y + SECTION_GAP
    }

    /** Raccourcit [text] avec « … » pour qu'il tienne dans [maxWidth]. */
    internal fun fit(text: String, size: Float, maxWidth: Float): String {
        val p = paint(size)
        if (p.measureText(text) <= maxWidth) return text
        var cut = text.length
        while (cut > 1 && p.measureText(text.substring(0, cut) + "…") > maxWidth) cut--
        return text.substring(0, cut) + "…"
    }
}
