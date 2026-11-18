package com.yawmi.diabetes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

object GraphicalWidgets {
    fun render(c: Context, s: State, id: Int) {
        val cfg = c.getSharedPreferences("home-widgets", Context.MODE_PRIVATE)
        val base = Widgets.look(c, id)
        val cycle = cfg.getInt("${id}_cycle", 0).coerceAtLeast(0)
        val offset = cycle % base.parts.size
        val quick = base.quickWater && !s.preferences.lock
        val undo = if (quick) WidgetActions.latest(s, id) else null
        val look = base.copy(parts = base.parts.drop(offset) + base.parts.take(offset), quickWater = quick, undoWater = undo != null)
        val manager = AppWidgetManager.getInstance(c)
        val options = manager.getAppWidgetOptions(id)
        val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 280).coerceIn(110, 600)
        val height = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 230).coerceIn(80, 600)
        val dark = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val density = c.resources.displayMetrics.density.coerceIn(1f, 2.5f)
        val widgetSizes = if (Build.VERSION.SDK_INT >= 31) sizes(options) else emptyList()
        val view = if (Build.VERSION.SDK_INT >= 31 && widgetSizes.isNotEmpty()) {
            val area = widgetSizes.sumOf { it.width.coerceIn(110f, 600f).toDouble() * it.height.coerceIn(80f, 600f) }
            val scale = kotlin.math.sqrt(1_150_000.0 / area).toFloat().coerceIn(1f, density)
            RemoteViews(widgetSizes.associateWith { size ->
                layout(c, s, id, look, size.width.toInt().coerceIn(110, 600), size.height.toInt().coerceIn(80, 600), dark, scale, cycle, undo)
            })
        } else layout(c, s, id, look, width, height, dark, density, cycle, undo)
        manager.updateAppWidget(id, view)
    }

    @RequiresApi(31)
    @Suppress("DEPRECATION")
    private fun sizes(options: Bundle): List<SizeF> =
        options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            .asSequence().filter { it.width.isFinite() && it.width > 0 && it.height.isFinite() && it.height > 0 }
            .distinct().take(4).toList()

    internal fun layout(
        c: Context, s: State, id: Int, look: WidgetLook, width: Int, height: Int,
        dark: Boolean, scale: Float, cycle: Int, undo: Entry?
    ): RemoteViews {
        val cfg = c.getSharedPreferences("home-widgets", Context.MODE_PRIVATE)
        val w = Words(s.preferences.language)
        val quick = look.quickWater
        val geometry = WidgetLayout.calculate(width, height, look, w.rtl)
        val resource = if (geometry.compactAction) R.layout.home_widget_mini else when (geometry.body) {
            WidgetBody.SINGLE -> R.layout.home_widget
            WidgetBody.FOCUS -> R.layout.home_widget_focus
            WidgetBody.ROWS -> R.layout.home_widget_rows
            WidgetBody.TILES -> R.layout.home_widget_tiles
        }
        val view = RemoteViews(c.packageName, resource)
        view.setImageViewBitmap(R.id.widget_background, WidgetArt.bitmap(c, s, width, height, look, dark, pixelScale = scale))
        fun show(viewId: Int, visible: Boolean) = view.setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
        fun open(kind: String, action: String = "open"): PendingIntent {
            return PendingIntent.getActivity(c, 0, WidgetDestination.intent(c, id, kind, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val readValues = Widgets.valuesVisible(s.preferences.lock, look.visible)
        val summary by lazy { dayOverview(s, System.currentTimeMillis()) }
        fun description(kind: String): String {
            val name = w[when (kind) { "glucose" -> "day_glucose_average"; "water" -> "water_week"; "day_summary" -> "day_overview"; else -> kind }]
            if (!readValues) return "$name. ${w["widget_values_off"]}"
            val detail = when (kind) {
                "glucose" -> summary.glucoseValue(s.preferences.unit, w)?.let { it + " " + s.preferences.unit } ?: w["no_reading"]
                "water" -> {
                    val average = summary.waterAverageMl?.let { number(it, w) + " " + w["ml"] } ?: w["no_reading"]
                    val today = number(summary.waterMl, w) + " " + w["ml"]
                    "$average. ${w.f("widget_water_today", "amount" to today)}"
                }
                "day_summary" -> w.f("day_event_count", "count" to number(summary.activityCount.toDouble(), w))
                else -> w["next_reminder"]
            }
            return "$name. $detail"
        }
        val cellIds = if (geometry.body == WidgetBody.SINGLE) listOf(R.id.widget_primary)
            else listOf(R.id.widget_primary, R.id.widget_secondary, R.id.widget_third)
        cellIds.forEachIndexed { i, target ->
            val kind = geometry.parts.getOrNull(i)
            show(target, kind != null)
            if (kind != null) {
                view.setOnClickPendingIntent(target, open(kind))
                view.setContentDescription(target, description(kind))
            }
        }
        val settingsId = if (w.rtl) R.id.widget_settings_rtl else R.id.widget_settings
        val cycleId = if (w.rtl) R.id.widget_cycle_rtl else R.id.widget_cycle
        val addId = if (geometry.compactAction) {
            show(R.id.widget_mini_controls_rtl, w.rtl)
            show(R.id.widget_mini_controls, !w.rtl)
            if (w.rtl) R.id.widget_mini_add_rtl else R.id.widget_mini_add
        } else if (w.rtl) R.id.widget_add_rtl else R.id.widget_add
        val undoId = when {
            geometry.compactAction && w.rtl -> R.id.widget_mini_undo_rtl
            geometry.compactAction -> R.id.widget_mini_undo
            w.rtl -> R.id.widget_undo_rtl
            else -> R.id.widget_undo
        }
        show(R.id.widget_header, geometry.header)
        show(R.id.widget_header_rtl, w.rtl)
        show(R.id.widget_header_ltr, !w.rtl)
        show(cycleId, geometry.cycle)
        if (geometry.header) {
            view.setContentDescription(settingsId, w["widget_customise"])
            view.setOnClickPendingIntent(settingsId, PendingIntent.getActivity(c, id,
                Widgets.configIntent(c, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        if (geometry.cycle) {
            view.setContentDescription(cycleId, w["widget_cycle"])
            view.setOnClickPendingIntent(cycleId, WidgetActions.pending(c, id, "cycle", cycle.toString()))
        }
        show(R.id.widget_footer, geometry.footer)
        show(R.id.widget_footer_rtl, w.rtl)
        show(R.id.widget_footer_ltr, !w.rtl)
        show(undoId, geometry.undo)
        if (geometry.footer || geometry.compactAction) {
            val kind = geometry.footerKind
            val actionKey = when (kind) {
                "water" -> if (quick) "widget_add_cup" else "widget_log_water"
                "glucose" -> "add_reading"
                "day_summary" -> "details"
                else -> "open"
            }
            val action = if (kind == "water" && quick) {
                val saved = cfg.getString("${id}_token", null)
                val token = if(saved != null && s.entries.none { it.id == "widget_$saved" }) saved else
                    java.util.UUID.randomUUID().toString().also { cfg.edit().putString("${id}_token", it).apply() }
                WidgetActions.pending(c, id, "add", token)
            } else open(kind, if (kind in listOf("glucose", "water")) "add" else "open")
            view.setOnClickPendingIntent(addId, action)
            view.setContentDescription(addId, w[actionKey])
        }
        if (geometry.undo && undo != null) {
            view.setOnClickPendingIntent(undoId, WidgetActions.pending(c, id, "undo", undo.id))
            view.setContentDescription(undoId, w["undo"])
        }
        return view
    }
}

@Composable
fun WidgetPreview(s: State, look: WidgetLook, width: Int = 300, height: Int = 230, fitBox: Boolean = false, announce: Boolean = true) {
    val c = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val bitmap = remember(s, look, width, height, dark) {
        WidgetArt.bitmap(c, s, width, height, look, dark, pixelScale = c.resources.displayMetrics.density)
    }
    val description = if (announce) LocalWords.current["widget_preview"] else null
    val layout = if (fitBox) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)
    if (announce && LocalMotion.current) {
        androidx.compose.animation.Crossfade(bitmap, modifier = layout, label = "widget-preview") { frame ->
            Image(frame.asImageBitmap(), if (frame == bitmap) description else null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else Image(bitmap.asImageBitmap(), description, layout, contentScale = ContentScale.Fit)
}
