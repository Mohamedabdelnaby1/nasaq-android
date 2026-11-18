@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZoneId

@Composable fun SettingsDetail(repo:Repository,activity:MainActivity,backup:(String)->Unit,info:(String)->Unit,section:String) {
    if(section=="notifications"){ReminderHub(repo,activity);return}
    val s=repo.state;val p=s.preferences;val w=LocalWords.current
    var layoutEditor by remember{mutableStateOf(false)}
    var edit by remember {mutableStateOf<String?>(null)}
    var notificationExpanded by remember {mutableStateOf(false)}
    LaunchedEffect(activity.openNotifications) {if(activity.openNotifications){notificationExpanded=true;activity.openNotifications=false}}
    var auditExpanded by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(p.lastZone!=ZoneId.systemDefault().id) Panel(w["travel_hint"]) {
            Text(w["interval_travel"],style=MaterialTheme.typography.bodySmall)
            Button(onClick={repo.preferences{it.copy(lastZone=ZoneId.systemDefault().id)}}){Text(w["acknowledge"])}
        }
        if(section=="integrations")IntegrationPanel(repo,activity)
        if(section=="appearance")Panel(w["appearance"]) {
            ChoiceRow(w["language"],listOf("system","ar","en"),p.language,{when(it){"ar"->"العربية";"en"->"English";else->w["system"]}}){choice->repo.preferences{it.copy(language=choice)}}
            ChoiceRow(w["theme"],listOf("system","light","dark"),p.theme,{w[it]}){choice->repo.preferences{it.copy(theme=choice)}}
            ChoiceRow(w["palette"],listOf("mint","violet","coral","blue"),p.palette,{w[it]}){choice->repo.preferences{it.copy(palette=choice,dynamicColors=false)}}
            Toggle(w["dynamic"],p.dynamicColors,w["dynamic_hint"]){v->repo.preferences{it.copy(dynamicColors=v)}}
            Toggle(w["large_text"],p.largeText){v->repo.preferences{it.copy(largeText=v)}}
            Toggle(w["reduce_motion"],p.reduceMotion){v->repo.preferences{it.copy(reduceMotion=v)}}
            Toggle(w["adaptive_actions"], p.adaptive, w["adaptive_actions_body"]) { value -> repo.preferences { it.copy(adaptive = value) } }
            Toggle(w["haptics"],p.haptics,w["haptics_hint"]){v->repo.preferences{it.copy(haptics=v)}}
            TextButton(onClick={repo.preferences {it.copy(theme="system",palette="mint",dynamicColors=false,largeText=false,reduceMotion=false)}}){Text(w["reset_appearance"])}
            Text(w["reset_appearance_hint"],style=MaterialTheme.typography.bodySmall)
        }
        if(section=="home_cards")Panel(w["home_cards"]){HomeLayoutPreview(s);Button(onClick={layoutEditor=true},modifier=Modifier.fillMaxWidth()){Text(w["customise"])}}
        if(section=="personal")Panel(w["personal"]) {
            SettingsLink(w["your_name"]){edit="personal"}
            SettingsLink(w["targets"]){edit="targets"}
            SettingsLink(w["daily_litres"]){edit="water"}
            SettingsLink(w["sleep_routine"]){edit="quiet"}
            SettingsLink(w["sick_days"]){edit="sick"}
            ChoiceRow(w["unit"],listOf("mg/dL","mmol/L"),p.unit,{it}){choice->repo.preferences{it.copy(unit=choice)}}
        }
        if(section=="privacy")Panel(w["privacy"]) {
            Text(w["local_privacy"],style=MaterialTheme.typography.bodyMedium)
            Toggle(w["lock_app"],p.lock,w["lock_hint"]){v->if(!v || activity.canLock())repo.preferences{it.copy(lock=v)} else activity.message("set_device_lock")}
            Toggle(w["secure_screen"],p.secureScreen){v->repo.preferences{it.copy(secureScreen=v)}}
            TextButton(onClick={info("privacy")}){Text(w["read_more"])}
        }
        if(section=="backup")Panel(w["backup"]) {
            Button(onClick={backup("create")},modifier=Modifier.fillMaxWidth()){Text(w["backup_create"])}
            OutlinedButton(onClick={backup("restore")},modifier=Modifier.fillMaxWidth()){Text(w["backup_restore"])}
            Text(w["private_backup"],style=MaterialTheme.typography.bodySmall)
        }
        if(section=="about")Panel(w["about"]) {
            Text(w["medical_notice"],style=MaterialTheme.typography.bodySmall)
            Text(w.f("version","version" to BuildConfig.VERSION_NAME),style=MaterialTheme.typography.labelMedium)
            Text(w["developer_credit"],style=MaterialTheme.typography.titleMedium)
            SettingsLink(w["developer_facebook"]) { activity.openSource("https://fb.com/mohamedabdelnaby190") }
            Text("Material 3 · Tajawal · Android 8+",style=MaterialTheme.typography.labelMedium)
            TextButton(onClick={activity.openSource("https://www.cdc.gov/diabetes/treatment/treatment-low-blood-sugar-hypoglycemia.html")}){Text("CDC · "+w["sources"])}
            TextButton(onClick={activity.openSource("https://www.nhs.uk/conditions/dehydration/")}){Text("NHS · "+w["sources"])}
        }
    }
    if(layoutEditor){
        var layouts by remember{mutableStateOf(s.care.layouts)}
        HomeLayoutEditor(s,{layoutEditor=false},{next->repo.saveLayout(next,layouts);layoutEditor=false},{layouts=it})
    }
    edit?.let {kind->PreferencesEditor(kind,p,onDismiss={edit=null},onSave={next->repo.preferences{next};edit=null})}
}

@Composable private fun SettingsLink(title:String,onClick:()->Unit) {
    TextButton(onClick=onClick,contentPadding=PaddingValues(vertical=10.dp,horizontal=0.dp),modifier=Modifier.fillMaxWidth()) {
        Text(title,Modifier.weight(1f),color=MaterialTheme.colorScheme.primary)
        Glyph("arrow",Modifier.size(20.dp))
    }
}
@Composable private fun StatusLine(title:String,enabled:Boolean,onFix:()->Unit) {
    val w=LocalWords.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(title);Text(w[if(enabled)"enabled" else "disabled"],style=MaterialTheme.typography.labelMedium,color=if(enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}
        if(!enabled) TextButton(onClick=onFix){Text(w["allow"])}
    }
}

@Composable fun PreferencesEditor(kind:String,p:Preferences,onDismiss:()->Unit,onSave:(Preferences)->Unit) {
    val w=LocalWords.current
    var name by remember{mutableStateOf(p.name)};var goal by remember{mutableStateOf((p.waterGoal/1000.0).toString())}
    var cup by remember{mutableStateOf(p.cup.toString())};var waterEvery by remember{mutableStateOf(p.waterEvery.toString())}
    var lower by remember{mutableStateOf(com.yawmi.diabetes.core.Planner.fromMg(p.lower,p.unit).toString())}
    var upper by remember{mutableStateOf(com.yawmi.diabetes.core.Planner.fromMg(p.upper,p.unit).toString())}
    var source by remember{mutableStateOf(p.targetNote)};var quietStart by remember{mutableStateOf(p.quietStart)}
    var quietEnd by remember{mutableStateOf(p.quietEnd)};var quietMed by remember{mutableStateOf(p.medicationQuiet)}
    var glucoseTimes by remember{mutableStateOf(p.glucoseTimes.joinToString(","))};var after by remember{mutableStateOf(p.afterMealMinutes.toString())}
    var snooze by remember{mutableStateOf(p.snoozeMinutes.toString())}
    var repeat by remember{mutableStateOf(p.maxRepeats.toString())};var delay by remember{mutableStateOf(p.repeatMinutes.toString())}
    var sick by remember{mutableStateOf(p.sickPlan)};var phone by remember{mutableStateOf(p.carePhone)}
    var invalid by remember{mutableStateOf(false)}
    EditorShell(w[when(kind){"personal"->"personal";"targets"->"targets";"water"->"water";"quiet"->"quiet";"glucose"->"glucose_reminders";"repeat"->"notifications";else->"sick_days"}],onDismiss,onSave={
        try {
            val next=when(kind) {
                "personal"->p.copy(name=name.trim())
                "targets"->p.copy(lower=com.yawmi.diabetes.core.Planner.toMg(com.yawmi.diabetes.core.Planner.parseNumber(lower),p.unit),upper=com.yawmi.diabetes.core.Planner.toMg(com.yawmi.diabetes.core.Planner.parseNumber(upper),p.unit),targetNote=source)
                "water"->p.copy(waterGoal=litresToMl(goal),cup=whole(cup),waterEvery=whole(waterEvery))
                "quiet"->p.copy(quietStart=parseTimes(quietStart).single(),quietEnd=parseTimes(quietEnd).single(),medicationQuiet=quietMed)
                "glucose"->p.copy(glucoseTimes=parseTimes(glucoseTimes),afterMealMinutes=whole(after))
                "repeat"->p.copy(maxRepeats=whole(repeat),repeatMinutes=whole(delay),snoozeMinutes=whole(snooze))
                else->p.copy(sickPlan=sick,carePhone=phone.trim())
            };Codec.validate(State(preferences=next));onSave(next)
        }catch(e:Exception){invalid=true}
    }) {
        if(invalid) Notice(w["invalid"],true)
        when(kind) {
            "personal"->Field(w["your_name"],name,{name=it})
            "targets"->{Field(w["lower"]+" · "+p.unit,lower,{lower=it},true);Field(w["upper"]+" · "+p.unit,upper,{upper=it},true);Field(w["target_source"],source,{source=it});Notice(w["target_hint"])}
            "water"->{Field(w["daily_litres"],goal,{goal=it},true);Field(w["cup_size"],cup,{cup=it},true);if(!p.waterSmart)Field(w["minutes"],waterEvery,{waterEvery=it},true);Notice(w["goal_hint"])}
            "quiet"->{TimesEditor(w["sleep_time"],quietStart,{quietStart=it},single=true);TimesEditor(w["wake_time"],quietEnd,{quietEnd=it},single=true);Toggle(w["quiet_meds"],quietMed){quietMed=it};Notice(w["notification_hint"])}
            "glucose"->{TimesEditor(w["time"],glucoseTimes,{glucoseTimes=it});Field(w["minutes"]+" · "+w["after_meal_reminders"],after,{after=it},true);Notice(w["aftermeal_note"])}
            "repeat"->{Field(w["snooze_minutes"],snooze,{snooze=it},true);Field(w["repeat_count"],repeat,{repeat=it},true);Field(w["repeat_delay"],delay,{delay=it},true);Notice(w["repeat_hint"])}
            else->{Field(w["sick_days"],sick,{sick=it},multiline=true);Field(w["care_phone"],phone,{phone=it});Notice(w["sick_hint"])}
        }
    }
}
