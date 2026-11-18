@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*

@Composable
fun Journal(s: State, onEdit: (Entry) -> Unit, onDelete: (Entry) -> Unit,
    initialFilter: String = "all", onAddReading: (() -> Unit)? = null, onImport: (() -> Unit)? = null) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = remember(s.entries, s.medications, filter, query, w) {
        val search = query.trim()
        s.entries.filter {
            (filter == "all" || it.kind == filter) &&
                (search.isBlank() || it.note.contains(search, true) || w[it.context].contains(search, true) ||
                    entryTitle(it, s, w).contains(search, true))
        }.sortedByDescending { it.time }
    }
    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(entries, zone) { entries.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() } }
    val motion = LocalMotion.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenGutters, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("journal-heading") {
            SectionBanner(if (initialFilter == "glucose") "glucose" else "log",
                w[if (initialFilter == "glucose") "glucose" else "log"], action = {
                if (onImport != null) IconAction(onClick = onImport,
                    modifier = Modifier.semantics { contentDescription = w["csv_import"] }) {
                    Glyph("backup", Modifier.size(21.dp))
                }
            })
        }
        if (initialFilter == "glucose" && onAddReading != null) item("daily-average") {
            ReadingHero(dayOverview(s, System.currentTimeMillis()), s.preferences, onAdd = onAddReading)
        }
        item("journal-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(w["search"]) },
                    leadingIcon = { Glyph("search", Modifier.size(20.dp)) },
                    trailingIcon = if (query.isNotEmpty()) {{
                        IconAction(onClick = { query = "" }, modifier = Modifier.semantics { contentDescription = w["clear_search"] }) {
                            Glyph("close", Modifier.size(18.dp))
                        }
                    }} else null,
                    shape = CircleShape, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = c.surfaceContainerLow,
                        unfocusedBorderColor = c.outlineVariant))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all", "glucose", "dose", "water", "meal", "symptom", "weight", "pressure", "a1c").forEach { kind ->
                        ChoiceChip(selected = filter == kind, onClick = { filter = kind; expandedId = null },
                            label = { Text(w[if (kind == "dose") "medication" else kind]) })
                    }
                }
            }
        }
        if (entries.isEmpty()) item("journal-empty") { NasaqBoard { EmptyState(w["empty"]) } }
        grouped.forEach { (date, group) ->
            item("date-$date") {
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(dateText(group.first().time, w), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f), color = c.outlineVariant)
                    Text(number(group.size.toDouble(), w), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                }
            }
            items(group, key = { it.id }) { entry ->
                val expanded = expandedId == entry.id
                val kind = if (entry.kind == "dose") "medication" else entry.kind
                val tone = domainColors(kind)
                NasaqBoard(color = if (expanded) tone.backdrop else c.surfaceContainerLow, modifier = if (motion) Modifier.animateItem(
                    fadeInSpec = tween(250, 55), placementSpec = tween(480, easing = NasaqEase), fadeOutSpec = tween(160))
                    else Modifier) {
                    Column(Modifier.fillMaxWidth().clip(ControlShape).clickable {
                        expandedId = if (expanded) null else entry.id
                    }) {
                        ActivityRow(entry, s, trailing = { Glyph(if (expanded) "up" else "down", Modifier.size(16.dp), c.onSurfaceVariant) })
                    }
                    MotionExpand(expanded) {
                        Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
                            if (entry.kind == "dose") Text(w[entry.status], style = MaterialTheme.typography.labelMedium)
                            Row {
                                if (entry.kind != "dose") TextAction(onClick = { onEdit(entry) }) { Text(w["edit"]) }
                                TextAction(onClick = { onDelete(entry) }, colors = ButtonDefaults.textButtonColors(contentColor = c.error)) {
                                    Text(w["delete"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun Medicines(s:State,add:()->Unit,edit:(Medication)->Unit,dose:(Reminder)->Unit,manual:()->Unit){
    val device=androidx.compose.ui.platform.LocalContext.current;val w=LocalWords.current;val c=MaterialTheme.colorScheme
    var archived by rememberSaveable{mutableStateOf(false)}
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000 - now % 60_000); now = System.currentTimeMillis() } }
    val upcoming = remember(s, now) { Reminders.plan(s, now - 7_200_000, now + 172_800_000).filter { it.kind == "medication" } }
    val motion = LocalMotion.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenGutters, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item("medicines-heading") {
            SectionBanner("medications", w["medications"], action = {
                IconAction(onClick = add, modifier = Modifier.semantics { contentDescription = w["add_med"] }) {
                    Glyph("plus", Modifier.size(22.dp), domainColors("medications").ink)
                }
            })
        }
        item("medicines-next") {
            val next = upcoming.firstOrNull()
            val tone = domainColors("medication")
            Surface(color = tone.backdrop, contentColor = tone.ink, shape = BoardShape) {
                Box(Modifier.fillMaxWidth()) {
                    SectionArt("medications", Modifier.matchParentSize(), tone.ink)
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Glyph("clock", Modifier.size(20.dp), tone.ink)
                            Text(w["next_reminder"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (next != null) {
                            Text(timeText(next.due, w), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text(s.medications.find { it.id == next.medicationId }?.name ?: w["no_doses"],
                                style = MaterialTheme.typography.titleMedium)
                        } else Text(w["no_doses"], style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ActionButton(onClick = { if (next != null) dose(next) else add() }, modifier = Modifier.weight(1f)) {
                                Text(w[if (next != null) "record_dose" else "add_med"])
                            }
                            if (s.medications.any { it.active }) TextAction(onClick = manual) { Text(w["manual_dose"]) }
                        }
                    }
                }
            }
        }
        item("medicines-switch") {
            SegmentedRail(listOf(w["active"], w["archived"]), if (archived) 1 else 0, { archived = it == 1 })
        }
        val meds=s.medications.filter{it.active!=archived}
        if(meds.isEmpty())item{Panel{EmptyState(w["no_meds"])}}
        items(meds,key={it.id}){med->
            var details by rememberSaveable { mutableStateOf(false) }
            NasaqBoard(modifier = if (motion) Modifier.animateItem(
                fadeInSpec = tween(260, 40), placementSpec = tween(440, easing = NasaqEase), fadeOutSpec = tween(150))
                else Modifier) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    DomainMark("medication")
                    Column(Modifier.weight(1f)){Text(med.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(listOf(med.strength,med.dose).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
                    IconAction(onClick={edit(med)}, modifier = Modifier.semantics { contentDescription = w["edit"] }){Glyph("edit",Modifier.size(20.dp))}
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    if(med.mode=="interval")StatusPill(w.f("hours_every","n" to number(med.intervalHours.toDouble(),w)))
                    else if(med.mode=="meal")StatusPill(w[med.meal])else med.times.forEach{StatusPill(clockText(it, w))}
                    if(med.relation!="none")StatusPill(w[med.relation],c.onSurfaceVariant,c.surfaceContainer)
                }
                if(med.stock>=0 && med.stock<=med.refillAt)StatusPill(w["refill"],c.error,c.errorContainer)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    TextAction(onClick={details=!details},contentPadding=PaddingValues(0.dp)){Text(w[if(details)"less"else"details"],style=MaterialTheme.typography.labelMedium)}
                    val next=upcoming.firstOrNull{it.medicationId==med.id}
                    if(next!=null && med.active)TonalAction(onClick={dose(next)},shape=RoundedCornerShape(12.dp)){Text(w["record_dose"],style=MaterialTheme.typography.labelMedium)}
                }
                MotionExpand(details){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(med.mode=="weekly")Text(med.weekdays.sorted().joinToString(" · "){w[dayKey(it)]},style=MaterialTheme.typography.bodySmall)
                    if(med.mode=="meal")Text(w[if(med.relation=="before")"planned_time"else"wait_meal"],style=MaterialTheme.typography.bodySmall)
                    if(med.stock>=0)Text(w["remaining"]+": "+number(med.stock.toDouble(),w),style=MaterialTheme.typography.labelMedium)
                    if(med.instructions.isNotBlank())Text(med.instructions,style=MaterialTheme.typography.bodySmall)
                    if(med.customAlert)TextAction(onClick={Reminders.medicationChannel(device,med,s.preferences);device.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,device.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,med.channelId()))}){Text(w["channel"])}
                    if(med.active)TextAction(onClick=manual){Text(w["manual_dose"])}
                }}
            }
        }
        item{Text(w["therapy_warning"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
    }
}

@Composable fun MetricTile(label:String,value:String,modifier:Modifier=Modifier,tone:Color=MaterialTheme.colorScheme.surfaceContainerLow){
    NasaqBoard(modifier.heightIn(min = 96.dp), color = tone) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable fun Insights(s:State,add:(String)->Unit,export:(Int,String)->Unit, onOpenDay: ((java.time.LocalDate) -> Unit)? = null){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val p=s.preferences
    var days by rememberSaveable{mutableIntStateOf(14)}
    var context by rememberSaveable{mutableStateOf("all")}
    val entries=s.entries.filter{it.time>=periodStart(days) && it.time<=System.currentTimeMillis()}
    val glucose=entries.filter{it.kind=="glucose" && (context=="all" || it.context==context)}
    val range=if(glucose.isEmpty())0.0 else glucose.count{mg(it) in p.lower..p.upper}*100.0/glucose.size
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),verticalArrangement=Arrangement.spacedBy(16.dp)){
        SectionBanner("insights", w["insights"], dateText(periodStart(days), w) + " – " + dateText(System.currentTimeMillis(), w))
        val periods = listOf(1, 7, 14, 30)
        SegmentedRail(periods.map { w[if (it == 1) "today_only" else "last${it}"] }, periods.indexOf(days), { days = periods[it] })
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            MetricTile(w["average_short"],if(glucose.isEmpty())"—"else number(com.yawmi.diabetes.core.Planner.fromMg(glucose.map{mg(it)}.average(),p.unit),w,1),Modifier.weight(1f),c.primaryContainer.copy(alpha=.6f))
            MetricTile(w["readings"],number(glucose.size.toDouble(),w),Modifier.weight(1f))
        }
        val chartTone = domainColors("glucose")
        NasaqBoard(color = chartTone.backdrop, contentColor = chartTone.ink, outlined = false) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(w["glucose_trend"])
            }
            Text(p.unit, style = MaterialTheme.typography.labelLarge)
            GlucoseChart(glucose, p, onOpenDay)
        }
        NasaqBoard { ChoiceRow(w["reading_filter"], listOf("all", "fasting", "not_fasting", "before", "after"),
            context, { w[it] }) { context = it } }
        WeeklyInsights(s, context, onOpenDay)
        Panel{
            SectionHeading(w["range_distribution"])
            if(glucose.isNotEmpty()){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)){
                    Box(Modifier.size(110.dp),contentAlignment=Alignment.Center){MeterRing((range/100).toFloat(),Modifier.fillMaxSize(),c.primary);Column(horizontalAlignment=Alignment.CenterHorizontally){Text(number(range,w)+"%",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(w["in_range_short"],style=MaterialTheme.typography.labelSmall,maxLines=1)}}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        listOf("below_target" to glucose.count{mg(it)<p.lower},"in_range_short" to glucose.count{mg(it) in p.lower..p.upper},"above_target" to glucose.count{mg(it)>p.upper}).forEachIndexed{i,(label,count)->
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Surface(shape=CircleShape,color=if(i==1)c.primary else if(i==0)Gold else c.error,modifier=Modifier.size(7.dp)){};Text(w[label],Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);Text(number(count.toDouble(),w),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}
                        }
                    }
                }
            }else Text(w["no_analysis"],style=MaterialTheme.typography.bodySmall)
            Text(w["range_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
        }
        if(glucose.isNotEmpty())Panel(w["context_analysis"]){glucose.groupBy{it.context}.forEach{(context,list)->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(w[context],Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);StatusPill(number(com.yawmi.diabetes.core.Planner.fromMg(list.map{mg(it)}.average(),p.unit),w,1)+" "+p.unit)}
        };Text(w["average_context_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Surface(color=LocalBrand.current,shape=RoundedCornerShape(23.dp)){Box{Pattern(Modifier.matchParentSize());Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Glyph("export",Modifier.size(26.dp),Gold);Text(w["report_ready"],style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color.White);Text(w["report_ready_body"],style=MaterialTheme.typography.bodySmall,color=Color(0xFFCEE0D6))
            ActionButton(onClick={export(days,"pdf")},enabled=entries.isNotEmpty(),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Forest),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text(w["export_pdf"])}
            TextAction(onClick={export(days,"csv")},enabled=entries.isNotEmpty(),colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text(w["export_csv"])}
        }}}
        Panel(w["other_tracking"]){FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("weight","pressure","a1c").forEach{kind->OutlineAction(onClick={add(kind)}){Text(w[kind])}}};listOf("weight","pressure","a1c").forEach{kind->s.entries.filter{it.kind==kind}.maxByOrNull{it.time}?.let{e->Text(w[kind]+" · "+entryTitle(e,s,w)+" · "+dateText(e.time,w),style=MaterialTheme.typography.labelSmall)}}}
        Text(w["correlation_hint"],style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
    }
}
