package com.yawmi.diabetes

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yawmi.diabetes.core.Planner
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.concurrent.Executors

data class Reminder(val id:String,val kind:String,val due:Long,val original:Long=due,
                    val medicationId:String="",val occurrence:String="",val attempt:Int=0) {
    fun json()=JSONObject().put("id",id).put("kind",kind).put("due",due).put("original",original)
        .put("medicationId",medicationId).put("occurrence",occurrence).put("attempt",attempt)
    companion object { fun read(o:JSONObject)=Reminder(o.getString("id"),o.getString("kind"),o.getLong("due"),
        o.optLong("original",o.getLong("due")),o.optString("medicationId"),o.optString("occurrence"),o.optInt("attempt")) }
}

object Records {
    fun add(s:State,e:Entry):State {
        if(s.entries.any { it.id==e.id || (e.kind=="dose" && e.occurrence.isNotBlank() && it.kind=="dose" && it.occurrence==e.occurrence) }) return s
        var next=e; var meds=s.medications
        if(e.kind=="dose" && e.status=="taken") {
            meds=meds.map { m -> if(m.id==e.medicationId && m.stock>=0) {
                val used=minOf(m.stock,m.stockPerDose); next=e.copy(value=used.toDouble(),second=m.stockEpoch.toDouble()); m.copy(stock=m.stock-used)
            } else m }
        }
        return s.copy(entries=s.entries+next,medications=meds)
    }
    fun replace(s: State, previous: Entry, changed: Entry): State {
        require(previous.id == changed.id && previous.kind == changed.kind)
        require(s.entries.firstOrNull { it.id == previous.id } == previous)
        if (previous.kind == "dose" && previous.medicationId == changed.medicationId && previous.status == changed.status) {
            val sameDose = changed.copy(value = previous.value, second = previous.second)
            return s.copy(entries = s.entries.map { if (it.id == previous.id) sameDose else it })
        }
        return add(remove(s, previous.id), changed)
    }

    fun remove(s:State,id:String):State {
        val entry=s.entries.find { it.id==id } ?: return s
        val meds=if(entry.kind=="dose" && entry.status=="taken" && entry.value>0) s.medications.map {
            if(it.id==entry.medicationId && it.stock>=0 && it.stockEpoch==entry.second.toLong()) it.copy(stock=(it.stock+entry.value.toInt()).coerceAtMost(100000)) else it
        } else s.medications
        return s.copy(entries=s.entries.filterNot { it.id==id },medications=meds)
    }
}

object Reminders {
    val worker=Executors.newSingleThreadExecutor()
    private const val ACTION="com.yawmi.diabetes.REMINDER"
    private fun prefs(c:Context)=c.getSharedPreferences("reminder-engine",Context.MODE_PRIVATE)
    private fun alarms(c:Context)=c.getSystemService(AlarmManager::class.java)
    fun exact(c:Context)=Build.VERSION.SDK_INT<31 || alarms(c).canScheduleExactAlarms()
    fun permitted(c:Context)=NotificationManagerCompat.from(c).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT<33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    fun channelEnabled(c:Context,id:String)=c.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(id)?.importance!=NotificationManager.IMPORTANCE_NONE
    fun channels(c:Context,p:Preferences) {
        val w=Words(p.language); val manager=c.getSystemService(NotificationManager::class.java)
        listOf("medication" to "medications","glucose" to "glucose","water" to "water","symptom" to "followup","care" to "care_tools").forEach { (id,label) ->
            val channel=NotificationChannel(id,w[label],if(id=="water") NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH)
            channel.description=w["notification_sound_note"]; channel.enableVibration(true)
            channel.lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(channel)
        }
    }
    fun medicationChannel(c:Context,m:Medication,p:Preferences) {
        if(!m.customAlert)return
        val w=Words(p.language)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(m.channelId(),w["medication"]+" · "+m.name,NotificationManager.IMPORTANCE_HIGH).apply {
                description=w["notification_sound_note"];enableVibration(true);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
    }
    private fun repeatFor(s:State,r:Reminder):Pair<Int,Int> {
        val m=s.medications.find {it.id==r.medicationId}
        return (m?.repeatMinutes?.takeIf{it>=5} ?: s.preferences.repeatMinutes) to
            (m?.repeatCount?.takeIf{it>=0} ?: s.preferences.maxRepeats)
    }
    fun plan(s:State,from:Long=System.currentTimeMillis(),until:Long=from+172_800_000):List<Reminder> {
        val zone=ZoneId.systemDefault(); val meals=s.entries.filter { it.kind=="meal" }.map { Planner.Meal(it.id,it.context,it.time) }
        val recorded=s.entries.filter { it.kind=="dose" }.map { it.occurrence }.toSet()
        val result=mutableListOf<Reminder>(); val p=s.preferences
        s.medications.filter { it.active && it.reminders }.forEach { med ->
            Planner.events(med.planner(),meals,from,until,zone).filterNot { it.id() in recorded }.forEach { event ->
                // Drug reminders are not shifted to another dose time by quiet hours.
                val local=Instant.ofEpochMilli(event.millis()).atZone(zone).toLocalTime()
                if(!p.medicationQuiet || !Planner.quiet(local,LocalTime.parse(p.quietStart),LocalTime.parse(p.quietEnd)))
                    result+=Reminder(event.id(),"medication",event.millis(),medicationId=med.id,occurrence=event.id())
            }
        }
        if(p.glucoseEnabled) {
            var date=Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
            val end=Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
            while(!date.isAfter(end)) {
                p.glucoseTimes.distinct().forEach { text ->
                    val due=date.atTime(LocalTime.parse(text)).atZone(zone).toInstant().toEpochMilli()
                    if(due in from until until && !isQuiet(due,p)) result+=Reminder("glucose_${date}_$text","glucose",due)
                }; date=date.plusDays(1)
            }
        }
        if(p.afterMealEnabled) s.entries.filter { it.kind=="meal" }.forEach { meal ->
            val due=meal.time+p.afterMealMinutes*60_000L
            if(due in from until until && !isQuiet(due,p)) result+=Reminder("after_${meal.id}","glucose",due)
        }
        if(p.waterEnabled && p.waterSmart && p.waterGoal>0){
            result+=smartWaterSlots(p,from,until,zone).filter {r->
                val day=Instant.ofEpochMilli(r.due).atZone(zone).toLocalDate()
                val total=s.entries.filter{it.kind=="water" && Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate()==day}.sumOf{it.value}
                total<p.waterGoal
            }
        }else if(p.waterEnabled) {
            val first=from-from.mod(p.waterEvery*60_000L)+p.waterEvery*60_000L
            val lastWater=s.entries.filter { it.kind=="water" }.maxOfOrNull { it.time } ?: 0
            var due=first
            while(due<until) {
                if(due-lastWater>=p.waterEvery*60_000L && !isQuiet(due,p)) result+=Reminder("water_$due","water",due)
                due+=p.waterEvery*60_000L
            }
        }
        s.care.items.filter{it.alert && !it.done && it.kind in setOf("visit","lab","refill") && it.time in from until until}
            .forEach{result+=Reminder("care_${it.id}_${it.time}","care",it.time,occurrence=it.id)}
        return result.filter { r->val day=Instant.ofEpochMilli(r.due).atZone(zone).dayOfWeek.value; when(r.kind){"water"->day in p.waterDays;"glucose"->day in p.glucoseDays;else->true} }.distinctBy { it.id }.sortedBy { it.due }
    }
    private fun isQuiet(time:Long,p:Preferences)=Planner.quiet(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime(),
        LocalTime.parse(p.quietStart),LocalTime.parse(p.quietEnd))
    private fun token(c:Context,r:Reminder,action:String="show"):PendingIntent {
        val uri=Uri.Builder().scheme("yawmi").authority("reminder").appendPath(r.id).appendPath(action).build()
        val intent=Intent(c,ReminderReceiver::class.java).setAction(ACTION).setData(uri)
            .putExtra("reminder",r.json().toString()).putExtra("operation",action)
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun saved(c:Context,key:String):List<Reminder> = try {
        val a=JSONArray(prefs(c).getString(key,"[]")); (0 until a.length()).map { Reminder.read(a.getJSONObject(it)) }
    } catch(e:Exception) { emptyList() }
    @Synchronized fun reschedule(c:Context,s:State=Store.get(c).read()) {
        channels(c,s.preferences)
        s.medications.filter{it.active && it.customAlert}.forEach{medicationChannel(c,it,s.preferences)}
        val previouslyScheduled = saved(c, "scheduled")
        val now=System.currentTimeMillis(); val recorded=s.entries.filter { it.kind=="dose" }.map { it.occurrence }.toSet()
        val manual=saved(c,"oneshots").filter { it.due>now && it.occurrence !in recorded &&
            (it.kind!="care" || s.care.items.any{item->item.id==it.occurrence && item.alert && !item.done && item.time==it.original}) &&
            (it.medicationId.isEmpty() || s.medications.any { med -> med.id==it.medicationId && med.active && med.reminders }) }
        prefs(c).edit().putString("oneshots",JSONArray(manual.map { it.json() }).toString()).apply()
        val reminders=(plan(s,now-60_000)+manual).distinctBy { it.id }.filter { !prefs(c).getBoolean("fired_${it.id}",false) }
            .map {if(it.due<=now)it.copy(due=now+1500)else it}
        c.getSystemService(NotificationManager::class.java).activeNotifications.forEach {active->
            val occurrence=active.notification.extras.getString("yawmi.occurrence").orEmpty()
            val medication=active.notification.extras.getString("yawmi.medication").orEmpty()
            if((occurrence.isNotBlank() && occurrence in recorded) || (medication.isNotBlank() && s.medications.none{it.id==medication && it.active && it.reminders}))
                NotificationManagerCompat.from(c).cancel(active.id)
        }
        val maintenance = Reminder("maintenance", "maintenance", nextWidgetRefresh(now))
        val next = reminders + maintenance
        next.forEach { schedule(c, it) }
        val ids = next.mapTo(mutableSetOf()) { it.id }
        previouslyScheduled.filterNot { it.id in ids }.forEach { alarms(c).cancel(token(c, it)) }
        prefs(c).edit().putString("scheduled", JSONArray(next.map { it.json() }).toString())
            .putBoolean("planned_exact", exact(c)).apply()
        // Old ledger entries are only deduplication metadata; never medical values.
        val old=prefs(c).all.filter { it.key.startsWith("fired_") }
        if(old.size>3000) prefs(c).edit().also { editor -> old.keys.take(old.size-2000).forEach { editor.remove(it) } }.apply()
    }
    @Synchronized
    internal fun reconcile(c: Context) {
        val now = System.currentTimeMillis()
        if (prefs(c).getBoolean("planned_exact", !exact(c)) != exact(c) ||
            saved(c, "scheduled").none { it.kind == "maintenance" && it.due in (now + 1)..nextWidgetRefresh(now) }) reschedule(c)
    }

    private fun schedule(c:Context,r:Reminder) {
        if(r.kind=="maintenance") { alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,token(c,r)); return }
        try {
            if(exact(c)) alarms(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,token(c,r))
            else alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,token(c,r))
        } catch(e:SecurityException) { alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,token(c,r)) }
    }
    @Synchronized fun once(c:Context,kind:String,minutes:Int):Unit {
        require(kind in setOf("glucose","symptom")); require(minutes in 1..300)
        val r=Reminder("manual_${java.util.UUID.randomUUID()}",kind,System.currentTimeMillis()+minutes*60_000L)
        prefs(c).edit().putString("oneshots",JSONArray((saved(c,"oneshots")+r).map { it.json() }).toString()).apply()
        reschedule(c)
    }
    fun audit(c:Context,r:Reminder,status:String) { Store.get(c).update {
        it.copy(audit=(it.audit+Audit(r.id,System.currentTimeMillis(),r.kind,status)).takeLast(500))
    } }
    fun test(c:Context,kind:String="glucose"): String {
        require(kind in setOf("glucose","water","medication","symptom"))
        val s=Store.get(c).read();channels(c,s.preferences)
        return post(c, Reminder("test", kind, System.currentTimeMillis()), s, test = true)
    }

    internal fun upcoming(c: Context, now: Long = System.currentTimeMillis()): List<Reminder> =
        saved(c, "scheduled").filter { it.kind != "maintenance" && it.due >= now }.sortedBy { it.due }

    fun scheduledCount(c:Context) = upcoming(c).size

    internal fun channelFor(s: State, r: Reminder) =
        s.medications.firstOrNull { it.id == r.medicationId }?.channelId() ?: r.kind
    private fun notificationId(c:Context,id:String):Int {
        val key="nid_$id"; val p=prefs(c); if(p.contains(key)) return p.getInt(key,1)
        val next=p.getInt("next-id",100)+1; p.edit().putInt("next-id",next).putInt(key,next).apply();return next
    }
    private fun open(c:Context,r:Reminder):PendingIntent=PendingIntent.getActivity(c,0,
        Intent(c,MainActivity::class.java).setData(Uri.Builder().scheme("yawmi").authority("open").appendPath(r.id).build())
            .putExtra("screen",if(r.kind=="medication") "medications" else "today")
            .putExtra("entry",if(r.kind=="care")"care_tools"else""),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun post(c:Context,r:Reminder,s:State,test:Boolean=false): String {
        val p=s.preferences; val w=Words(p.language); val med=s.medications.find { it.id==r.medicationId }
        val channel = channelFor(s, r)
        fun finish(status: String): String {
            runCatching { audit(c, r, status) }
            return status
        }
        if (!permitted(c)) return finish("reminder_no_permission")
        if (!channelEnabled(c, channel)) return finish("reminder_channel_disabled")
        val care=s.care.items.find{it.id==r.occurrence && r.kind=="care"}
        val title=if(p.lock || test) w["reminder_title"] else med?.name ?: care?.let{w[it.kind]} ?: w[if(r.kind=="symptom") "followup" else r.kind]
        val body=if(test) w["test_body"] else if(p.lock) w["privacy_hidden"] else w[when(r.kind) {
            "medication"->"med_reminder";"water"->"water_reminder";"symptom"->"followup_body";"care"->"care_reminder_body";else->"glucose_reminder"
        }]
        val notification=NotificationCompat.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .addExtras(android.os.Bundle().apply {putString("yawmi.occurrence",r.occurrence);putString("yawmi.medication",r.medicationId)})
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open(c,r))
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(if(p.privateNotifications) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(NotificationCompat.Builder(c,r.kind).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(w["app"]).setContentText(w["privacy_hidden"]).build())
        if(!test && !p.lock) {
            if(r.kind=="medication") notification.addAction(0,w["taken"],token(c,r,"taken"))
            if(r.kind=="water") notification.addAction(0,w["add_cup"],token(c,r,"water"))
            if(r.attempt<repeatFor(s,r).second) notification.addAction(0,w["snooze"],token(c,r,"snooze"))
        }
        return try {
            NotificationManagerCompat.from(c).notify(notificationId(c,r.id.substringBefore("~")),notification.build())
            finish("posted")
        } catch (_: SecurityException) { finish("blocked") }
    }
    @Synchronized fun receive(c:Context,r:Reminder,operation:String) {
        if (operation == "show" && saved(c, "scheduled").none { it.id == r.id && it.due == r.due }) return
        if(r.kind=="maintenance") {
            Store.get(c).refreshWidgets()
            reschedule(c)
            return
        }
        var state=Store.get(c).read(); val now=System.currentTimeMillis()
        if(r.kind=="care" && state.care.items.none{it.id==r.occurrence && !it.done && it.alert && it.time==r.original})return
        if(r.kind=="medication") {
            if(state.medications.none { it.id==r.medicationId && it.active && it.reminders }) return
            if(state.entries.any { it.kind=="dose" && it.occurrence==r.occurrence }) return
            val valid=plan(state,r.original-1000,r.original+1000).any { it.occurrence==r.occurrence }
            if(!valid) return
        }
        if(operation=="show") {
            if(prefs(c).getBoolean("fired_${r.id}",false)) return
            prefs(c).edit().putBoolean("fired_${r.id}",true).apply()
            // Do not prompt for stale medication reminders after an extended delivery delay.
            if(now-r.due>2*3_600_000L) {
                audit(c, r, "reminder_expired")
                reschedule(c)
                return
            }
            if(r.kind=="care" || !isQuiet(now,state.preferences) || (r.kind=="medication" && !state.preferences.medicationQuiet)) {
                post(c,r,state)
                if(r.kind=="medication" && r.attempt<repeatFor(state,r).second) snooze(c,r,state,automatic=true)
            } else audit(c, r, "reminder_quiet")
        } else {
            if(state.preferences.lock) return
            if(operation=="taken") {
                if(now-r.original>2*3_600_000L) return
                Store.get(c).update { Records.add(it,Entry(kind="dose",medicationId=r.medicationId,occurrence=r.occurrence,
                    planned=r.original,status="taken",note=it.medications.find { m->m.id==r.medicationId }?.let { m->"${m.name} · ${m.dose}" } ?: "")) }
                audit(c,r,"taken")
            } else if(operation=="water") {
                // Repeated taps on the same notification cannot add multiple cups.
                val actionKey="water-action_${r.id}"
                if(!prefs(c).getBoolean(actionKey,false)) {
                    Store.get(c).update { Records.add(it,Entry(id=actionKey,kind="water",value=it.preferences.cup.toDouble(),unit="mL")) }
                    prefs(c).edit().putBoolean(actionKey,true).apply()
                }
            } else if(operation=="snooze") snooze(c,r,state,false)
            NotificationManagerCompat.from(c).cancel(notificationId(c,r.id.substringBefore("~")))
        }
        reschedule(c)
    }
    private fun snooze(c:Context,r:Reminder,s:State,automatic:Boolean) {
        val policy=repeatFor(s,r)
        if(r.attempt>=policy.second) return
        val due=System.currentTimeMillis()+(if(automatic)policy.first else s.preferences.snoozeMinutes)*60_000L
        if(r.kind=="medication" && due-r.original>2*3_600_000L) return
        if(r.kind!="care" && isQuiet(due,s.preferences) && (r.kind!="medication" || s.preferences.medicationQuiet)) return
        val next=r.copy(id=r.id.substringBefore("~")+"~${r.attempt+1}",due=due,attempt=r.attempt+1)
        val list=saved(c,"oneshots").filterNot { it.id==next.id }+next
        prefs(c).edit().putString("oneshots",JSONArray(list.map { it.json() }).toString()).apply()
        if(!automatic) audit(c,r,"snoozed")
    }
    @Synchronized fun clearAll(c:Context) {
        saved(c,"scheduled").forEach { alarms(c).cancel(token(c,it)) }
        NotificationManagerCompat.from(c).cancelAll();prefs(c).edit().clear().commit()
    }
}

class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        Reminders.worker.execute { try {
            val value=intent.getStringExtra("reminder") ?: return@execute
            Reminders.receive(context,Reminder.read(JSONObject(value)),intent.getStringExtra("operation") ?: "show")
        } catch(e:Exception) { /* Data is not reset on a decryption or scheduling failure. */ } finally { pending.finish() } }
    }
}
class RescheduleReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        Reminders.worker.execute {
            try {
                val state = Store.get(context).read()
                runCatching { Store.get(context).refreshWidgets() }
                Reminders.reschedule(context, state)
            } catch (_: Exception) {
            } finally { pending.finish() }
        }
    }
}


