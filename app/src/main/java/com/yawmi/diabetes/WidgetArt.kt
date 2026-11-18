package com.yawmi.diabetes

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class WidgetLook(val mode:String="summary",val style:String="auto",val visible:Boolean=false,
    val parts:List<String> = listOf("glucose","water","medication"),val opacity:Int=100,val font:String="comfortable",
    val title:Boolean=true,val details:Boolean=true,val action:Boolean=true,val background:String="",val accent:String="",
    val waterVisual:String="ring",val radius:Int=24,val quickWater:Boolean=false,val undoWater:Boolean=false,val layout:String="widget_focus")

/** Used by both the launcher and the in-app preview. */
object WidgetArt {
    fun bitmap(
        c: Context, s: State, width: Int, height: Int, look: WidgetLook,
        darkSystem: Boolean = false, pixelScale: Float = 1.5f
    ): Bitmap {
        val widthDp = width.coerceIn(110, 600)
        val heightDp = height.coerceIn(80, 600)
        val scale = pixelScale.coerceIn(1f, 3f).coerceAtMost(sqrt(1_300_000f / (widthDp * heightDp)))
        val bitmap = Bitmap.createBitmap((widthDp * scale).toInt(), (heightDp * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(scale, scale) }
        Painter(c, canvas, widthDp.toFloat(), heightDp.toFloat(), s, look, darkSystem).draw()
        return bitmap
    }

    private data class Metric(val title: String, val value: String, val unit: String = "", val detail: String = "")

    private class Painter(
        context: Context, val canvas: Canvas, val width: Float, val height: Float,
        val state: State, val look: WidgetLook, darkSystem: Boolean
    ) {
        private val words = Words(state.preferences.language)
        private val rtl = words.rtl
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val regular = Typeface.createFromAsset(context.assets, "fonts/tajawal_medium.ttf")
        private val bold = Typeface.createFromAsset(context.assets, "fonts/tajawal_bold.ttf")
        private val fontScale = when (look.font) { "compact" -> .9f; "large" -> 1.12f; else -> 1f }
        private val daylight = 0xFFF8F6EF.toInt()
        private val pine = 0xFF102D2A.toInt()
        private val proposed = when (look.style) {
            "forest" -> pine
            "widget_midnight" -> 0xFF162137.toInt()
            "widget_rose" -> 0xFFF9F0F1.toInt()
            "blue" -> 0xFFEAF5F6.toInt()
            "auto" -> if (darkSystem) pine else daylight
            else -> daylight
        }
        private val background = ColorUtils.setAlphaComponent(parse(look.background, proposed), 255)
        private val ink = readable(0xFF18322C.toInt(), background)
        private val dark = ink == Color.WHITE
        private val muted = readable(mix(ink, .76f), background)
        private val rule = mix(ink, if (dark) .20f else .13f)
        private val accent = parse(look.accent, if (dark) 0xFFC6E8A9.toInt() else 0xFF37634E.toInt())
        private val aqua = if (dark) 0xFF9ADAE0.toInt() else 0xFF216975.toInt()
        private val visible = Widgets.valuesVisible(state.preferences.lock, look.visible)
        private val layout = WidgetLayout.calculate(width.toInt(), height.toInt(), look, rtl)
        private val day by lazy { dayOverview(state, System.currentTimeMillis()) }
        private val next by lazy { Reminders.plan(state).firstOrNull { it.kind == "medication" } }
        private val fraction by lazy {
            if (visible && state.preferences.waterGoal > 0)
                (day.waterMl / state.preferences.waterGoal).coerceIn(0.0, 1.0).toFloat()
            else 0f
        }

        private fun parse(raw: String, fallback: Int): Int = runCatching { Color.parseColor(raw) }.getOrDefault(fallback)

        private fun readable(preferred: Int, surface: Int): Int = when {
            ColorUtils.calculateContrast(ColorUtils.setAlphaComponent(preferred, 255), surface) >= 4.5 -> ColorUtils.setAlphaComponent(preferred, 255)
            ColorUtils.calculateContrast(Color.WHITE, surface) >= 4.5 -> Color.WHITE
            else -> Color.BLACK
        }

        private fun mix(color: Int, amount: Float) = ColorUtils.blendARGB(background, color, amount)

        private fun category(key: String): Int {
            val tone = when (key) {
                "water" -> aqua
                "medication" -> if (dark) 0xFFF3CDA2.toInt() else 0xFF805839.toInt()
                else -> accent
            }
            return readable(tone, background)
        }

        private fun rounded(rect: RectF, color: Int, radius: Float) {
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        private fun round(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float) =
            rounded(RectF(x, y, x + w, y + h), color, radius)

        private fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int = rule, weight: Float = 1f) {
            paint.color = color
            paint.strokeWidth = weight
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, y, xx, yy, paint)
        }

        private fun text(
            value: String, x: Float, baseline: Float, size: Float, maxWidth: Float,
            color: Int = ink, strong: Boolean = false,
            align: Paint.Align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT,
            fit: Boolean = false
        ): Float {
            if (value.isBlank() || maxWidth <= 0f) return 0f
            type.typeface = if (strong) bold else regular
            type.textSize = size * fontScale
            if (fit) {
                val measured = type.measureText(value)
                if (measured > maxWidth) type.textSize = (type.textSize * maxWidth / measured).coerceAtLeast(8f)
            }
            type.textAlign = align
            type.color = color
            val shown = TextUtils.ellipsize(value, type, maxWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, x, baseline, type)
            return type.measureText(shown)
        }

        private fun glyph(kind: String, centerX: Float, centerY: Float, size: Float, color: Int) {
            canvas.save()
            canvas.translate(centerX - size / 2, centerY - size / 2)
            canvas.scale(size / 24, size / 24)
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.strokeWidth = 1.75f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            val path = Path()
            when (kind) {
                "glucose", "water" -> {
                    path.moveTo(12f, 2f)
                    path.cubicTo(9f, 6f, 4f, 12f, 4f, 15.5f)
                    path.cubicTo(4f, 24f, 20f, 24f, 20f, 15.5f)
                    path.cubicTo(20f, 12f, 15f, 6f, 12f, 2f)
                    canvas.drawPath(path, paint)
                    if (kind == "glucose") {
                        path.reset()
                        path.moveTo(7f, 15f)
                        path.lineTo(10f, 15f)
                        path.lineTo(12f, 11.5f)
                        path.lineTo(14f, 17f)
                        path.lineTo(17f, 14.5f)
                        canvas.drawPath(path, paint)
                    }
                }
                "medication" -> {
                    canvas.rotate(42f, 12f, 12f)
                    canvas.drawRoundRect(7.2f, 2f, 16.8f, 22f, 4.8f, 4.8f, paint)
                    canvas.drawLine(7.2f, 12f, 16.8f, 12f, paint)
                }
                "settings" -> {
                    canvas.drawLine(4f, 8f, 20f, 8f, paint)
                    canvas.drawLine(4f, 16f, 20f, 16f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(9f, 8f, 2.7f, paint)
                    canvas.drawCircle(15f, 16f, 2.7f, paint)
                }
                "cycle" -> {
                    canvas.drawArc(4f, 4f, 20f, 20f, -35f, 298f, false, paint)
                    canvas.drawLine(19f, 3f, 19f, 9f, paint)
                    canvas.drawLine(19f, 9f, 13f, 9f, paint)
                }
                "plus" -> {
                    canvas.drawLine(12f, 6f, 12f, 18f, paint)
                    canvas.drawLine(6f, 12f, 18f, 12f, paint)
                }
                else -> {
                    if (rtl) canvas.rotate(180f, 12f, 12f)
                    canvas.drawLine(5f, 12f, 19f, 12f, paint)
                    canvas.drawLine(14f, 7f, 19f, 12f, paint)
                    canvas.drawLine(19f, 12f, 14f, 17f, paint)
                }
            }
            paint.style = Paint.Style.FILL
            canvas.restore()
        }

        private fun metric(kind: String): Metric {
            val title = words[if (kind == "glucose") "widget_glucose_average" else kind]
            if (!visible) return Metric(title, "•••", detail = words["widget_values_off"])
            return when (kind) {
                "glucose" -> {
                    val value = day.glucoseValue(state.preferences.unit, words)
                    Metric(title, value ?: "—", if (value == null) "" else state.preferences.unit,
                        if (value == null) words["no_reading"]
                        else words.f("day_reading_count", "count" to number(day.glucoseCount.toDouble(), words)))
                }
                "water" -> {
                    val target = state.preferences.waterGoal
                    Metric(title, number(day.waterMl / 1000, words, 2), words["litres_short"],
                        if (target > 0) words.f("widget_goal_short", "amount" to number(target / 1000.0, words, 2) + " " + words["litres_short"])
                        else words["water_today"])
                }
                else -> {
                    val dose = next
                    Metric(title, dose?.let { timeText(it.due, words) } ?: "—", detail =
                        dose?.let { r -> state.medications.find { it.id == r.medicationId }?.name } ?: words["widget_no_dose"])
                }
            }
        }

        private fun value(item: Metric, x: Float, y: Float, size: Float, available: Float, align: Paint.Align) {
            val unitWidth = if (item.unit.isBlank()) 0f else min(available * .46f, if (item.unit.length > 4) 42f else 22f)
            val numberWidth = (available - unitWidth - if (unitWidth > 0f) 4f else 0f).coerceAtLeast(10f)
            val measured = text(item.value, x, y, size, numberWidth, strong = true, align = align, fit = true)
            if (unitWidth > 0f) {
                val unitX = x + (measured + 4f) * if (align == Paint.Align.RIGHT) -1f else 1f
                text(item.unit, unitX, y, min(11f, size * .54f), unitWidth, muted, align = align, fit = true)
            }
        }

        private fun trend(rect: RectF) {
            if (!visible) return
            val readings = day.entries.filter { it.kind == "glucose" }.sortedBy { it.time }.takeLast(12)
            if (readings.size < 2) return
            val values = readings.map(::mg)
            val low = values.min() - 4
            val range = values.max() - low + 4
            val tone = category("glucose")
            val points = values.mapIndexed { i, v ->
                PointF(rect.left + rect.width() * i / values.lastIndex,
                    rect.bottom - ((v - low) / range * rect.height()).toFloat())
            }
            for (i in 0..2) {
                val y = rect.top + rect.height() * i / 2
                line(rect.left, y, rect.right, y, rule, .65f)
            }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path(path).apply { lineTo(rect.right, rect.bottom); lineTo(rect.left, rect.bottom); close() }
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, ColorUtils.setAlphaComponent(tone, 46), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(fill, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            paint.color = tone
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            for (point in points) canvas.drawCircle(point.x, point.y, 2f, paint)
        }

        private fun ring(cx: Float, cy: Float, r: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = (r * .21f).coerceIn(2f, 6f)
            paint.color = mix(aqua, .20f)
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = aqua
            if (fraction > 0f) canvas.drawArc(cx-r, cy-r, cx+r, cy+r, -90f, 360f*fraction, false, paint)
            paint.style = Paint.Style.FILL
            glyph("water", cx, cy, r * 1.10f, aqua)
        }

        private fun bottle(cx:Float,y:Float,ww:Float,hh:Float){
            val x=cx-ww/2
            val left=x+ww*.08f
            val right=x+ww*.92f
            val bottom=y+hh*.96f
            val outline=Path().apply{
                moveTo(x+ww*.33f,y+hh*.12f)
                lineTo(x+ww*.67f,y+hh*.12f)
                lineTo(x+ww*.67f,y+hh*.23f)
                cubicTo(x+ww*.67f,y+hh*.30f,right,y+hh*.29f,right,y+hh*.43f)
                lineTo(right,y+hh*.83f)
                quadTo(right,bottom,x+ww*.65f,bottom)
                lineTo(x+ww*.35f,bottom)
                quadTo(left,bottom,left,y+hh*.83f)
                lineTo(left,y+hh*.43f)
                cubicTo(left,y+hh*.29f,x+ww*.33f,y+hh*.30f,x+ww*.33f,y+hh*.23f)
                close()
            }
            paint.style=Paint.Style.FILL
            paint.shader=LinearGradient(left,y,right,y,intArrayOf(0xFFAFD2D5.toInt(),0xFFE5F7F8.toInt(),0xFF93BFC5.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawPath(outline,paint)
            paint.shader=null
            if(fraction>0){
                val level=bottom-hh*.72f*fraction
                canvas.save()
                canvas.clipPath(outline)
                val wave=Path().apply{
                    moveTo(x,bottom)
                    lineTo(x,level)
                    cubicTo(cx,level-hh*.09f,cx,level+hh*.08f,x+ww,level)
                    lineTo(x+ww,bottom)
                    close()
                }
                paint.shader=LinearGradient(x,level,x,bottom,0xFF65C6CE.toInt(),0xFF1C687F.toInt(),Shader.TileMode.CLAMP)
                canvas.drawPath(wave,paint)
                paint.shader=null
                canvas.restore()
            }
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=1f
            paint.color=0xFF356B74.toInt()
            canvas.drawPath(outline,paint)
            paint.style=Paint.Style.FILL
            round(x+ww*.26f,y+hh*.02f,ww*.48f,hh*.13f,0xFFE1C68C.toInt(),2.6f)
            round(x+ww*.22f,y+hh*.42f,ww*.08f,hh*.35f,0x9AFFFFFF.toInt(),1.5f)
            repeat(3){ i ->
                val tick=y+hh*(.46f+i*.13f)
                paint.color=0x80356B74.toInt()
                canvas.drawLine(x+ww*.65f,tick,x+ww*.80f,tick,paint)
            }
        }

        private fun waterMark(cx: Float, cy: Float, size: Float) {
            when (look.waterVisual) {
                "bottle" -> bottle(cx, cy - size / 2, size * .64f, size)
                "widget_bars" -> {
                    val bar = size / 15
                    repeat(5) { i ->
                        val x = cx - size * .37f + size * i / 6
                        val h = size * (.43f + .11f * i)
                        val bottom = cy + size * .43f
                        round(x, bottom-h, bar, h, mix(aqua, .21f), bar)
                        val fill = h * (fraction * 5 - i).coerceIn(0f, 1f)
                        if (fill > 0) round(x, bottom-fill, bar, fill, aqua, bar)
                    }
                }
                else -> ring(cx, cy, size * .36f)
            }
        }

        private fun row(kind: String, bounds: RectF) {
            val m = metric(kind)
            val pad = if (bounds.width() < 190) 12f else 16f
            val start = if (rtl) bounds.right-pad else bounds.left+pad
            val end = if (rtl) bounds.left+pad else bounds.right-pad
            val dir = if (rtl) -1 else 1
            val centerY = bounds.centerY()
            val titleWidth = min(bounds.width() * .44f, bounds.width() - 2 * pad - 64f)
            glyph(kind, start+dir*8f, centerY, 16f, category(kind))
            text(m.title, start+dir*23f, centerY+4.5f, 12f, titleWidth, strong = true, fit = true)
            val available = (bounds.width() - 2*pad - 32f - titleWidth).coerceAtLeast(24f)
            val align = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
            if (available < 58f && m.unit.length > 4) {
                value(m.copy(unit = ""), end, centerY+2f, 16f, available, align)
                text(m.unit, end, centerY+13f, 8.5f, available, muted, align = align)
            } else value(m, end, centerY+5.5f, 16f, available, align)
        }

        private fun tile(kind: String, bounds: RectF) {
            if (bounds.height() < 55f && bounds.width() >= 136f) {
                row(kind, bounds)
                return
            }
            val m = metric(kind)
            val pad = if (bounds.width() < 106f) 9f else 14f
            val start = if (rtl) bounds.right-pad else bounds.left+pad
            val dir = if (rtl) -1 else 1
            val available = bounds.width() - 2*pad
            if (bounds.height() < 53f) {
                text(m.title, start, bounds.top+13f, 10.5f, available, muted, strong = true)
                value(m, start, bounds.top+30f, 17f, available, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
                return
            }
            val tall = bounds.height() >= 82f
            val header = bounds.top + if (tall) 25f else 20f
            val icon = if (available >= 61f || tall) 15f else 0f
            if (icon > 0f) glyph(kind, start+dir*7.5f, header-4.5f, icon, category(kind))
            text(m.title, start+dir*(if (icon>0f) 20f else 0f), header, 11.5f,
                available - if (icon>0f) 20f else 0f, strong = true, fit = true)
            val baseline = bounds.top + if (tall) 56f else 44f
            val hasUnitBelow = tall && available < 106f && m.unit.isNotBlank()
            value(if (hasUnitBelow) m.copy(unit = "") else m, start, baseline, if (tall) 25f else 18f,
                available, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
            if (hasUnitBelow) text(m.unit, start, baseline+14f, 10f, available, muted)
            val sub = baseline + if (hasUnitBelow) 30f else 17f
            if (look.details && bounds.bottom-sub >= 8f) text(m.detail, start, sub, 10f, available, muted)
            if (bounds.bottom - sub >= 39f) {
                val cy = sub + (bounds.bottom - sub) / 2f
                if (kind == "water") waterMark(bounds.centerX(), cy, min(53f, bounds.bottom-sub-8f))
                if (kind == "glucose") trend(RectF(bounds.left+pad, cy-9f, bounds.right-pad, cy+10f))
            }
        }

        private fun instrument(kind: String, bounds: RectF) {
            if (bounds.width() < 140f) {
                tile(kind, bounds)
                return
            }
            val m = metric(kind)
            val pad = if (bounds.width() >= 220f) 17f else 13f
            val start = if (rtl) bounds.right-pad else bounds.left+pad
            val dir = if (rtl) -1 else 1
            val available = bounds.width()-pad*2
            val chart = look.details && bounds.width() >= 168f && bounds.height() >= 74f &&
                (kind == "water" || kind == "glucose" && visible && day.glucoseCount > 1)
            val artWidth = if (chart) min(available*.38f, 124f) else 0f
            val textWidth = available - artWidth - if (chart) 12f else 0f
            val title = bounds.top + (bounds.height() * .25f).coerceIn(19f, 34f)
            val accent = category(kind)
            glyph(kind, start+dir*8f, title-4f, 16f, accent)
            text(m.title, start+dir*24f, title, 13f, textWidth-24f, strong = true)
            val baseline = title + (bounds.height()*.34f).coerceIn(23f, 40f)
            value(m, start, baseline, if (bounds.height() >= 86f) 35f else 26f, textWidth,
                if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
            if (look.details && baseline+19f <= bounds.bottom-4f)
                text(m.detail, start, baseline+19f, 11f, textWidth, muted)
            if (chart) {
                val artLeft = if (rtl) bounds.left+pad else bounds.right-pad-artWidth
                val artTop = title + 2f
                val artBottom = min(bounds.bottom-12f, baseline+9f)
                if (kind == "glucose") trend(RectF(artLeft+3f, artTop, artLeft+artWidth-3f, artBottom))
                else waterMark(artLeft+artWidth/2, (artTop+artBottom)/2,
                    min(artWidth, min(bounds.height() *.62f, 91f)))
            }
        }

        private fun overview(bounds: RectF) {
            val start = if (rtl) bounds.right-15f else bounds.left+15f
            val titleY = bounds.top+24f
            text(words["day_overview"], start, titleY, 14f, bounds.width()-30f, strong = true)
            val avg = metric("glucose")
            val water = metric("water")
            val doses = Metric(words["widget_doses_short"], if (visible) number(day.takenDoses.toDouble(), words) else "•••")
            val stats = listOf(avg, water, doses)
            if (bounds.width() >= 252f && bounds.height() >= 88f) {
                val usable = bounds.width()-18f
                val offset = bounds.left+9f
                val step = usable / stats.size
                val y = titleY+26f
                stats.forEachIndexed { index, item ->
                    val i = if (rtl) stats.lastIndex-index else index
                    val x = if (rtl) offset+(i+1)*step-8f else offset+i*step+8f
                    text(item.title, x, y, 11f, step-16f, muted, fit = true)
                    value(item, x, y+24f, 19f, step-16f, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
                    if (i > 0) line(offset+i*step, y-10f, offset+i*step, y+25f)
                }
            } else if (bounds.height() >= 104f) {
                stats.forEachIndexed { index, item ->
                    val y = titleY+23f+index*19f
                    val end = if (rtl) bounds.left+13f else bounds.right-13f
                    text(item.title, start, y, 11.2f, bounds.width()*.49f, muted, fit = true)
                    value(item, end, y, 13f, bounds.width()*.44f,
                        if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT)
                }
            } else {
                val description = if (visible) words.f("day_event_count", "count" to number(day.activityCount.toDouble(), words))
                    else words["widget_values_off"]
                text(description, start, titleY+26f, 13f, bounds.width()-30f, muted)
            }
            if (visible && look.details && bounds.height() >= 142f) {
                val avgWater = day.waterAverageMl?.let { number(it/1000, words, 2) + " " + words["litres_short"] } ?: "—"
                text(words["day_water_average"] + "  ·  " + avgWater, start, bounds.bottom-14f, 10.5f, bounds.width()-30f, muted)
            }
        }

        private fun header() {
            if (look.title && (!layout.cycle || width >= 145f)) {
                val x = if (rtl) width-27f else 27f
                round(x-11f, 11f, 22f, 22f, if (dark) 0xFFD1E9B5.toInt() else pine, 6f)
                val logoInk = if (dark) pine else 0xFFE4EFD0.toInt()
                for (i in 0..2) {
                    val h = 5f + i * 3f
                    round(x-6f+i*5f, 27f-h, 2.6f, h, logoInk, 1.3f)
                }
                val available = width - if (layout.cycle) 143f else 98f
                text(words["app"], if (rtl) x-18f else x+18f, 27f, 16f, available, strong = true)
            }
            glyph("settings", if (rtl) 22f else width-22f, 22f, 17f, ink)
            if (layout.cycle) glyph("cycle", if (rtl) 66f else width-66f, 22f, 16f, muted)
            line(14f, 43.5f, width-14f, 43.5f)
        }

        private fun footer() {
            val kind = layout.footerKind
            val key = when (kind) {
                "water" -> if (look.quickWater) "widget_add_cup" else "widget_log_water"
                "glucose" -> "add_reading"
                "day_summary" -> "details"
                else -> "open"
            }
            val y = height - 23f
            val actionWidth = width - if (layout.undo) 72f else 0f
            val left = if (rtl && layout.undo) 72f else 0f
            val button = RectF(left+10f, y-15f, left+actionWidth-10f, y+15f)
            rounded(button, mix(accent, if (dark) .22f else .13f), 11f)
            val textStart = if (rtl) button.right-29f else button.left+29f
            val iconX = if (rtl) button.right-15f else button.left+15f
            val color = category(kind)
            glyph(if (kind == "water" || kind == "glucose") "plus" else "open", iconX, y, 15f, color)
            text(words[key], textStart, y+4.5f, 11.5f, button.width()-38f, ink, strong = true, fit = true)
            if (layout.undo) text(words["undo"], if (rtl) 36f else width-36f, y+4.5f, 11.5f, 62f,
                muted, strong = true, align = Paint.Align.CENTER)
        }

        fun draw() {
            val radius = look.radius.toFloat().coerceIn(12f, 32f)
            val box = RectF(0f, 0f, width, height)
            rounded(box, ColorUtils.setAlphaComponent(background, look.opacity.coerceIn(0, 100)*255/100), radius)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
            if (layout.header) header()
            val cells = layout.cells(width, height)
            layout.parts.zip(cells).forEachIndexed { i, (kind, rect) ->
                canvas.save()
                canvas.clipRect(rect)
                when {
                    kind == "day_summary" -> overview(rect)
                    layout.body == WidgetBody.ROWS -> row(kind, rect)
                    layout.body == WidgetBody.SINGLE || layout.body == WidgetBody.FOCUS && i == 0 -> instrument(kind, rect)
                    else -> tile(kind, rect)
                }
                canvas.restore()
            }
            for (i in 1 until cells.size) {
                val rect = cells[i]
                val before = cells[i-1]
                if (abs(rect.top-before.top) < 1f) line(rect.left, rect.top+10f, rect.left, rect.bottom-10f)
                else line(14f, rect.top, width-14f, rect.top)
            }
            if (layout.footer) footer()
            canvas.restore()
        }
    }
}
