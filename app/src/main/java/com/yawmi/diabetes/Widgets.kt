package com.yawmi.diabetes

import android.app.*
import android.appwidget.*
import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.graphics.*
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object Widgets {
    fun valuesVisible(locked:Boolean,optIn:Boolean)=optIn && !locked
    private fun config(c:Context)=c.getSharedPreferences("home-widgets",Context.MODE_PRIVATE)
    internal fun ids(c: Context): List<Int> = AppWidgetManager.getInstance(c)
        .getAppWidgetIds(ComponentName(c, YawmiWidget::class.java)).sorted()

    internal fun configIntent(c: Context, id: Int) = Intent(c, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    fun pin(c: Context, look: WidgetLook = WidgetLook()): Boolean {
        val manager = AppWidgetManager.getInstance(c)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val payload = Bundle().apply {
            putString("mode",look.mode); putString("style",look.style); putBoolean("visible",look.visible)
            putStringArrayList("parts",ArrayList(look.parts)); putInt("opacity",look.opacity); putString("font",look.font)
            putBoolean("title",look.title); putBoolean("details",look.details); putBoolean("action",look.action)
            putString("background",look.background); putString("accent",look.accent); putString("waterVisual",look.waterVisual)
            putInt("radius",look.radius); putBoolean("quickWater",look.quickWater); putString("layout",look.layout)
        }
        val call = Intent(c, WidgetPinReceiver::class.java)
            .setData(android.net.Uri.parse("yawmi://widget-pin/${java.util.UUID.randomUUID()}"))
            .putExtra("look", payload)
        val flags = PendingIntent.FLAG_ONE_SHOT or if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(c, 0, call, flags)
        return runCatching { manager.requestPinAppWidget(ComponentName(c, YawmiWidget::class.java), null, callback) }
            .getOrDefault(false).also { if (!it) callback.cancel() }
    }
    fun updateAll(c:Context,s:State) {
        ids(c).forEach { render(c,s,it) }
    }
    internal fun save(c: Context, id: Int, look: WidgetLook) = save(c, id, look.mode, look.style, look.visible, look.parts,
        look.opacity, look.font, look.title, look.details, look.action, look.background, look.accent,
        look.waterVisual, look.radius, look.quickWater, look.layout)
    fun render(c:Context,s:State,id:Int)=GraphicalWidgets.render(c,s,id)
    fun save(c:Context,id:Int,mode:String,style:String,visible:Boolean,parts:List<String>,opacity:Int,font:String,title:Boolean,time:Boolean,action:Boolean,background:String,accent:String,waterVisual:String="ring",radius:Int=24,quickWater:Boolean=false,layout:String="widget_focus"){
        require(waterVisual in widgetWaterVisuals && radius in 12..32 && layout in widgetLayouts)
        require(mode in widgetModes && style in widgetStyles)
        require(parts.isNotEmpty() && parts.distinct().size==parts.size && parts.all{it in widgetParts})
        require(opacity in 0..100 && font in setOf("compact","comfortable","large"))
        if(background.isNotBlank())Color.parseColor(background);if(accent.isNotBlank())Color.parseColor(accent)
        config(c).edit().putString("${id}_mode",mode).putString("${id}_style",style).putBoolean("${id}_visible",visible)
            .putString("${id}_parts",parts.joinToString(",")).putInt("${id}_opacity",opacity).putString("${id}_font",font)
            .putBoolean("${id}_title",title).putBoolean("${id}_time",time).putBoolean("${id}_action",action)
            .putString("${id}_background",background).putString("${id}_accent",accent).putString("${id}_waterVisual",waterVisual).putInt("${id}_radius",radius)
            .putString("${id}_layout",layout).putBoolean("${id}_quickWater",quickWater).putInt("${id}_cycle",0).remove("${id}_token").apply()
        render(c,Store.get(c).read(),id)
    }
    fun look(c: Context, id: Int): WidgetLook {
        val config = config(c)
        fun str(key: String, fallback: String) = config.getString("${id}_$key", fallback) ?: fallback
        fun bool(key: String, default: Boolean) = config.getBoolean("${id}_$key", default)
        return WidgetLook(
            mode = str("mode", "summary"), style = str("style", "auto"),
            visible = bool("visible", false), parts = str("parts", widgetParts.joinToString(","))
                .split(',').filter { it in widgetParts }.distinct().ifEmpty { widgetParts },
            opacity = config.getInt("${id}_opacity", 100), font = str("font", "comfortable"),
            title = bool("title", true), details = bool("time", true), action = bool("action", true),
            background = str("background", ""), accent = str("accent", ""),
            waterVisual = str("waterVisual", "ring"), radius = config.getInt("${id}_radius", 24),
            quickWater = bool("quickWater", false), layout = str("layout", "widget_focus")
        )
    }
    fun remove(c:Context,ids:IntArray){val edit=config(c).edit();ids.forEach{id->listOf("mode","style","visible","parts","opacity","font","title","time","action","background","accent","waterVisual","radius","quickWater","layout","cycle","token").forEach{edit.remove("${id}_$it")}};edit.apply()}
}

class YawmiWidget:AppWidgetProvider(){
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){runCatching{val s=Store.get(c).read();ids.forEach{Widgets.render(c,s,it)}}}
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,options:Bundle){onUpdate(c,m,intArrayOf(id))}
    override fun onDeleted(c:Context,ids:IntArray){Widgets.remove(c,ids)}
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id !in Widgets.ids(context)) return
        val data = intent.getBundleExtra("look") ?: return
        val look = WidgetLook(mode = data.getString("mode","summary"), style = data.getString("style","auto"),
            visible = data.getBoolean("visible"), parts = data.getStringArrayList("parts") ?: widgetParts,
            opacity = data.getInt("opacity",100), font = data.getString("font","comfortable"),
            title = data.getBoolean("title",true), details = data.getBoolean("details",true), action = data.getBoolean("action",true),
            background = data.getString("background",""), accent = data.getString("accent",""),
            waterVisual = data.getString("waterVisual","ring"), radius = data.getInt("radius",24),
            quickWater = data.getBoolean("quickWater"), layout = data.getString("layout","widget_focus"))
        val task = goAsync()
        Reminders.worker.execute {
            try { Widgets.save(context, id, look) } finally { task.finish() }
        }
    }
}

class WidgetConfigActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);setResult(RESULT_CANCELED)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider!=ComponentName(this,YawmiWidget::class.java)){finish();return}
        val s=runCatching{Store.get(this).read()}.getOrElse{finish();return}
        setContent { YawmiTheme(s.preferences) { WidgetStudio(s, Widgets.look(this@WidgetConfigActivity, id), onDismiss={finish()}, onSave={next->
            Widgets.save(this@WidgetConfigActivity, id, next)
            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()
        })}}
    }
}
