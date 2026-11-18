@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

fun number(value:Double,w:Words,digits:Int=0):String=NumberFormat.getNumberInstance(w.locale).apply {
    maximumFractionDigits=digits;minimumFractionDigits=0
}.format(value)
fun dateText(time:Long,w:Words):String=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(w.locale))
fun timeText(time: Long, w: Words): String = clockText(
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime(), w
)

internal fun clockText(time: String, w: Words): String = clockText(LocalTime.parse(time), w)

internal fun clockText(time: LocalTime, w: Words): String {
    val clock = time.format(DateTimeFormatter.ofPattern("h:mm", w.locale)
        .withDecimalStyle(java.time.format.DecimalStyle.of(w.locale)))
    return clock + " " + w[if (time.hour < 12) "clock_am" else "clock_pm"]
}
fun isToday(time:Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()
fun periodStart(days:Int)=LocalDate.now().minusDays((days-1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun mg(e:Entry)=com.yawmi.diabetes.core.Planner.toMg(e.value,e.unit)

@Composable fun YawmiApp(repo:Repository,activity:MainActivity) {
    val s=repo.state;val p=s.preferences
    YawmiTheme(p) {
        val w=LocalWords.current
        var tab by rememberSaveable { mutableStateOf("today") }
        var form by rememberSaveable { mutableStateOf<String?>(null) }
        var medEdit by remember { mutableStateOf<Medication?>(null) }
        var recordEdit by remember { mutableStateOf<Entry?>(null) }
        var draftEntry by remember { mutableStateOf<Entry?>(null) }
        var pendingDose by remember { mutableStateOf<Reminder?>(null) }
        var delete by remember { mutableStateOf<Entry?>(null) }
        var backupMode by remember { mutableStateOf<String?>(null) }
        var info by remember { mutableStateOf<String?>(null) }
        val snackbar=remember { SnackbarHostState() }
        val scope=rememberCoroutineScope();val feedback=rememberFeedback()
        var homeLayoutOpen by remember{mutableStateOf(false)}
        var timelineDate by rememberSaveable { mutableStateOf<String?>(null) }
        var timelineOrigin by rememberSaveable { mutableStateOf("today") }
        fun openDay(date: LocalDate?, origin: String = "today") {
            timelineDate = date?.toString()
            timelineOrigin = origin
            tab = "day_summary"
        }
        var saveNotice by remember { mutableStateOf<Job?>(null) }
        fun saved(entry:Entry,previous:Entry?,actual:State){
            val stored=actual.entries.find{it.id==entry.id} ?: return
            feedback()
            if (previous == null && entry.kind == "glucose" && System.currentTimeMillis() - entry.time in 0..600_000 && mg(entry) < 70) info = "low"
            saveNotice?.cancel()
            saveNotice = scope.launch {
                val action = withTimeoutOrNull(8_000) {
                    snackbar.showSnackbar(w["record_saved"], w["undo"], withDismissAction = true, duration = SnackbarDuration.Long)
                }
                if(action==SnackbarResult.ActionPerformed)
                    repo.change{RecordUndo.revert(it,stored,previous)}
            }
        }
        fun record(entry:Entry){repo.add(entry){saved(entry,null,it)}}
        fun editRecord(entry: Entry) { draftEntry = null; recordEdit = entry; form = entry.kind }
        fun addToDay(kind: String, date: LocalDate) {
            val local = java.time.LocalTime.now()
            val time = date.atTime(local).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            recordEdit = null
            draftEntry = Entry(kind = kind, time = time, value = if (kind == "water") p.cup.toDouble() else 0.0,
                unit = if (kind == "water") "mL" else p.unit)
            form = kind
        }
        LaunchedEffect(activity.requestedScreen, activity.requestedEntry, activity.locked, p.setupVersion) {
            val screen = activity.requestedScreen
            val entry = activity.requestedEntry
            if (!activity.locked && p.setupVersion >= 2 && (screen.isNotBlank() || entry.isNotBlank())) {
                if (screen.isNotBlank()) tab = screen
                timelineDate = null
                timelineOrigin = "today"
                recordEdit = null
                draftEntry = null
                medEdit = null
                pendingDose = null
                homeLayoutOpen = false
                form = entry.takeIf { it in setOf("glucose", "water", "meal", "symptom", "care_tools", "day_summary") }
                activity.requestedScreen = ""
                activity.requestedEntry = ""
            }
        }
        LaunchedEffect(repo.error) { repo.error?.takeIf { it!="load_error" }?.let { snackbar.showSnackbar(w[it]);repo.clearError() } }
        BackHandler(enabled=form!=null || medEdit!=null || tab!="today") {
            when {
                form != null -> { form = null; recordEdit = null; draftEntry = null }
                medEdit != null -> medEdit = null
                tab == "day_summary" -> tab = timelineOrigin
                else -> tab = "today"
            }
        }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            when {
                !repo.ready -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                repo.error=="load_error" -> Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) {
                    Text(w["load_error"],style=MaterialTheme.typography.titleLarge)
                    Button(onClick={repo.refresh()}) {Text(w["retry"])}
                    OutlinedButton(onClick={backupMode="restore"}) {Text(w["backup_restore"])}
                }
                activity.locked -> Column(Modifier.fillMaxSize().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Glyph("shield",Modifier.size(72.dp));Spacer(Modifier.height(24.dp));Text(w["locked"],style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp));Button(onClick={activity.unlock()}) {Text(w["unlock"])}
                }
                (!p.onboarded || p.setupVersion<2) -> SetupFlow(repo,activity)
                else -> BoxWithConstraints {
                    val wide=maxWidth>=840.dp
                    Row(Modifier.fillMaxSize()) {
                        if(wide) NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars),containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
                            Spacer(Modifier.height(24.dp))
                            Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher), null, Modifier.size(40.dp))
                            Spacer(Modifier.height(24.dp))
                            mainSections.forEach { key ->
                                NavigationRailItem(selected=sectionGroup(tab)==key,onClick={tab=key},icon={Glyph(key,Modifier.size(24.dp))},label={Text(w[key])})
                            }
                        }
                        AppChrome(tab,p,onTab={tab=it},onQuick={form="quick"},notifications={tab="settings";activity.openNotifications=true},snackbar=snackbar,showBottomBar=!wide) { insets ->
                            val content:@Composable (String)->Unit={destination->
                                Box(Modifier.fillMaxSize().padding(insets),contentAlignment=Alignment.TopCenter) {
                                    Box(Modifier.widthIn(max=1000.dp).fillMaxSize()) {
                                        when(destination) {
                                            "today"->Home(s,repo.busy,add={form=it},dose={pendingDose=it},sick={info="sick"},cup={record(Entry(kind="water",value=p.cup.toDouble(),unit="mL"))},notifications={tab="settings";activity.openNotifications=true},portion={amount,context,id->record(Entry(id=id,kind="water",value=amount,unit="mL",context=context))},customise={homeLayoutOpen=true},onMode={v->repo.preferences{it.copy(compactHome=v)}},permissionRevision=activity.permissionRevision,openOverview={openDay(null)},openHydration={tab="hydration"},openGlucose={tab="glucose_log"},
                                                openMedicines={tab="medications"},openDay={openDay(it)})
                                            "log", "glucose_log" -> Journal(s,onEdit={recordEdit=it;form=it.kind},onDelete={delete=it},
                                                initialFilter=if(destination=="glucose_log")"glucose"else"all",onAddReading={form="glucose"},onImport={activity.openCsv()})
                                            "hydration" -> HydrationScreen(s,repo.busy,onBack={tab="today"},add={form=it},
                                                portion={amount,context,id->record(Entry(id=id,kind="water",value=amount,unit="mL",context=context))})
                                            "day_summary" -> DayOverviewScreen(s, timelineDate?.let(LocalDate::parse) ?: LocalDate.now(),
                                                onEdit = ::editRecord, onAdd = ::addToDay) { tab=timelineOrigin }
                                            "medications"->Medicines(s,add={form="new_med"},edit={medEdit=it},dose={pendingDose=it},manual={form="dose"})
                                            "insights"->Insights(s,add={form=it},export={days,type->activity.export(s,days,type)},
                                                onOpenDay={openDay(it, "insights")})
                                            else->SettingsScreen(repo,activity,backup={backupMode=it},info={info=it})
                                        }
                                    }
                                }
                            }
                            SectionMotion(tab, content)
                        }
                    }
                    if(repo.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
        if(!activity.locked || repo.error=="load_error") {
        fun dismissEntry() {
            form = null
            recordEdit = null
            draftEntry = null
        }
        fun saveEntry(entry: Entry) {
            val old = recordEdit
            if (old != null) repo.change(onSuccess = { saved(entry, old, it) }) { Records.replace(it, old, entry) }
            else record(entry)
            dismissEntry()
        }
        val activeForm = form
        if (activeForm == "quick" || activeForm == "glucose" || activeForm == "water") {
            RecordingSheet(activeForm, ::dismissEntry, onSelect = { form = it }, state = s,
                onDraft = { draftEntry = it; form = it.kind }, onQuickWater = if (!repo.busy) {{
                    dismissEntry()
                    record(Entry(kind = "water", value = p.cup.toDouble(), unit = "mL"))
                }} else null, onHideWater = { amount -> repo.preferences { prefs ->
                    prefs.copy(ignoredWaterAmounts = (prefs.ignoredWaterAmounts.toList() + amount).takeLast(20).toSet())
                } }) { kind, close ->
                QuickEntryContent(kind, recordEdit, s, onDismiss = { close(::dismissEntry) },
                    onSave = { entry -> close { saveEntry(entry) } }, draft = draftEntry)
            }
        } else if (activeForm == "assistant_title") ArabicAssistant(s, { form = null }) { entry ->
            draftEntry = entry
            form = entry.kind
        } else if (activeForm == "day_summary") androidx.compose.ui.window.Dialog(
            onDismissRequest = { form = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) { DayOverviewScreen(s) { form = null } }
        else if (activeForm == "care_tools") CareTools(s, { form = null }) { change -> repo.change(block = change) }
        else if (activeForm == "new_med" || medEdit != null) MedicationEditor(medEdit,p,onDismiss={form=null;medEdit=null},onSave={repo.put(it);form=null;medEdit=null})
        else if (activeForm != null) EntryEditor(activeForm, recordEdit, s, onDismiss = ::dismissEntry, onSave = ::saveEntry, initialTime = draftEntry?.time)
        if (!activity.locked) {
            activity.csvPreview?.let { preview ->
                CsvReview(preview, s, repo.busy, onDismiss = { activity.csvPreview = null }) {
                    repo.change(onSuccess = { activity.csvPreview = null; activity.message("csv_import_done") }) { current ->
                        val incoming = preview.additions(current)
                        require(current.entries.size + incoming.size <= 100_000)
                        current.copy(entries = current.entries + incoming)
                    }
                }
            }
            activity.reportDays?.let { days ->
                ReportComposer(days, onDismiss = { activity.reportDays = null }) { range, options ->
                    activity.reportDays = null
                    activity.exportFile(s, range, "pdf", options)
                }
            }
        }
        pendingDose?.let { r ->
            val med=s.medications.find { it.id==r.medicationId }
            AlertDialog(onDismissRequest={pendingDose=null},title={Text(med?.name ?: w["record_dose"])},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(med?.dose ?: "");Text(w["dose_confirm"]);Text(w["missed_hint"],style=MaterialTheme.typography.bodySmall)
                if(!med?.instructions.isNullOrBlank()) Text(med!!.instructions,style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton(onClick={
                record(Entry(kind="dose",medicationId=r.medicationId,occurrence=r.occurrence,planned=r.original,status="taken",note=med?.let {"${it.name} · ${it.dose}"} ?: ""));pendingDose=null
            },enabled=!repo.busy){Text(w["taken"])}},dismissButton={TextButton(onClick={
                record(Entry(kind="dose",medicationId=r.medicationId,occurrence=r.occurrence,planned=r.original,status="skipped",note=med?.name ?: ""));pendingDose=null
            },enabled=!repo.busy){Text(w["skipped"])}})
        }
        delete?.let { e -> AlertDialog(onDismissRequest={delete=null},title={Text(w["delete"])},text={Text(w["delete_confirm"])},
            confirmButton={TextButton(onClick={repo.delete(e.id);delete=null}){Text(w["delete"])}},dismissButton={TextButton(onClick={delete=null}){Text(w["cancel"])}}) }
        activity.backupPreview?.let { incoming ->
            BackupRestoreReview(s, incoming, repo.error == "load_error", repo.busy,
                onDismiss = { activity.backupPreview = null }) { useSettings, review ->
                repo.restore(incoming, useSettings, review) {
                    activity.backupPreview = null
                    activity.message("restore_done")
                }
            }
        }
        backupMode?.let { mode -> BackupDialog(mode,onDismiss={backupMode=null},onConfirm={password->backupMode=null;activity.backup(mode,password)}) }
        info?.let { kind ->
            AlertDialog(onDismissRequest={info=null},title={Text(w[if(kind=="low") "low_alert" else if(kind=="privacy") "privacy" else "sick_days"])},text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(w[if(kind=="low") "low_body" else if(kind=="privacy") "privacy_detail" else "urgent_body"])
                    if(kind=="sick") Text(p.sickPlan.ifBlank { w["no_plan"] })
                    if(kind=="sick") OutlinedButton(onClick={activity.oneShot("symptom",60);info=null}) {Text(w["followup_schedule"])}
                    Text(w["medical_notice"],style=MaterialTheme.typography.bodySmall)
                    if(kind=="low") Text(w["timer_warning"],style=MaterialTheme.typography.bodySmall)
                    if(p.carePhone.isNotBlank() && kind!="privacy") OutlinedButton(onClick={activity.dial(p.carePhone)}) {Text(w["call_care"])}
                }
            },confirmButton={TextButton(onClick={info=null}){Text(w["close"])}},dismissButton={if(kind=="low") TextButton(onClick={activity.oneShot("glucose",15);info=null}) {Text(w["recheck"])}})
        }
        }
        if(homeLayoutOpen && !activity.locked){
            var layouts by remember{mutableStateOf(s.care.layouts)}
            HomeLayoutEditor(s,onDismiss={homeLayoutOpen=false},onSave={next->repo.saveLayout(next,layouts);homeLayoutOpen=false},onLayouts={layouts=it})
        }
    }
}

internal val mainSections = listOf("today", "log", "medications", "insights", "settings")

internal fun sectionGroup(section: String): String = when (section) {
    "hydration", "day_summary" -> "today"
    "glucose_log" -> "log"
    else -> section
}

@Composable
internal fun SectionMotion(section: String, content: @Composable (String) -> Unit) {
    val state = rememberSaveableStateHolder()
    if (!LocalMotion.current) {
        state.SaveableStateProvider(section) { content(section) }
        return
    }
    val rtl = LocalWords.current.rtl
    SharedTransitionLayout {
        val shared = this
    AnimatedContent(
        targetState = section,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val from = mainSections.indexOf(sectionGroup(initialState))
            val to = mainSections.indexOf(sectionGroup(targetState))
            val result = if (targetState != sectionGroup(targetState)) {
                (fadeIn(tween(240, 35)) + slideInVertically(tween(480, easing = NasaqEase)) { it / 6 }) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(360, easing = NasaqEase), targetScale = .965f))
            } else if (initialState != sectionGroup(initialState)) {
                (fadeIn(tween(230)) + scaleIn(tween(370, easing = NasaqEase), initialScale = .965f)) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(365, easing = NasaqEase)) { it / 7 })
            } else {
                val direction = (if (to >= from) 1 else -1) * (if (rtl) -1 else 1)
                (fadeIn(tween(280, 70)) + slideInHorizontally(tween(520, easing = NasaqEase)) { direction * it / 5 } +
                    scaleIn(tween(490, easing = NasaqEase), initialScale = .985f)) togetherWith
                    (fadeOut(tween(185)) + slideOutHorizontally(tween(360, easing = NasaqEase)) { -direction * it / 10 })
            }
            result.using(null)
        },
        label = "destination"
    ) { destination ->
        val routeScope = this
        state.SaveableStateProvider(destination) {
            CompositionLocalProvider(LocalDeskTransition provides DeskTransition(shared, routeScope)) { content(destination) }
        }
    }
    }
}

@Composable fun Welcome(repo:Repository) {
    val w=LocalWords.current;val p=repo.state.preferences
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(18.dp))
        Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.primaryContainer) {Glyph("glucose",Modifier.padding(22.dp).size(64.dp))}
        Text(w["welcome"],style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(w["welcome_body"],style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceRow(w["language"],listOf("system","ar","en"),p.language,{when(it){"ar"->"العربية";"en"->"English";else->w["system"]}}) { choice->repo.preferences {it.copy(language=choice)} }
        ChoiceRow(w["theme"],listOf("system","light","dark"),p.theme,{w[it]}) {choice->repo.preferences {it.copy(theme=choice)} }
        Notice(w["onboard_hint"])
        Text(w["medical_notice"],style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {AssistChip(onClick={},label={Text(w["offline"])});AssistChip(onClick={},label={Text(w["no_account"])})}
        Button(onClick={repo.preferences {it.copy(onboarded=true)}},enabled=!repo.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(w["start"])}
        Text(w["sample_warning"],style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun Panel(title:String?=null,tone:Color=MaterialTheme.colorScheme.surfaceContainerLow,content:@Composable ColumnScope.()->Unit) {
    NasaqBoard(modifier = Modifier.animateContentSize(if (LocalMotion.current) tween(370, easing = NasaqEase) else snap()), color = tone) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}
@Composable fun Notice(text:String,error:Boolean=false) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(18.dp),color=if(error) colors.errorContainer else colors.secondaryContainer.copy(alpha=.6f)) {
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Glyph(if(error) "info" else "shield",Modifier.size(22.dp),if(error) colors.onErrorContainer else colors.onSecondaryContainer)
            Text(text,style=MaterialTheme.typography.bodySmall,color=if(error) colors.onErrorContainer else colors.onSecondaryContainer)
        }
    }
}
@Composable fun ChoiceRow(title:String,values:List<String>,selected:String,label:(String)->String,onSelect:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            values.forEach { value->FilterChip(selected=selected==value,onClick={onSelect(value)},label={Text(label(value))},modifier=Modifier.pressResponse()) }
        }
    }
}
@Composable fun Toggle(title:String,checked:Boolean,subtitle:String?=null,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().pressResponse(compression=.985f).clickable {onChange(!checked)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable fun EmptyState(title:String,description:String=LocalWords.current["empty_detail"]) {
    Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Glyph("log",Modifier.size(36.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
