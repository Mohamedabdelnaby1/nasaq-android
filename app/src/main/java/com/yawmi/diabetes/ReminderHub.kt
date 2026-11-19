@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*

data class RoutineEvent(val time:Long,val kind:String,val reminder:Reminder?=null)
fun routineEvents(s:State,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):List<RoutineEvent>{
    val day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();val until=now+86400000
    val reminders=Reminders.plan(s,now,until).take(10).map{RoutineEvent(it.due,it.kind,it)}
    val routine=listOf("wake" to s.preferences.quietEnd,"sleep" to s.preferences.quietStart).map{(kind,raw)->
        var stamp=day.atTime(LocalTime.parse(raw)).atZone(zone).toInstant().toEpochMilli()
        if(stamp<now)stamp=day.plusDays(1).atTime(LocalTime.parse(raw)).atZone(zone).toInstant().toEpochMilli()
        RoutineEvent(stamp,kind)
    }
    return (reminders+routine).sortedBy{it.time}
}

@Composable fun ReminderHub(repo:Repository,activity:MainActivity,openWaterSettings:()->Unit,openMedications:(Medication?)->Unit){
    val s=repo.state;val w=LocalWords.current
    var edit by remember{mutableStateOf<String?>(null)}
    var audit by remember{mutableStateOf(false)}
    var care by remember{mutableStateOf(false)}
    var chooseTest by remember { mutableStateOf(false) }
    var editError by remember(edit) { mutableStateOf<String?>(null) }
    val lastTest = s.audit.lastOrNull { it.id == "test" }
    val suggestion=remember(s.revision,s.preferences){waterSuggestion(s)}
    val allowed=remember(activity.permissionRevision){Reminders.permitted(activity)}
    val precise=remember(activity.permissionRevision){Reminders.exact(activity)}
    val scheduled = remember(activity.permissionRevision, s.revision) { Reminders.upcoming(activity) }
    val muted = remember(scheduled, activity.permissionRevision) { scheduled.firstOrNull { !Reminders.channelEnabled(activity, Reminders.channelFor(s, it)) } }
    ReminderHubContent(s,allowed,precise,edit={edit=it},event={e->
        if(e.kind=="care")care=true
        else if(e.reminder?.kind=="medication")openMedications(s.medications.find{it.id==e.reminder.medicationId})
        else edit=if(e.kind in setOf("wake","sleep"))"quiet"else e.kind
    },permission={activity.notificationPermission()},exact={activity.exactPermission()},test={chooseTest=true},medications={openMedications(null)},
        suggestion=suggestion,onApply={
            if(suggestion!=null)repo.preferences{
                val wake=LocalTime.parse(it.quietEnd)
                val offset=Math.floorMod(suggestion.time.toSecondOfDay()-wake.toSecondOfDay(),86400)/60
                it.copy(waterOffset=offset.coerceIn(0,240))
            }
        },onReset={repo.preferences{it.copy(waterOffset=0)}},onRepair={activity.reschedule()},
        onBattery={activity.batterySettings()},onCare={care=true}, scheduled = scheduled,
        mutedKind = muted?.kind, testResult = lastTest?.status ?: activity.reminderTestResult,
        testKind = lastTest?.kind, onTestSeen = if (lastTest?.status == "posted" && !repo.busy) { seen ->
            repo.change { fresh ->
                val index = fresh.audit.indexOfLast { it.id == "test" }
                if (fresh.audit.getOrNull(index) != lastTest) fresh else fresh.copy(audit = fresh.audit.toMutableList().apply {
                    this[index] = lastTest.copy(status = if (seen) "test_seen" else "test_missing")
                })
            }
        } else null, onAcknowledgeZone = { repo.preferences { it.copy(lastZone = ZoneId.systemDefault().id) } })
    if (chooseTest) BrandedDialog(onDismissRequest = { chooseTest = false },
        title = { Text(w["reminder_test_channel"]) },
        text = {
            Column {
                listOf("glucose", "water", "medication", "symptom").forEach { kind ->
                    SettingsRow(kind, w[if (kind == "symptom") "followup" else kind]) {
                        chooseTest = false
                        activity.testNotification(kind)
                    }
                }
            }
        }, confirmButton = { TextAction({ chooseTest = false }) { Text(w["cancel"]) } })
    if(care)CareTools(s,{care=false}){change->repo.change(block=change)}
    edit?.let { kind ->
        val onSave: ((Preferences) -> Preferences) -> Unit = { update ->
            if (!repo.busy) repo.change(onSuccess = { edit = null }, onFailure = { editError = it }) {
                it.copy(preferences = update(it.preferences))
            }
        }
        when (kind) {
            "water", "glucose" -> ReminderScheduleEditor(kind, s.preferences, { edit = null }, onSave,
                channel = { activity.channel(kind) }, test = { activity.testNotification(kind) },
                openWaterSettings = { edit = null; openWaterSettings() }, saving = repo.busy, saveError = editError)
            "audit" -> LaunchedEffect(Unit) { audit = true; edit = null }
            else -> PreferencesEditor(kind, s.preferences, { edit = null },
                saving = repo.busy, saveError = editError, onSave = onSave)
        }
    }

    if(audit)BrandedDialog(onDismissRequest={audit=false},title={Text(w["notification_log"])},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
        if(s.audit.isEmpty())Text(w["no_audit"])
        s.audit.takeLast(20).reversed().forEach{a->Text(dateText(a.time,w)+" "+timeText(a.time,w)+" · "+w[if(a.kind=="medication")"medication"else a.kind]+" · "+w[a.status],style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={TextAction(onClick={audit=false}){Text(w["close"])}})
}

@Composable fun ReminderHubContent(s:State,allowed:Boolean,precise:Boolean,edit:(String)->Unit,event:(RoutineEvent)->Unit,permission:()->Unit,exact:()->Unit,test:()->Unit,medications:()->Unit,
    suggestion:WaterSuggestion?=null,onApply:()->Unit={},onReset:()->Unit={},onRepair:()->Unit={},onBattery:()->Unit={},onCare:()->Unit={}, scheduled: List<Reminder> = emptyList(), mutedKind: String? = null,
    testResult: String? = null, testKind: String? = null, onTestSeen: ((Boolean) -> Unit)? = null, onAcknowledgeZone: () -> Unit = {}){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60000);now=System.currentTimeMillis()}}
    val events=remember(s.revision,s.preferences,now/60000){routineEvents(s,now)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),verticalArrangement=Arrangement.spacedBy(16.dp)){
        ScreenTitle(w["notification_hub"])
        if (s.preferences.lastZone != ZoneId.systemDefault().id) NasaqBoard {
            Notice(w["travel_hint"])
            TextAction(onAcknowledgeZone) { Text(w["acknowledge"]) }
        }
        Surface(color=LocalBrand.current,shape=RoundedCornerShape(24.dp)){Box{Pattern(Modifier.matchParentSize());Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(w["reminder_readiness"],style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color.White)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                ReadinessTile(w["permission"],allowed,Modifier.weight(1f),permission)
                ReadinessTile(w["exact"],precise,Modifier.weight(1f),exact)
            }
            ActionButton(onClick=test,colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Forest),shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()){Text(w["test_notification"])}
        }}}
        NasaqBoard {
            SectionHeading(w["reminder_status"])
            val queue = scheduled.filter { it.due >= now }
            val next = queue.firstOrNull()
            Text(if (next != null) w.f("reminder_next_scheduled", "kind" to w[next.kind],
                "time" to (dateText(next.due, w) + " · " + timeText(next.due, w))) else w["reminder_no_schedule"],
                style = MaterialTheme.typography.titleSmall)
            if (!allowed) Text(w["reminder_no_permission"], color = c.error, style = MaterialTheme.typography.bodySmall)
            if (!precise) Text(w["reminder_approximate"], style = MaterialTheme.typography.bodySmall)
            if (mutedKind != null) Text(w[mutedKind] + " · " + w["reminder_channel_disabled"], color = c.error)
            if (testResult != null) {
                val result = w[if (testResult == "posted") "notification_test_sent" else testResult]
                val kind = testKind?.let { w[if (it == "symptom") "followup" else it] }
                Text(listOfNotNull(kind, result).joinToString(" · "))
            }
            if (onTestSeen != null) {
                Text(w["reminder_test_question"], style = MaterialTheme.typography.bodyMedium)
                TextAction({ onTestSeen(true) }) { Text(w["reminder_test_yes"]) }
                TextAction({ onTestSeen(false) }) { Text(w["reminder_test_no"]) }
            }
            val latest = s.audit.lastOrNull { it.id != "test" }
            if (latest != null) Text(w.f("reminder_latest_result", "status" to w[latest.status], "time" to timeText(latest.time, w)),
                style = MaterialTheme.typography.labelMedium)
            val deliveryIssues = s.audit.asReversed().asSequence()
                .filter { it.id != "test" && it.status in setOf("blocked", "reminder_expired", "reminder_quiet",
                    "reminder_channel_disabled", "reminder_no_permission") }.take(3).toList()
            if (deliveryIssues.isNotEmpty()) {
                Text(w["reminder_recent_issues"], style = MaterialTheme.typography.titleSmall)
                deliveryIssues.forEach { attempt ->
                    Text(dateText(attempt.time, w) + " · " + timeText(attempt.time, w) + " · " + w[attempt.status],
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Panel{
            SettingsRow("water",w["water_reminders"],w[if(s.preferences.waterEnabled)"enabled"else"disabled"]){edit("water")}
            SettingsRow("glucose",w["glucose_reminders"],w[if(s.preferences.glucoseEnabled)"enabled"else"disabled"]){edit("glucose")}
            SettingsRow("medication",w["medications"],w["per_medicine_policy"]){medications()}
            SettingsRow("care",w["care_tools"],w["care_tools_subtitle"],onClick=onCare)
        }
        if(suggestion!=null || s.preferences.waterOffset>0)Panel(w["adaptive_water"]){
            if(suggestion!=null){
                Text(w.f("water_pattern","time" to clockText(suggestion.time, w),"days" to number(suggestion.respondingDays.toDouble(),w)))
                TonalAction(onClick=onApply){Text(w["apply_water_time"])}
            }
            if(s.preferences.waterOffset>0)TextAction(onClick=onReset){Text(w["reset_water_time"])}
        }
        SectionHeading(w["day_timeline"],w["sleep_card"]){edit("quiet")}
        Panel{
            Text(w["timeline_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            events.forEachIndexed{index,e->
                val kind=when(e.kind){"wake"->"sun";"sleep"->"moon";else->e.kind}
                Row(Modifier.fillMaxWidth().clickable{event(e)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Surface(shape=CircleShape,color=if(e.kind=="water")Aqua.copy(alpha=.12f)else c.primaryContainer){Glyph(kind,Modifier.padding(9.dp).size(18.dp),if(e.kind=="water")Aqua else c.primary)}
                        if(index<events.lastIndex)Box(Modifier.width(1.dp).height(13.dp).background(c.outlineVariant))
                    }
                    Column(Modifier.weight(1f)){
                        Text(when(e.kind){"wake"->w["wake_time"];"sleep"->w["sleep_time"];"medication"->s.medications.find{it.id==e.reminder?.medicationId}?.name ?: w["medication"];"care"->s.care.items.find{it.id==e.reminder?.occurrence}?.title ?: w["care_tools"];else->w[e.kind]},style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                        Text(dateText(e.time,w),style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
                    }
                    Text(timeText(e.time,w),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold);Glyph("edit",Modifier.size(16.dp))
                }
            }
        }
        Panel{
            SettingsRow("bell",w["repeat_and_snooze"],w["policy_shared_hint"]){edit("repeat")}
            SettingsRow("log",w["notification_log"]){edit("audit")}
            SettingsRow("bell",w["repair_reminders"],onClick=onRepair)
            SettingsRow("settings",w["battery_settings"],onClick=onBattery)
        }
        Text(w["notification_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
    }
}

@Composable private fun ReadinessTile(title:String,ready:Boolean,modifier:Modifier,onClick:()->Unit){
    Surface(onClick=onClick,color=Color.White.copy(alpha=.08f),shape=RoundedCornerShape(14.dp),modifier=modifier){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Glyph(if(ready)"check"else"info",Modifier.size(20.dp),Gold)
        Text(title,style=MaterialTheme.typography.labelMedium,color=Color.White)
        Text(LocalWords.current[if(ready)"enabled"else"allow"],style=MaterialTheme.typography.labelSmall,color=Color(0xFFD5E6DB))
    }}
}

@Composable fun ReminderScheduleEditor(kind:String,p:Preferences,onDismiss:()->Unit,onSave:((Preferences)->Preferences)->Unit,channel:()->Unit,test:()->Unit,openWaterSettings:()->Unit,saving:Boolean=false,saveError:String?=null){
    val water=kind=="water";val w=LocalWords.current
    var enabled by remember{mutableStateOf(if(water)p.waterEnabled else p.glucoseEnabled)}
    var days by remember{mutableStateOf(if(water)p.waterDays else p.glucoseDays)}
    var smart by remember{mutableStateOf(p.waterSmart)}
    var interval by remember{mutableStateOf(p.waterEvery.toString())};var times by remember{mutableStateOf(p.glucoseTimes.joinToString(","))}
    var after by remember{mutableStateOf(p.afterMealEnabled)};var delay by remember{mutableStateOf(p.afterMealMinutes.toString())}
    var invalid by remember{mutableStateOf(false)}
    EditorShell(w[if(water)"water_reminders"else"glucose_reminders"],onDismiss,{
        try{
            val update: (Preferences) -> Preferences = if (water) {
                val intervalMinutes = if (smart) p.waterEvery else whole(interval)
                val reminderDays = days
                val isEnabled = enabled
                val adaptive = smart
                val apply: (Preferences) -> Preferences = { current ->
                    require(!isEnabled || !adaptive || current.waterGoal > 0)
                    current.copy(waterEnabled = isEnabled, waterDays = reminderDays, waterSmart = adaptive,
                        waterEvery = intervalMinutes)
                }
                apply
            } else {
                val reminderTimes = parseTimes(times)
                val reminderDays = days
                val isEnabled = enabled
                val afterMeals = after
                val afterMealDelay = if (afterMeals) whole(delay) else p.afterMealMinutes
                val apply: (Preferences) -> Preferences = {
                    it.copy(glucoseEnabled = isEnabled, glucoseDays = reminderDays, glucoseTimes = reminderTimes,
                        afterMealEnabled = afterMeals, afterMealMinutes = afterMealDelay)
                }
                apply
            }
            require(!enabled || days.isNotEmpty())
            Codec.validate(State(preferences = update(p)))
            onSave(update)

        }catch(e:Exception){invalid=true}
    }, saveEnabled = !saving){
        if (saveError != null) Notice(w[saveError], true)
        Toggle(w["enabled"],enabled){enabled=it}
        Text(w["reminder_days"],style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..7).forEach{day->ChoiceChip(selected=day in days,onClick={days=if(day in days)days-day else days+day},label={Text(w[dayKey(day)])},shape=RoundedCornerShape(50))}}
        if(water){
            Toggle(w["smart_water_reminders"],smart,w["smart_water_hint"]){smart=it}
            SettingsRow("water", w["water_goal_and_cup"], onClick = openWaterSettings)
            if(!smart)Field(w["minutes"],interval,{interval=it;invalid=false},true)
            else Text(w["wake_time"]+" · "+clockText(p.quietEnd, w)+" · "+w["sleep_time"]+" · "+clockText(p.quietStart, w),style=MaterialTheme.typography.bodySmall)
        }else{
            TimesEditor(w["time"],times,{times=it;invalid=false})
            Toggle(w["after_meal_reminders"],after){after=it}
            if(after)Field(w["minutes"],delay,{delay=it;invalid=false},true)
        }
        Panel{SettingsRow("bell",w["channel"],w["notification_sound_note"],onClick=channel);OutlineAction(onClick=test,modifier=Modifier.fillMaxWidth()){Text(w["test_notification"])}}
        if(invalid)Notice(w["schedule_error"],true)
        Text(w["policy_shared_hint"],style=MaterialTheme.typography.bodySmall)
    }
}
