package com.yawmi.diabetes

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.*

data class WidgetLook(
    val mode: String = "summary", val style: String = "auto", val visible: Boolean = false,
    val parts: List<String> = listOf("glucose", "water", "medication"), val opacity: Int = 100,
    val font: String = "comfortable", val title: Boolean = true, val details: Boolean = true,
    val action: Boolean = true, val background: String = "", val accent: String = "",
    val waterVisual: String = "ring", val radius: Int = 24, val quickWater: Boolean = false,
    val undoWater: Boolean = false, val layout: String = "widget_focus", val actionTarget: String = "auto"
)

internal class WidgetContent(private val state: State, look: WidgetLook, now: Long = System.currentTimeMillis()) {
    val words = Words(state.preferences.language)
    val visible = Widgets.valuesVisible(state.preferences.lock, look.visible)
    private val stats by lazy { TrackingStats(state, now) }
    private val day by lazy { dayOverview(state, now) }
    private val next by lazy { Reminders.plan(state, now).firstOrNull { it.kind == "medication" } }
    val readings get() = if (visible) stats.glucoseReadings.take(14).asReversed().map(::mg) else emptyList()
    val hydration: Float? get() = state.preferences.let { p ->
        if (visible && p.waterGoal > 0 && p.waterViewDays == 1 && p.waterViewMetric == "total")
            (stats.todayWaterMl / p.waterGoal).toFloat()
        else null
    }
    fun metric(kind: String): MetricDisplay {
        val name = words[when (kind) {
            "day_summary" -> "day_overview"
            "medication" -> "next_reminder"
            else -> kind
        }]
        if (!visible) return MetricDisplay(name, "—", "", "", words["widget_values_off"])
        return when (kind) {
            "glucose" -> stats.glucose(words)
            "water" -> stats.water(words)
            "medication" -> next?.let { dose ->
                MetricDisplay(name, timeText(dose.due, words), "", dateText(dose.due, words),
                    state.medications.firstOrNull { it.id == dose.medicationId }?.name.orEmpty())
            } ?: MetricDisplay(name, "—", "", words["no_doses"], "")
            else -> MetricDisplay(name, number(day.activityCount.toDouble(), words), "",
                words.f("day_event_count", "count" to number(day.activityCount.toDouble(), words)),
                words.f("day_dose_count", "count" to number(day.takenDoses.toDouble(), words)))
        }
    }
}

object WidgetArt {
    private var fonts: Pair<Typeface, Typeface>? = null

    internal fun showsWaterGraphic(width: Float, height: Float) =
        height >= 113f && width >= 97f || width >= 143f && height >= 64f

    @Synchronized private fun fonts(context: Context): Pair<Typeface, Typeface> =
        fonts ?: (Typeface.createFromAsset(context.assets, "fonts/tajawal_regular.ttf") to
            Typeface.createFromAsset(context.assets, "fonts/tajawal_bold.ttf")).also { fonts = it }

    fun bitmap(c: Context, s: State, width: Int, height: Int, look: WidgetLook,
               darkSystem: Boolean = false, pixelScale: Float = 1.5f): Bitmap =
        bitmap(c, WidgetContent(s, look), width, height, look, darkSystem, pixelScale)

    internal fun bitmap(c: Context, content: WidgetContent, width: Int, height: Int, look: WidgetLook,
                        darkSystem: Boolean, pixelScale: Float): Bitmap {
        val scale = pixelScale.coerceIn(.25f, 3f).coerceAtMost(sqrt(1_150_000f / (width * height)))
        val bitmap = Bitmap.createBitmap((width * scale).roundToInt(), (height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / height)
        Painter(canvas, width.toFloat(), height.toFloat(), look, content, darkSystem, fonts(c)).draw()
        return bitmap
    }

    private class Painter(
        val canvas: Canvas, val width: Float, val height: Float, val look: WidgetLook,
        val content: WidgetContent, darkSystem: Boolean, val fonts: Pair<Typeface, Typeface>
    ) {
        val words = content.words
        val rtl = words.rtl
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val dark = look.style in listOf("widget_midnight", "forest") || look.style == "auto" && darkSystem
        val surface = parse(look.background, when (look.style) {
            "widget_midnight" -> 0xFF171D30.toInt()
            "forest" -> 0xFF142A26.toInt()
            "blue" -> 0xFFEAF3FC.toInt()
            "cream" -> 0xFFFBF3E5.toInt()
            "widget_rose" -> 0xFFF9EBEE.toInt()
            else -> if (dark) 0xFF18252C.toInt() else 0xFFF6F8FA.toInt()
        })
        val night = ColorUtils.calculateLuminance(surface) < .29
        val ink = contrast(surface)
        val secondary = ColorUtils.blendARGB(ink, surface, .24f)
        val seam = ColorUtils.blendARGB(surface, ink, if (night) .14f else .10f)
        val panel = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(surface, Color.WHITE, if (night) .055f else .67f), 255)
        val fontScale = when (look.font) { "compact" -> .89f; "large" -> 1.14f; else -> 1f }
        val geometry = WidgetLayout.calculate(width.toInt(), height.toInt(), look, rtl)

        private fun parse(value: String, fallback: Int) =
            runCatching { ColorUtils.setAlphaComponent(Color.parseColor(value), 255) }.getOrDefault(fallback)
        private fun contrast(color: Int): Int =
            if (ColorUtils.calculateContrast(Color.WHITE, color) >= 4.5) Color.WHITE else 0xFF101416.toInt()
                .takeIf { ColorUtils.calculateContrast(it, color) >= 4.5 } ?: Color.BLACK
        private fun tone(kind: String): Int = when (kind) {
            "water" -> if (night) 0xFF79CDFF.toInt() else 0xFF176EA4.toInt()
            "medication" -> if (night) 0xFFFFC17A.toInt() else 0xFF9B4C18.toInt()
            "day_summary" -> if (night) 0xFF85DEC2.toInt() else 0xFF18775E.toInt()
            else -> if (night) 0xFFF6AFBC.toInt() else 0xFFAB3859.toInt()
        }
        private fun box(rect: RectF, color: Int, radius: Float, stroke: Boolean = false) {
            if (rect.isEmpty) return
            paint.color = color
            paint.style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = 1f
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
        private fun line(x: Float, y: Float, x2: Float, y2: Float, color: Int, stroke: Float = 1f) {
            paint.color = color
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, y, x2, y2, paint)
        }
        private fun text(value: String, area: RectF, top: Float, size: Float, color: Int, bold: Boolean = false,
                         fit: Boolean = false, center: Boolean = false) {
            if (value.isBlank() || area.width() < 8) return
            type.color = color
            type.typeface = if (bold) fonts.second else fonts.first
            type.textSize = size * fontScale
            if (fit && type.measureText(value) > area.width()) {
                type.textSize *= (area.width() / type.measureText(value)).coerceAtLeast(.60f)
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, type, area.width().toInt())
                .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
            val x = when {
                center -> area.left
                rtl -> area.right - layout.getLineRight(0)
                else -> area.left - layout.getLineLeft(0)
            }
            canvas.save()
            canvas.translate(x, top)
            layout.draw(canvas)
            canvas.restore()
        }
        private fun glyph(kind: String, x: Float, y: Float, size: Float, color: Int) {
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(size / 24, size / 24)
            paint.color = color
            paint.strokeWidth = 1.65f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.STROKE
            val path = Path()
            fun mark(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
            when (kind) {
                "water" -> {
                    path.moveTo(12f, 2f)
                    path.cubicTo(9f, 7f, 5f, 10f, 5f, 15f)
                    path.cubicTo(5f, 24f, 19f, 24f, 19f, 15f)
                    path.cubicTo(19f, 10f, 15f, 7f, 12f, 2f)
                    canvas.drawPath(path, paint)
                }
                "glucose" -> { path.moveTo(2f, 13f); path.lineTo(7f, 13f); path.lineTo(10f, 5f)
                    path.lineTo(14f, 19f); path.lineTo(17f, 11f); path.lineTo(22f, 11f); canvas.drawPath(path, paint) }
                "medication" -> {
                    canvas.rotate(42f, 12f, 12f)
                    canvas.drawRoundRect(7f, 2f, 17f, 22f, 5f, 5f, paint)
                    mark(7f, 12f, 17f, 12f)
                }
                "settings" -> { mark(3f, 8f, 21f, 8f); mark(3f, 16f, 21f, 16f)
                    mark(9f, 5f, 9f, 11f); mark(15f, 13f, 15f, 19f) }
                "plus" -> { mark(5f, 12f, 19f, 12f); mark(12f, 5f, 12f, 19f) }
                "cycle" -> { mark(4f, 8f, 20f, 8f); mark(20f, 8f, 16f, 4f)
                    mark(4f, 16f, 20f, 16f); mark(4f, 16f, 8f, 20f) }
                "undo" -> { canvas.drawArc(4f, 7f, 21f, 20f, -142f, 240f, false, paint)
                    mark(4f, 9f, 4f, 3f); mark(4f, 9f, 10f, 9f) }
                "arrow" -> { val end = if (rtl) 5f else 19f; val bend = if (rtl) 11f else 13f
                    mark(5f, 12f, 19f, 12f); mark(end, 12f, bend, 6f); mark(end, 12f, bend, 18f) }
                else -> { canvas.drawRoundRect(4f, 4f, 20f, 20f, 4f, 4f, paint)
                    mark(8f, 10f, 16f, 10f); mark(8f, 15f, 13f, 15f) }
            }
            paint.style = Paint.Style.FILL
            canvas.restore()
        }

        private fun trace(a: RectF, color: Int): Boolean {
            val values = content.readings
            if (values.size < 2) return false
            val low = values.min()
            val range = (values.max() - low).coerceAtLeast(10.0)
            repeat(3) { index -> val y = a.top + a.height() * index / 2; line(a.left, y, a.right, y, seam) }
            val path = Path()
            val points = values.mapIndexed { i, value ->
                val x = a.left + a.width() * i / values.lastIndex
                val y = a.bottom - a.height() * (.12f + .76f * ((value - low) / range).toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                PointF(x, y)
            }
            val fill = Path(path).apply { lineTo(a.right, a.bottom); lineTo(a.left, a.bottom); close() }
            paint.shader = LinearGradient(0f, a.top, 0f, a.bottom, ColorUtils.setAlphaComponent(color, 55),
                ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP)
            canvas.drawPath(fill, paint)
            paint.shader = null
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            points.last().let { canvas.drawCircle(it.x, it.y, 3f, paint) }
            return true
        }

        private fun water(a: RectF, color: Int) {
            val progress = content.hydration?.coerceIn(0f, 1f)
            val edge = min(a.width(), a.height())
            val x = a.centerX()
            val y = a.centerY()
            val track = ColorUtils.blendARGB(panel, color, if (night) .28f else .17f)
            when (look.waterVisual) {
                "bottle" -> {
                    val jar = RectF(x - edge * .23f, y - edge * .27f, x + edge * .23f, y + edge * .41f)
                    val corners = edge * .12f
                    box(jar, track, corners)
                    progress?.let {
                        canvas.save()
                        canvas.clipPath(Path().apply { addRoundRect(jar, corners, corners, Path.Direction.CW) })
                        box(RectF(jar.left, jar.bottom - jar.height() * it, jar.right, jar.bottom), color, 2f)
                        canvas.restore()
                    }
                    box(jar, color, corners, true)
                    box(RectF(x - edge * .125f, y - edge * .43f, x + edge * .125f, y - edge * .29f),
                        if (progress == null) track else color, 2f)
                }
                "widget_bars" -> repeat(7) { i ->
                    val left = x - edge * .48f + i * edge * .14f
                    val bar = RectF(left, y - edge * .27f, left + edge * .085f, y + edge * .27f)
                    box(bar, track, 3f)
                    val fraction = ((progress ?: 0f) * 7 - i).coerceIn(0f, 1f)
                    box(RectF(bar.left, bar.bottom - bar.height() * fraction, bar.right, bar.bottom), color, 3f)
                }
                else -> {
                    val radius = edge * .39f
                    val dial = RectF(x - radius, y - radius, x + radius, y + radius)
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = (edge * .105f).coerceAtLeast(2f)
                    paint.color = track
                    canvas.drawArc(dial, 140f, 260f, false, paint)
                    if (progress != null && progress > 0f) {
                        paint.color = color
                        canvas.drawArc(dial, 140f, 260 * progress, false, paint)
                    }
                    paint.style = Paint.Style.FILL
                    glyph("water", x - edge * .14f, y - edge * .14f, edge * .28f, color)
                }
            }
        }

        private fun metric(kind: String, bounds: RectF, hero: Boolean) {
            val card = RectF(bounds).apply { inset(3.5f, 3.5f) }
            val rows = geometry.body == WidgetBody.ROWS
            val radius = (look.radius * .56f).coerceIn(7f, 20f)
            if (!rows) {
                box(card, panel, radius)
                box(RectF(card).apply { inset(.5f, .5f) }, seam, radius, true)
            } else {
                box(card, panel, 6f)
                val hasNextRow = bounds.bottom < height - if (geometry.footer) 47f else 1f
                if (hasNextRow) line(card.left + 8, bounds.bottom, card.right - 8, bounds.bottom, seam)
            }
            val m = content.metric(kind)
            val hue = tone(kind)
            val roomy = card.width() >= (if (kind == "water") 136f else 171f) && card.height() >= 57
            val tall = card.height() >= 106
            val micro = card.width() < 90 || card.height() < 47
            val inner = RectF(card).apply { inset(if (micro) 5f else 9f, 0f) }
            val artSize = if (roomy) min(card.height() - 18f, if (hero) 61f else 42f) else if (tall) 36f else 13f
            val artWidth = if (roomy && hero && kind == "glucose") min(102f, card.width() * .36f) else artSize
            val art = if (roomy && rtl) RectF(inner.left, card.centerY() - artSize / 2, inner.left + artWidth, card.centerY() + artSize / 2)
            else if (roomy) RectF(inner.right - artWidth, card.centerY() - artSize / 2, inner.right, card.centerY() + artSize / 2)
            else if (tall) RectF(inner.centerX() - artSize / 2, card.bottom - artSize - 5, inner.centerX() + artSize / 2, card.bottom - 5)
            else if (rtl) RectF(inner.left, card.top + 6, inner.left + artSize, card.top + artSize + 6)
            else RectF(inner.right - artSize, card.top + 6, inner.right, card.top + artSize + 6)
            val titleArea = RectF(inner)
            if (!micro && (roomy || !tall)) {
                if (rtl) titleArea.left = art.right + 4 else titleArea.right = art.left - 4
            }
            val readingArea = if (roomy) titleArea else inner
            val labelY = card.top + if (micro) 3f else if (roomy && tall) 14f else 6f
            val readingY = labelY + if (micro) 15f else 17f
            val amountSize = when { micro -> 15f; hero && tall -> 34f; hero && roomy -> 27f; rows -> 18f; else -> 22f }
            val useUnitLine = m.unit.isNotBlank() && (hero && tall || readingArea.width() < 80 && card.height() >= 62)
            text(m.label, titleArea, labelY, if (micro) 9.5f else 12f, secondary, bold = true, fit = true)
            text(if (useUnitLine) m.amount else m.value, readingArea, readingY, amountSize, ink, bold = true, fit = true)
            val noteY = readingY + amountSize * fontScale + 1f
            if (useUnitLine) text(m.unit, readingArea, noteY, 10f, hue)
            val goal = if (kind == "water") content.hydration?.let {
                words.f("widget_goal_pct", "amount" to number(it.toDouble() * 100, words))
            }.orEmpty() else ""
            val notes = if (look.details) listOf(m.period, m.detail, goal).filter(String::isNotBlank) else emptyList()
            val noteStart = noteY + if (useUnitLine) 12 * fontScale else 0f
            val noteLimit = if (!roomy && tall) art.top else card.bottom
            notes.forEachIndexed { i, note ->
                val top = noteStart + i * 12 * fontScale
                if (top + 11 * fontScale <= noteLimit - 2f) text(note, readingArea, top, 10f, secondary, fit = true)
            }
            if (micro) return
            if (kind == "water" && showsWaterGraphic(bounds.width(), bounds.height())) water(art, hue)
            else if (kind == "glucose" && (roomy || tall) && trace(RectF(art).apply { inset(3f, 6f) }, hue)) Unit
            else {
                val size = if (roomy || tall) min(20f, artSize) else artSize
                glyph(kind, art.centerX() - size / 2, art.centerY() - size / 2, size, hue)
            }
        }

        private fun control(spot: RectF, kind: String, fill: Int, label: String = "") {
            val fg = contrast(fill)
            box(spot, fill, (look.radius * .54f).coerceIn(7f, 18f))
            val described = label.isNotEmpty() && spot.width() >= 101f
            val x = if (described) if (rtl) spot.right - 20f else spot.left + 20f else spot.centerX()
            glyph(kind, x - 7.5f, spot.centerY() - 7.5f, 15f, fg)
            if (described) {
                val space = RectF(spot).apply { if (rtl) { right -= 33; left += 9 } else { left += 33; right -= 9 } }
                text(label, space, spot.centerY() - 6.5f * fontScale, 12f, fg, bold = true, fit = true)
            }
        }

        fun draw() {
            val radius = min(look.radius.toFloat(), min(width, height) / 2)
            val outer = RectF(0f, 0f, width, height)
            val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawColor(ColorUtils.setAlphaComponent(surface, look.opacity * 255 / 100))
            box(RectF(outer).apply { inset(.6f, .6f) }, ColorUtils.setAlphaComponent(seam, look.opacity * 255 / 100), radius, true)

            if (geometry.header) {
                fun headerButton(index: Int, kind: String) {
                    val center = if (rtl) 22f + index * 44 else width - 22f - index * 44
                    control(RectF(center - 15, 7f, center + 15, 37f), kind, panel)
                }
                headerButton(0, "settings")
                if (geometry.cycle) headerButton(1, "cycle")
                if (look.title && width >= 145f) {
                    val margin = if (geometry.cycle) 91f else 47f
                    val area = if (rtl) RectF(margin, 0f, width - 15, 44f) else RectF(15f, 0f, width - margin, 44f)
                    text(words["app"], area, 12f, 19f, ink, bold = true, fit = true)
                }
            }
            geometry.parts.zip(geometry.cells(width, height)).forEachIndexed { i, (kind, area) ->
                metric(kind, area, geometry.body == WidgetBody.SINGLE || geometry.body == WidgetBody.FOCUS && i == 0)
            }
            val action = parse(look.accent, if (night) 0xFFBAE5DB.toInt() else 0xFF244C49.toInt())
            fun button(rect: RectF, key: String, color: Int, label: String = "") {
                rect.inset(4f, 4f)
                control(rect, key, color, label)
            }
            if (geometry.compactAction) {
                val x = if (rtl) 0f else width - 42
                val split = if (geometry.undo) height / 2 else height
                button(RectF(x, 0f, x + 42, split), "plus", action)
                if (geometry.undo) button(RectF(x, split, x + 42, height), "undo", panel)
            }
            if (geometry.footer) {
                val size = if (geometry.undo) 48f else 0f
                val footer = RectF(if (rtl) size else 0f, height - 46, if (rtl) width else width - size, height)
                val key = geometry.footerKind
                val adding = key == "water" || key == "glucose"
                button(footer, if (adding) "plus" else "arrow", action, words[geometry.actionLabel(look)])
                if (geometry.undo) {
                    val undo = if (rtl) RectF(0f, footer.top, size, height) else RectF(width - size, footer.top, width, height)
                    button(undo, "undo", panel)
                }
            }
            canvas.restore()
        }
    }
}


