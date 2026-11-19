package com.yawmi.diabetes

import android.app.KeyguardManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import android.widget.Toast

object WidgetActions {
    internal const val undoWindow=12_000L

    fun latest(s:State,id:Int,now:Long=System.currentTimeMillis()):Entry? =
        s.entries.filter{it.kind=="water" && it.context=="widget_$id" && now-it.created in 0..undoWindow}
            .maxByOrNull{it.created}

    fun pending(c:Context,id:Int,action:String,token:String):PendingIntent {
        val uri=Uri.Builder().scheme("yawmi").authority("widget-action").appendPath(id.toString()).appendPath(action).appendPath(token).build()
        return PendingIntent.getBroadcast(c,0,Intent(c,WidgetActionReceiver::class.java).setData(uri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun record(s:State,widgetId:Int,token:String,now:Long):State {
        if(s.preferences.lock || token.length!=36)return s
        return Records.add(s,Entry(id="widget_$token",kind="water",value=s.preferences.cup.toDouble(),unit="mL",
            context="widget_$widgetId",time=now,created=now))
    }

    fun undo(s:State,widgetId:Int,token:String,now:Long):State {
        if(s.preferences.lock)return s
        val entry=latest(s,widgetId,now)?.takeIf{it.id==token} ?: return s
        return Records.remove(s,entry.id)
    }
}

class WidgetActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val segments=intent.data?.pathSegments ?: return
        if(segments.size!=3)return
        val id=segments[0].toIntOrNull() ?: return
        val action=segments[1]
        val token=segments[2]
        val pending=goAsync()
        Reminders.worker.execute {
            try {
                val manager=AppWidgetManager.getInstance(context)
                if(manager.getAppWidgetInfo(id)?.provider!=ComponentName(context,YawmiWidget::class.java))return@execute
                val config=Widgets.config(context)
                val store=Store.get(context)
                if(action=="cycle"){
                    if(config.getString("${id}_mode","summary")!="summary")return@execute
                    val index=config.getInt("${id}_cycle",0).coerceAtLeast(0)
                    if(index.toString()!=token)return@execute
                    config.edit().putInt("${id}_cycle",(index+1)%60).apply()
                    store.refreshWidgets(listOf(id))
                    return@execute
                }
                val look = Widgets.look(context, id)
                val water = look.mode == "water" || look.mode == "summary" && "water" in look.parts &&
                    (look.actionTarget !in look.parts || look.actionTarget == "water")
                if(action !in setOf("add","undo") || !look.action || !look.quickWater || !water ||
                    context.getSystemService(KeyguardManager::class.java).isDeviceLocked)return@execute
                val now=System.currentTimeMillis()
                var changed=false
                val state=store.update{current->
                    val next=when(action){
                        "add"->if(config.getString("${id}_token","")==token)WidgetActions.record(current,id,token,now)else current
                        else->WidgetActions.undo(current,id,token,now)
                    }
                    changed=next!=current
                    next
                }
                if(changed){
                    if (action == "add" && config.getString("${id}_token", "") == token) {
                        config.edit().remove("${id}_token").apply()
                        store.refreshWidgets(listOf(id))
                    }
                    Reminders.reschedule(context)
                    if (action == "add") {
                        val app = context.applicationContext
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Reminders.worker.execute {
                                runCatching {
                                    if (AppWidgetManager.getInstance(app).getAppWidgetInfo(id) != null)
                                        Store.get(app).refreshWidgets(listOf(id))
                                }
                            }
                        }, WidgetActions.undoWindow + 30)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post{
                        val words=Words(state.preferences.language)
                        Toast.makeText(context,words[if(action=="undo")"widget_undone"else"record_saved"],Toast.LENGTH_SHORT).show()
                    }
                } else if (action == "undo") store.refreshWidgets(listOf(id))
            } catch (error:Exception) {
                android.util.Log.w("NasaqWidget", "Widget action failed", error)
            } finally { pending.finish() }
        }
    }
}

