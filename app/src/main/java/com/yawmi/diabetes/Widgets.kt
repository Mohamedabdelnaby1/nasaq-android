package com.yawmi.diabetes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Keep the installed preference keys readable across app upgrades.
private fun WidgetLook.fields(): Map<String, Any> = mapOf(
    "mode" to mode, "style" to style, "visible" to visible, "parts" to parts.joinToString(","),
    "opacity" to opacity, "font" to font, "title" to title, "time" to details, "action" to action,
    "background" to background, "accent" to accent, "waterVisual" to waterVisual, "radius" to radius,
    "quickWater" to quickWater, "layout" to layout, "actionTarget" to actionTarget
)

internal fun WidgetLook.toBundle(): Bundle = Bundle().apply {
    fields().forEach { (name, value) ->
        when (value) {
            is String -> putString(name, value)
            is Int -> putInt(name, value)
            is Boolean -> putBoolean(name, value)
        }
    }
}

internal fun widgetColor(value: String): Boolean =
    value.isBlank() || runCatching { android.graphics.Color.parseColor(value.trim()) }.isSuccess

private fun widgetLook(read: (String) -> Any?): WidgetLook {
    fun str(name: String, fallback: String) = read(name) as? String ?: fallback
    fun bool(name: String, fallback: Boolean) = read(name) as? Boolean ?: fallback
    fun int(name: String, fallback: Int) = read(name) as? Int ?: fallback
    fun choice(name: String, values: List<String>, fallback: String) =
        str(name, fallback).takeIf { it in values } ?: fallback
    fun color(name: String) = str(name, "").trim().takeIf(::widgetColor).orEmpty()
    return WidgetLook(
        mode = choice("mode", widgetModes, "summary"), style = choice("style", widgetStyles, "auto"),
        visible = bool("visible", false),
        parts = str("parts", "").split(',').filter { it in widgetParts }.distinct().ifEmpty { widgetParts },
        opacity = int("opacity", 100).coerceIn(0, 100),
        font = choice("font", listOf("compact", "comfortable", "large"), "comfortable"),
        title = bool("title", true), details = bool("time", true), action = bool("action", true),
        background = color("background"), accent = color("accent"),
        waterVisual = choice("waterVisual", widgetWaterVisuals, "ring"),
        radius = int("radius", 24).coerceIn(12, 32), quickWater = bool("quickWater", false),
        layout = choice("layout", widgetLayouts, "widget_focus"),
        actionTarget = choice("actionTarget", widgetActionTargets, "auto")
    )
}

@Suppress("DEPRECATION")
internal fun widgetLook(bundle: Bundle): WidgetLook = widgetLook(bundle::get)

object Widgets {
    fun valuesVisible(locked: Boolean, optIn: Boolean) = optIn && !locked
    internal fun config(c: Context) = c.getSharedPreferences("home-widgets", Context.MODE_PRIVATE)
    private fun provider(c: Context) = ComponentName(c, YawmiWidget::class.java)
    internal fun ids(c: Context): List<Int> = AppWidgetManager.getInstance(c).getAppWidgetIds(provider(c)).sorted()
    internal fun exists(c: Context, id: Int) = id != AppWidgetManager.INVALID_APPWIDGET_ID &&
        AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider == provider(c)

    internal fun configIntent(c: Context, id: Int) = Intent(c, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    fun pin(c: Context, look: WidgetLook = WidgetLook()): Boolean {
        val manager = AppWidgetManager.getInstance(c)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val call = Intent(c, WidgetPinReceiver::class.java)
            .setData(android.net.Uri.parse("yawmi://widget-pin/" + java.util.UUID.randomUUID()))
            .putExtra("look", look.toBundle())
        val flags = PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(c, 0, call, flags)
        return runCatching { manager.requestPinAppWidget(provider(c), null, callback) }
            .getOrDefault(false).also { if (!it) callback.cancel() }
    }

    fun updateAll(c: Context, s: State) = update(c, s, ids(c))
    internal fun update(c: Context, s: State, ids: Iterable<Int>) {
        ids.forEach { id ->
            runCatching { GraphicalWidgets.render(c, s, id) }
                .onFailure {
                    Log.w("NasaqWidget", "Widget refresh failed", it)
                    if (s.preferences.lock) runCatching { placeholder(c, id) }
                }
        }
    }
    internal fun placeholder(c: Context, id: Int) = AppWidgetManager.getInstance(c).updateAppWidget(
        id, android.widget.RemoteViews(c.packageName, R.layout.widget_placeholder))

    internal fun save(c: Context, id: Int, look: WidgetLook) {
        check(exists(c, id))
        val editor = config(c).edit()
        widgetLook(look.toBundle()).fields().forEach { (key, value) ->
            val name = "${id}_$key"
            when (value) {
                is String -> editor.putString(name, value)
                is Int -> editor.putInt(name, value)
                is Boolean -> editor.putBoolean(name, value)
            }
        }
        check(editor.putInt("${id}_cycle", 0).remove("${id}_token").commit())
        Store.get(c).renderWidget(id)
    }

    fun look(c: Context, id: Int): WidgetLook {
        val all = config(c).all
        return widgetLook { key -> all["${id}_$key"] }
    }

    internal fun remove(c: Context, ids: IntArray) {
        val prefs = config(c)
        val prefixes = ids.map { "${it}_" }
        val edit = prefs.edit()
        prefs.all.keys.filter { key -> prefixes.any(key::startsWith) }.forEach(edit::remove)
        edit.apply()
    }
}

class YawmiWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val task = goAsync()
        Reminders.worker.execute {
            try {
                Store.get(c).refreshWidgets(ids.asIterable())
            } catch (error: Exception) {
                Log.w("NasaqWidget", "Could not read local widget data", error)
                ids.forEach { id -> runCatching { Widgets.placeholder(c, id) } }
            } finally {
                task?.finish()
            }
        }
    }
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) =
        onUpdate(c, m, intArrayOf(id))

    override fun onDeleted(c: Context, ids: IntArray) {
        Reminders.worker.execute { Widgets.remove(c, ids) }
    }
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!Widgets.exists(context, id)) return
        val look = intent.getBundleExtra("look")?.let(::widgetLook) ?: return
        val task = goAsync()
        Reminders.worker.execute {
            try { Widgets.save(context, id, look) }
            catch (error: Exception) { Log.w("NasaqWidget", "Could not save pinned widget", error) }
            finally { task.finish() }
        }
    }
}

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!Widgets.exists(this, id)) { finish(); return }
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { runCatching { Store.get(applicationContext).read() }.getOrNull() }
            if (state == null) {
                Toast.makeText(this@WidgetConfigActivity, Words("system")["load_error"], Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            if (state.preferences.secureScreen || state.preferences.lock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val initial = Widgets.look(applicationContext, id)
            val size = GraphicalWidgets.currentSize(applicationContext, id)
            var saving by mutableStateOf(false)
            setContent {
                YawmiTheme(state.preferences) {
                    WidgetStudio(state, initial, onDismiss = { if (!saving) finish() }, onSave = { next ->
                        if (!saving) {
                            saving = true
                            lifecycleScope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    runCatching { Widgets.save(applicationContext, id, next) }.isSuccess
                                }
                                saving = false
                                if (saved) {
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                    finish()
                                } else Toast.makeText(this@WidgetConfigActivity,
                                    Words(state.preferences.language)["save_error"], Toast.LENGTH_LONG).show()
                            }
                        }
                    }, actualSize = size, saving = saving)
                }
            }
        }
    }
}

