package com.yawmi.diabetes

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.sqrt

data class WidgetLook(val mode:String="summary",val style:String="auto",val visible:Boolean=false,
    val parts:List<String> = listOf("glucose","water","medication"),val opacity:Int=100,val font:String="comfortable",
    val title:Boolean=true,val details:Boolean=true,val action:Boolean=true,val background:String="",val accent:String="",
    val waterVisual:String="ring",val radius:Int=24,val quickWater:Boolean=false,val undoWater:Boolean=false,val layout:String="widget_focus")

/** One canvas shared by configuration previews and the launcher, with the same cell geometry as its touch targets. */
object WidgetArt {
    fun bitmap(
        c: Context, s: State, width: Int, height: Int, look: WidgetLook,
        darkSystem: Boolean = false, pixelScale: Float = 1.5f
    ): Bitmap {
        val w = width.coerceIn(110, 600)
        val h = height.coerceIn(80, 600)
        val density = pixelScale.coerceIn(1f, 3f).coerceAtMost(sqrt(1_300_000f / (w * h)))
        val result = Bitmap.createBitmap((w * density).toInt(), (h * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result).apply { scale(density, density) }
        DeskWidget(c, canvas, w, h, s, look, darkSystem).draw()
        return result
    }
}

private data class WidgetMetric(val label: String, val amount: String, val unit: String, val detail: String)

private class DeskWidget(
    context: Context, private val canvas: Canvas, width: Int, height: Int, private val state: State,
    private val look: WidgetLook, darkSystem: Boolean
) {
    private val w = width.toFloat()
    private val h = height.toFloat()
    private val words = Words(state.preferences.language)
    private val rtl = words.rtl
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val normal = Typeface.createFromAsset(context.assets, "fonts/tajawal_medium.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "fonts/tajawal_bold.ttf")
    private val scale = when (look.font) { "compact" -> .86f; "large" -> 1.19f; else -> 1f }
    private val base = parse(look.background, when (look.style) {
        "forest" -> 0xFF163936.toInt()
        "cream" -> 0xFFF8F0E4.toInt()
        "blue" -> 0xFFE3F0FF.toInt()
        "widget_rose" -> 0xFFFFE6EC.toInt()
        "widget_midnight" -> 0xFF222F50.toInt()
        else -> if (darkSystem) 0xFF192A46.toInt() else 0xFFEDF2FA.toInt()
    })
    private val dark = ColorUtils.calculateLuminance(base) < .25
    private val baseInk = ink(base)
    private val accent = parse(look.accent, if (dark) 0xFFBBD4FF.toInt() else 0xFF36579B.toInt())
    private val opacity = look.opacity.coerceIn(0, 100) / 100f
    private val visible = Widgets.valuesVisible(state.preferences.lock, look.visible)
    private val geometry = WidgetLayout.calculate(width, height, look, rtl)
    private val now = System.currentTimeMillis()
    private val day by lazy { dayOverview(state, now) }

    private fun parse(raw: String, fallback: Int) =
        ColorUtils.setAlphaComponent(runCatching { Color.parseColor(raw) }.getOrDefault(fallback), 255)

    private fun translucent(color: Int) = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * opacity).toInt())
    private fun merge(a: Int, b: Int, fraction: Float) = ColorUtils.blendARGB(a,b,fraction)
    private fun ink(background: Int): Int {
        val candidates = intArrayOf(0xFF152944.toInt(), Color.WHITE, Color.BLACK)
        return candidates.firstOrNull { ColorUtils.calculateContrast(it, background) >= 4.5 }
            ?: candidates.maxBy { ColorUtils.calculateContrast(it, background) }
    }
    private fun panelColor(kind: String, featured: Boolean): Int {
        val tone = when (kind) {
            "water" -> 0xFF71C8D5.toInt()
            "medication" -> 0xFFF4AB82.toInt()
            "day_summary" -> 0xFFB5A6F2.toInt()
            else -> accent
        }
        return merge(base, tone, if (featured) (if (dark) .56f else .86f) else if (dark) .25f else .18f)
    }

    private fun rect(area: RectF, color: Int, round: Float) {
        if (area.width() <= 0 || area.height() <= 0) return
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(area, round, round, paint)
    }
    private fun text(value: String, x: Float, baseline: Float, size: Float, limit: Float, color: Int,
        strong: Boolean = false, align: Paint.Align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT) {
        if (limit <= 0 || value.isBlank()) return
        type.typeface = if (strong) bold else normal
        type.textSize = size * scale
        val required = type.measureText(value)
        if (required > limit) type.textSize = (type.textSize * limit / required).coerceAtLeast(7.4f)
        type.textAlign = align
        type.color = color
        val displayed = TextUtils.ellipsize(value, type, limit, TextUtils.TruncateAt.END)
        canvas.drawText(displayed.toString(), x, baseline, type)
    }
    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1.5f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        canvas.drawLine(x1, y1, x2, y2, paint)
    }
    private fun glyph(key: String, x: Float, y: Float, color: Int, radius: Float = 7f) {
        paint.strokeWidth = 1.6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        paint.color = color
        when (key) {
            "water", "glucose" -> {
                val path = Path().apply {
                    moveTo(x, y-radius)
                    cubicTo(x-radius*.42f,y-radius*.25f,x-radius*.86f,y+radius*.13f,x-radius*.86f,y+radius*.48f)
                    cubicTo(x-radius*.86f,y+radius*1.4f,x+radius*.86f,y+radius*1.4f,x+radius*.86f,y+radius*.48f)
                    cubicTo(x+radius*.86f,y+radius*.13f,x+radius*.42f,y-radius*.25f,x,y-radius)
                }
                canvas.drawPath(path, paint)
                if (key=="glucose") {
                    line(x-4,y+2,x-1,y+2,color); line(x-1,y+2,x+1,y-1,color); line(x+1,y-1,x+4,y+3,color)
                }
            }
            "medication" -> {
                canvas.save()
                canvas.rotate(45f,x,y)
                canvas.drawRoundRect(x-radius*.5f,y-radius,x+radius*.5f,y+radius,radius/2,radius/2,paint)
                line(x-radius*.5f,y,x+radius*.5f,y,color)
                canvas.restore()
            }
            "settings" -> {
                line(x-7,y-3,x+7,y-3,color); line(x-7,y+3,x+7,y+3,color)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x-2,y-3,2.2f,paint); canvas.drawCircle(x+3,y+3,2.2f,paint)
            }
            "cycle" -> {
                canvas.drawArc(x-6,y-6,x+6,y+6,-5f,292f,false,paint)
                line(x+6,y-5,x+6,y-1,color); line(x+6,y-1,x+2,y-1,color)
            }
            "plus" -> {
                line(x-5,y,x+5,y,color,2f); line(x,y-5,x,y+5,color,2f)
            }
            else -> {
                line(x-6,y,x+6,y,color); val sign = if (rtl) -1 else 1
                line(x+sign*6,y,x+sign*2,y-4,color); line(x+sign*6,y,x+sign*2,y+4,color)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun metrics(kind: String): WidgetMetric {
        val key = when (kind) { "glucose" -> "widget_glucose_average"; "day_summary" -> "day_overview"; else -> kind }
        val label = words[key]
        if (!visible) return WidgetMetric(label, "•••", "", words["widget_values_off"])
        return when (kind) {
            "water" -> {
                val goal = state.preferences.waterGoal
                val average = day.waterAverageMl?.let { number(it / 1000, words, 2) + " " + words["litres_short"] }
                WidgetMetric(label, number(day.waterMl / 1000, words, 2), words["litres_short"],
                    average?.let { words["water_week"] + " · " + it }
                        ?: if (goal > 0) words.f("widget_goal_short", "amount" to (number(goal / 1000.0, words, 2) + " " + words["litres_short"])) else "")
            }
            "medication" -> {
                val next = Reminders.plan(state, now, now + 86_400_000).firstOrNull { it.kind == "medication" }
                WidgetMetric(label, next?.let { timeText(it.due, words) } ?: "—", "",
                    next?.let { r -> state.medications.firstOrNull { it.id == r.medicationId }?.name } ?: words["widget_no_dose"])
            }
            "day_summary" -> WidgetMetric(label, number(day.activityCount.toDouble(), words), "",
                words.f("day_event_count", "count" to number(day.activityCount.toDouble(), words)))
            else -> WidgetMetric(label, day.glucoseValue(state.preferences.unit, words) ?: "—", state.preferences.unit,
                words.f("day_reading_count", "count" to number(day.glucoseCount.toDouble(), words)))
        }
    }

    private fun visual(kind: String, area: RectF, color: Int) {
        if (area.width() < 21 || area.height() < 20) return
        val cx = area.centerX(); val cy = area.centerY()
        val faded = ColorUtils.setAlphaComponent(color, 66)
        when (kind) {
            "water" -> {
                val fill = if (visible && state.preferences.waterGoal > 0)
                    (day.waterMl / state.preferences.waterGoal).toFloat().coerceIn(0f,1f) else 0f
                val max = min(area.width(),area.height())
                when (look.waterVisual) {
                    "bottle" -> {
                        val half = max * .254f
                        val body = RectF(cx-half,cy-max*.26f,cx+half,cy+max*.39f)
                        rect(body, faded, 6f)
                        rect(RectF(cx-half*.57f,cy-max*.42f,cx+half*.57f,cy-max*.20f),color,3f)
                        if (fill > 0f) {
                            canvas.save()
                            val outline = Path().apply { addRoundRect(body, 6f, 6f, Path.Direction.CW) }
                            canvas.clipPath(outline)
                            rect(RectF(body.left, body.bottom-body.height()*fill, body.right, body.bottom), color, 1f)
                            canvas.restore()
                        }
                    }
                    "widget_bars" -> repeat(5) { i ->
                        val width = max * .105f
                        val x = cx - max*.38f + i*max*.16f
                        val y = cy + max*.32f
                        val height = max*(.36f+i*.09f)
                        rect(RectF(x,y-height,x+width,y),faded,3f)
                        val partial = (fill*5-i).coerceIn(0f,1f)
                        if(partial>0) rect(RectF(x,y-height*partial,x+width,y),color,3f)
                    }
                    else -> {
                        val radius = max * .34f
                        paint.color = faded; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                        canvas.drawCircle(cx,cy,radius,paint)
                        paint.color = color
                        if (fill > 0) canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,-90f,360f*fill,false,paint)
                        paint.style = Paint.Style.FILL
                        glyph("water", cx, cy, color, max*.12f)
                    }
                }
            }
            "glucose" -> {
                val readings = if (visible) day.entries.filter { it.kind == "glucose" }.asReversed().takeLast(10).map(::mg) else emptyList()
                if (readings.size < 2) { glyph(kind,cx,cy,faded,13f); return }
                val lo = readings.min()
                val span = (readings.max()-lo).coerceAtLeast(1.0)
                val points = readings.mapIndexed { i, value ->
                    PointF(area.left + area.width() * i / readings.lastIndex,
                        area.bottom - area.height() * (.19f + .65f * ((value - lo) / span).toFloat()))
                }
                repeat(3) { i ->
                    val y = area.top + area.height() * (.17f+i*.33f)
                    line(area.left,y,area.right,y,faded,1f)
                }
                points.zipWithNext().forEach { (a,b) -> line(a.x,a.y,b.x,b.y,color,2f) }
                paint.style=Paint.Style.FILL;paint.color=color
                points.forEach { canvas.drawCircle(it.x,it.y,2.3f,paint) }
            }
            else -> glyph(if(kind=="medication") kind else "arrow",cx,cy,color,12f)
        }
    }

    private fun metricCard(kind: String, hit: RectF, featured: Boolean) {
        val area = RectF(hit).apply { inset(5f, 4f) }
        val bg = panelColor(kind, featured)
        val fg = ink(bg)
        val muted = merge(bg, fg, .75f)
        rect(area, translucent(bg), if (featured) 17f else 14f)
        val item = metrics(kind)
        val pad = if (area.width() < 81f) 6f else 10f
        val start = if(rtl)area.right-pad else area.left+pad
        val end = if(rtl)area.left+pad else area.right-pad
        val direction = if (rtl) -1 else 1
        val space = area.width()-2*pad
        if (area.height() < 61f && area.width() >= 120f) {
            val mid = area.centerY()
            val amountSpace = (space * .40f).coerceAtLeast(31f)
            if (area.width() >= 155) glyph(kind,start+direction*6f,mid-2,fg,5.5f)
            val iconSpace = if(area.width()>=155) 16 else 0
            text(item.label, start+direction*iconSpace, mid-1f, 11.5f, space-amountSpace-iconSpace-3, fg, true)
            if (look.details && area.height() >= 39f && item.detail.isNotEmpty())
                text(item.detail,start+direction*iconSpace,mid+12f,8f,space-amountSpace-iconSpace-3,muted)
            val align=if(rtl)Paint.Align.LEFT else Paint.Align.RIGHT
            text(item.amount,end,mid+2f,18f,amountSpace,fg,true,align)
            text(item.unit,end,mid+13f,8.5f,amountSpace,muted,false,align)
            return
        }
        val hasVisual = area.width() >= 141f && area.height() >= 64f
        val chartSpace = if (hasVisual) (space*.42f).coerceAtMost(132f) else 0f
        text(item.label, start, area.top+19, 12.5f, space, fg, true)
        val size = if (area.height() > 100 && featured) 34f else if (area.height()>75f) 25f else 19f
        val baseline = area.top + min(area.height() - 13f, if (size>25f) 61f else 46f)
        text(item.amount,start,baseline,size,space-chartSpace,fg,true)
        if(item.unit.isNotBlank() && baseline+11<=area.bottom)
            text(item.unit,start,baseline+11,9.5f,space-chartSpace,muted)
        if (look.details && baseline+25<=area.bottom && item.detail.isNotBlank())
            text(item.detail,start,baseline+24,9.5f,space,muted)
        if (hasVisual) {
            val left = if(rtl)area.left+pad else area.right-pad-chartSpace+5
            val right = if(rtl)area.left+pad+chartSpace-5 else area.right-pad
            visual(kind,RectF(left,area.top+26,right,min(area.bottom-9,area.top+100)),fg)
        } else if (kind == "water" && area.height() >= 90 && area.width() >= 92) {
            visual(kind,RectF(area.left+14,baseline+12,area.right-14,area.bottom-7),fg)
        }
    }

    fun draw() {
        val radius = look.radius.coerceIn(12,32).toFloat()
        val frame = RectF(0f,0f,w,h)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(frame,radius,radius,Path.Direction.CW) })
        rect(frame, translucent(base), radius)
        if (geometry.header) {
            val from = if (rtl) w-12 else 12f
            val settings = if (rtl) 22f else w-22f
            val hold = if (geometry.cycle) 88f else 44f
            glyph("settings", settings,22f,baseInk)
            if (geometry.cycle) glyph("cycle", if(rtl)66f else w-66f,22f,baseInk)
            if (look.title) text(words["app"],from,27f,18f,w-hold-18f,baseInk,true)
        }
        geometry.parts.zip(geometry.cells(w,h)).forEachIndexed { i, (kind, area) ->
            metricCard(kind,area,(i==0 && geometry.body in setOf(WidgetBody.FOCUS, WidgetBody.SINGLE)))
        }
        if (geometry.compactAction) {
            val x = if (rtl) 5f else w-37f
            val fill = panelColor("water",true)
            rect(RectF(x,8f,x+32f,h-8),translucent(fill),14f)
            glyph("plus",x+16,h/2,ink(fill))
        }
        if (geometry.footer) {
            val active = panelColor(geometry.footerKind,true)
            val fg = ink(active)
            val area = if (geometry.undo) {
                if(rtl)RectF(49f,h-39f,w-6f,h-6f) else RectF(6f,h-39f,w-49f,h-6f)
            } else RectF(6f,h-39f,w-6f,h-6f)
            rect(area,translucent(active),13f)
            val label = words[when(geometry.footerKind) {
                "water" -> if(look.quickWater) "widget_add_cup" else "widget_log_water"
                "glucose" -> "add_reading"
                else -> "details"
            }]
            glyph(if(geometry.footerKind in listOf("water","glucose"))"plus" else "arrow",
                if(rtl)area.right-14 else area.left+14,area.centerY(),fg)
            text(label,if(rtl)area.right-26 else area.left+26,h-17,11.5f,area.width()-36,fg,true)
            if(geometry.undo) glyph("cycle",if(rtl)22f else w-22f,h-23,baseInk)
        }
        canvas.restore()
    }
}
