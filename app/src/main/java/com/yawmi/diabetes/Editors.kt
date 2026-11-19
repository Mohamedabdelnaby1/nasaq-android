@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yawmi.diabetes.core.Planner
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun EditorShell(title:String,onDismiss:()->Unit,onSave:()->Unit,saveLabel:String=LocalWords.current["save"],saveEnabled:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    val w=LocalWords.current
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(containerColor = c.background, topBar = {
            Column(Modifier.statusBarsPadding()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
                    SectionBanner("edit", title, action = {
                        IconAction(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                            Glyph("close", Modifier.size(22.dp), LocalContentColor.current)
                        }
                    })
                }
            }
        }, bottomBar = {
            Surface(color = c.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    HorizontalDivider(color = c.outlineVariant)
                    ActionButton(onClick = onSave, enabled = saveEnabled,
                        modifier = Modifier.fillMaxWidth().padding(14.dp).heightIn(min = 50.dp)) {
                        Glyph("check", Modifier.size(19.dp), LocalContentColor.current)
                        Spacer(Modifier.width(9.dp))
                        Text(saveLabel)
                    }
                }
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenGutters),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reveal { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
                }
            }
        }
    }
}

@Composable fun Field(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false,multiline:Boolean=false) {
    OutlinedTextField(value=value,onValueChange={if(it.length<=4000)onChange(it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),shape=ControlShape,
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh,unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow),
        singleLine=!multiline,minLines=if(multiline) 3 else 1,keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun TimeChoiceDialog(initial:LocalTime,onDismiss:()->Unit,onPicked:(LocalTime)->Unit) {
    val w=LocalWords.current
    val state=rememberTimePickerState(initialHour=initial.hour,initialMinute=initial.minute,is24Hour=false)
    val compact=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f ||
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<600
    BrandedDialog(onDismissRequest=onDismiss,title={Text(w["time"])},text={
        if(compact)TimeInput(state=state)else TimePicker(state=state)
    },confirmButton={TextAction(onClick={onPicked(LocalTime.of(state.hour,state.minute))}){Text(w["confirm"])}},
        dismissButton={TextAction(onClick=onDismiss){Text(w["cancel"])}})
}

@Composable fun StampPicker(value:Long,onChange:(Long)->Unit,allowFuture:Boolean=false,label:String=LocalWords.current["date_time"]) {
    val w=LocalWords.current
    val z=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    var showDate by remember{mutableStateOf(false)}
    var showTime by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlineAction(onClick={showDate=true}){Text(dateText(value,w))}
            OutlineAction(onClick={showTime=true}){Text(timeText(value,w))}
        }
    }
    if(showDate) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=z.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates=object:SelectableDates {
                override fun isSelectableDate(utcTimeMillis:Long):Boolean =
                    allowFuture || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            })
        DatePickerDialog(onDismissRequest={showDate=false},confirmButton={
            TextAction(onClick={
                picker.selectedDateMillis?.let{selected->
                    val date=Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                    onChange(date.atTime(z.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                };showDate=false
            },enabled=picker.selectedDateMillis!=null){Text(w["confirm"])}
        },dismissButton={TextAction(onClick={showDate=false}){Text(w["cancel"])}}){DatePicker(state=picker)}
    }
    if(showTime)TimeChoiceDialog(z.toLocalTime(),onDismiss={showTime=false},onPicked={
        onChange(z.toLocalDate().atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());showTime=false
    })
}

@Composable fun TimesEditor(label:String,raw:String,onChange:(String)->Unit,single:Boolean=false) {
    val w=LocalWords.current
    val times=runCatching{parseTimes(raw)}.getOrDefault(emptyList())
    var picking by remember{mutableStateOf(false)}
    var editingIndex by remember{mutableStateOf<Int?>(null)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            times.forEachIndexed {index,time->
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    OutlineAction(onClick={editingIndex=index;picking=true}){Text(clockText(time, w))}
                    if(!single && times.size>1)TextAction(onClick={onChange(times.filterIndexed{i,_->i!=index}.joinToString(","))}){Text(w["delete"])}
                }
            }
            if((!single && times.size<12) || times.isEmpty())TextAction(onClick={editingIndex=null;picking=true}){Text(w["add_time"])}
        }
    }
    if(picking) {
        val initial=times.getOrNull(editingIndex ?: -1)?.let{LocalTime.parse(it)} ?: LocalTime.of(8,0)
        TimeChoiceDialog(initial,onDismiss={picking=false},onPicked={chosen->
            val formatted=chosen.format(DateTimeFormatter.ofPattern("HH:mm"))
            val next=times.toMutableList()
            if(single){next.clear();next+=formatted}else if(editingIndex==null)next+=formatted else next[editingIndex!!]=formatted
            onChange(next.distinct().sorted().joinToString(","));picking=false
        })
    }
}

fun parseTimes(raw:String):List<String> {
    val values=raw.split(Regex("[,،;]")).map {text->
        val parts=text.trim().split(':');require(parts.size==2)
        val hour=Planner.parseNumber(parts[0]);val minute=Planner.parseNumber(parts[1]);require(hour%1.0==0.0 && minute%1.0==0.0)
        LocalTime.of(hour.toInt(),minute.toInt()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.distinct().sorted();require(values.size in 1..12);return values
}
fun whole(raw:String):Int {val value=Planner.parseNumber(raw);require(value%1.0==0.0);return value.toInt()}

@Composable fun EntryEditor(kind:String,existing:Entry?,s:State,onDismiss:()->Unit,onSave:(Entry)->Unit, initialTime: Long? = null, draft: Entry? = null, saving: Boolean = false, saveError: String? = null) {
    val w=LocalWords.current
    val initial = existing ?: draft
    val recordId=remember {initial?.id ?: java.util.UUID.randomUUID().toString()}
    var value by remember {mutableStateOf(initial?.value?.takeIf {it!=0.0}?.toString() ?: "")}
    var second by remember {mutableStateOf(initial?.second?.takeIf {it!=0.0}?.toString() ?: "")}
    var time by remember {mutableLongStateOf(initial?.time ?: initialTime ?: System.currentTimeMillis())}
    var context by remember {mutableStateOf(initial?.context ?: when(kind){"meal"->"breakfast";"symptom"->"diarrhea";else->""})}
    var note by remember {mutableStateOf(initial?.note ?: "")}
    var status by remember {mutableStateOf(initial?.status ?: if(kind=="symptom")"mild" else "taken")}
    var medicationId by remember {mutableStateOf(initial?.medicationId ?: s.medications.firstOrNull {it.active}?.id.orEmpty())}
    var invalid by remember {mutableStateOf(false)}
    fun prepare():Entry {
        require(time<=System.currentTimeMillis()+60_000)
        val n=if(value.isBlank())0.0 else Planner.parseNumber(value)
        val n2=if(second.isBlank())0.0 else Planner.parseNumber(second)
        when(kind) {
            "meal" -> require(n in 0.0..2000.0 && note.isNotBlank())
            "symptom" -> require(n in 0.0..1000.0)
            "weight" -> require(n in 1.0..600.0)
            "pressure" -> require(n in 20.0..400.0 && n2 in 10.0..300.0 && n>n2)
            "a1c" -> require(n in 1.0..30.0)
            "dose" -> require(medicationId.isNotBlank())
        }
        val m=s.medications.find {it.id==medicationId}
        return Entry(id=recordId,kind=kind,time=time,value=n,second=n2,
            unit=when(kind){"meal"->"g";"weight"->"kg";"a1c"->"%";"pressure"->"mmHg";else->""},
            context=context,note=if(kind=="dose" && existing==null) "${m?.name.orEmpty()} · ${m?.dose.orEmpty()}${if(note.isNotBlank()) " · $note" else ""}" else note,
            medicationId=medicationId,status=status,created=initial?.created ?: System.currentTimeMillis(),
            occurrence=initial?.occurrence.orEmpty(),planned=initial?.planned ?: 0L)
    }
    EditorShell(w[if(kind=="dose")"record_dose" else kind],onDismiss,onSave={
        try {onSave(prepare())}catch(e:Exception){invalid=true}
    }, saveEnabled = !saving) {
        if (saveError != null) Notice(w[saveError], true)
        if(invalid) Notice(w["invalid"],true)
        when(kind) {
            "meal" -> {
                ChoiceRow(w["meal_type"],listOf("breakfast","lunch","dinner","snack"),context,{w[it]}){context=it}
                Field(w["meal_description"],note,{note=it},multiline=true)
                Field(w["carbs"],value,{value=it},true)
                Notice(w["meal_time_hint"])
            }
            "symptom" -> {
                ChoiceRow(w["symptom"],listOf("diarrhea","vomiting","dizziness","thirst","other_symptom"),context,{w[it]}){context=it}
                Field(w["episodes"],value,{value=it},true)
                ChoiceRow(w["severity"],listOf("mild","moderate","severe"),status,{w[it]}){status=it}
                Notice(w["urgent_body"],true);Text(w["symptom_hint"],style=MaterialTheme.typography.bodySmall)
            }
            "dose" -> {
                if (initial?.occurrence.isNullOrBlank()) ChoiceRow(w["medication"],s.medications.filter {it.active}.map {it.id},medicationId,{id->s.medications.find {it.id==id}?.name.orEmpty()}){medicationId=it}
                ChoiceRow(w["status"],listOf("taken","skipped"),status,{w[it]}){status=it}
                Notice(w["manual_hint"])
                DoseTrackingReference(s)
            }
            "pressure" -> {Field(w["systolic"],value,{value=it},true);Field(w["diastolic"],second,{second=it},true)}
            else -> Field(w["value"]+" · "+when(kind){"weight"->w["kg"];"a1c"->"%";else->""},value,{value=it},true)
        }
        StampPicker(time,{time=it})
        if(kind!="meal") Field(w["notes"],note,{note=it},multiline=true)
        if(kind=="symptom") Text(s.preferences.sickPlan.ifBlank {w["sick_hint"]},style=MaterialTheme.typography.bodySmall)
    }

}

@Composable fun MedicationEditor(existing:Medication?,p:Preferences,onDismiss:()->Unit,onSave:(Medication)->Unit,saving:Boolean=false,saveError:String?=null) {
    val w=LocalWords.current
    val medicationId=remember {existing?.id ?: java.util.UUID.randomUUID().toString()}
    var name by remember {mutableStateOf(existing?.name.orEmpty())};var strength by remember {mutableStateOf(existing?.strength.orEmpty())}
    var dose by remember {mutableStateOf(existing?.dose.orEmpty())};var mode by remember {mutableStateOf(existing?.mode ?: "daily")}
    var times by remember {mutableStateOf(existing?.times?.joinToString(",") ?: "08:00")}
    var days by remember {mutableStateOf(existing?.weekdays ?: setOf(1))};var hours by remember {mutableStateOf((existing?.intervalHours ?: 12).toString())}
    var anchor by remember {mutableLongStateOf(existing?.anchor ?: System.currentTimeMillis())}
    var relation by remember {mutableStateOf(existing?.relation ?: "none")};var meal by remember {mutableStateOf(existing?.meal ?: "breakfast")}
    var offset by remember {mutableStateOf((existing?.offsetMinutes ?: 0).toString())};var planned by remember {mutableStateOf(existing?.plannedTime ?: "08:00")}
    var instructions by remember {mutableStateOf(existing?.instructions.orEmpty())};var active by remember {mutableStateOf(existing?.active ?: true)}
    var reminders by remember {mutableStateOf(existing?.reminders ?: true)}
    var stock by remember {mutableStateOf(existing?.stock?.takeIf {it>=0}?.toString().orEmpty())}
    var perDose by remember {mutableStateOf((existing?.stockPerDose ?: 1).toString())};var refill by remember {mutableStateOf((existing?.refillAt ?: 5).toString())}
    var customAlert by remember {mutableStateOf(existing?.customAlert ?: false)}
    var overrideRepeat by remember {mutableStateOf((existing?.repeatCount ?: -1)>=0)}
    var repeatCount by remember {mutableStateOf((existing?.repeatCount?.takeIf{it>=0} ?: p.maxRepeats).toString())}
    var repeatMinutes by remember {mutableStateOf((existing?.repeatMinutes?.takeIf{it>=5} ?: p.repeatMinutes).toString())}
    var advanced by remember {mutableStateOf(false)};var invalid by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf<Medication?>(null)}
    fun build():Medication=Medication(medicationId,name.trim(),strength.trim(),dose.trim(),mode,
        parseTimes(times),days,whole(hours),anchor,meal,relation,whole(offset),parseTimes(planned).single(),instructions,active,reminders,
        if(stock.isBlank())-1 else whole(stock),whole(perDose),whole(refill),
        if(existing==null || existing.stock!=(if(stock.isBlank())-1 else whole(stock)))System.currentTimeMillis()else existing.stockEpoch,customAlert,if(overrideRepeat)whole(repeatMinutes)else -1,if(overrideRepeat)whole(repeatCount)else -1).also {m->Codec.validate(State(medications=listOf(m)))}
    EditorShell(if(existing==null)w["add"]+" "+w["medication"] else w["edit"]+" "+existing.name,onDismiss,onSave={try{onSave(build())}catch(e:Exception){invalid=true}},saveEnabled=!saving) {
        if (saveError != null) Notice(w[saveError], true)
        if(invalid) Notice(w["invalid"],true)
        MedicineScan { name=it }
        Field(w["medicine_name"],name,{name=it});Field(w["strength"],strength,{strength=it});Field(w["dose_text"],dose,{dose=it})
        ChoiceRow(w["schedule"],listOf("daily","weekly","interval","meal"),mode,{w[if(it=="meal")"meal_linked" else it]}) {
            mode=it;if(it=="meal" && relation=="none")relation="with"
        }
        when(mode) {
            "daily","weekly" -> TimesEditor(w["time"],times,{times=it})
            "interval" -> {Field(w["hours"],hours,{hours=it},true);StampPicker(anchor,{anchor=it},true,w["anchor"]);Text(w["interval_travel"],style=MaterialTheme.typography.bodySmall)}
            "meal" -> {
                ChoiceRow(w["meal_type"],listOf("breakfast","lunch","dinner","snack"),meal,{w[it]}){meal=it}
                ChoiceRow(w["food_relation"],listOf("before","with","after"),relation,{w[it]}){relation=it}
                if(relation!="with") Field(w["offset"],offset,{offset=it},true)
                if(relation=="before") TimesEditor(w["planned_meal"],planned,{planned=it},single=true)
                Notice(w["meal_schedule_hint"])
            }
        }
        if(mode=="weekly") FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {(1..7).forEach {d->ChoiceChip(selected=d in days,onClick={days=if(d in days)days-d else days+d},label={Text(w[dayKey(d)])})}}
        if(mode!="meal") ChoiceRow(w["food_relation"],listOf("none","before","with","after"),relation,{w[it]}){relation=it}
        Notice(w["save_prescription"])
        TextAction(onClick={advanced=!advanced}) {Text(w[if(advanced)"less" else "advanced"])}
        if(advanced) {
            Field(w["instructions"],instructions,{instructions=it},multiline=true)
            Field(w["stock"],stock,{stock=it},true);Text(w["unknown_stock"],style=MaterialTheme.typography.bodySmall)
            if(stock.isNotBlank()) {Field(w["stock_per_dose"],perDose,{perDose=it},true);Field(w["refill_at"],refill,{refill=it},true);Text(w["refill_hint"],style=MaterialTheme.typography.bodySmall)}
            Toggle(w["remind_med"],reminders){reminders=it}
            Toggle(w["custom_med_alert"],customAlert,w["custom_med_alert_hint"]){customAlert=it}
            Toggle(w["override_repeat"],overrideRepeat){overrideRepeat=it}
            if(overrideRepeat){Field(w["repeat_count"],repeatCount,{repeatCount=it},true);Field(w["repeat_delay"],repeatMinutes,{repeatMinutes=it},true)}
            Toggle(w["active"],active){active=it}
            if(existing!=null) Text(w["edit_plan_hint"],style=MaterialTheme.typography.bodySmall)
        }
        OutlineAction(onClick={try{preview=build()}catch(e:Exception){invalid=true}}) {Text(w["preview"])}
    }
    preview?.let {m->
        val now=System.currentTimeMillis();val events=Planner.events(m.planner(),emptyList(),now,now+8*86_400_000L,ZoneId.systemDefault()).take(8)
        BrandedDialog(onDismissRequest={preview=null},title={Text(w["preview"])},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(events.isEmpty()) Text(w[if(m.mode=="meal")"wait_meal" else "no_doses"])
            events.forEach {e->Text("${dateText(e.millis(),w)} · ${timeText(e.millis(),w)}${if(e.estimated())" · "+w["planned_time"] else ""}")}
        }},confirmButton={TextAction(onClick={preview=null}){Text(w["close"])}})
    }
}

@Composable fun BackupDialog(mode:String,onDismiss:()->Unit,onConfirm:(CharArray)->Unit) {
    val w=LocalWords.current;var password by remember {mutableStateOf("")};var repeat by remember {mutableStateOf("")}
    var understood by remember {mutableStateOf(false)};var invalid by remember {mutableStateOf(false)}
    BrandedDialog(onDismissRequest=onDismiss,title={Text(w[if(mode=="create")"backup_create" else "backup_restore"])},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(w[if(mode=="create")"password_hint" else "restore_warning"])
            if(mode=="restore") Text(w["restore_lock_notice"],style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=password,onValueChange={password=it},label={Text(w["password"])},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            if(mode=="create") OutlinedTextField(value=repeat,onValueChange={repeat=it},label={Text(w["password_repeat"])},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            if(mode=="restore") Toggle(w["backup_replace_confirm"],understood){understood=it}
            if(invalid) Text(w["invalid"],color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextAction(onClick={
        if(password.length<10 || (mode=="create" && password!=repeat) || (mode=="restore" && !understood)) invalid=true
        else {val chars=password.toCharArray();password="";repeat="";onConfirm(chars)}
    }){Text(w["confirm"])}},dismissButton={TextAction(onClick=onDismiss){Text(w["cancel"])}})
}
