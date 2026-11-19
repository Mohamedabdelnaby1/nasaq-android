@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
        var path by rememberSaveable { mutableStateOf(arrayListOf("today")) }
        val tab = path.last()
        fun navigate(route: String) { path = ArrayList(pushRoute(path, route)) }
        fun selectTab(route: String) { path = arrayListOf(route) }
        fun openSettings(section: String) = navigate(settingsRoute(section))
        fun goBack() { path = if (path.size > 1) ArrayList(path.dropLast(1)) else arrayListOf("today") }
        var form by remember { mutableStateOf<String?>(null) }
        var medEdit by remember { mutableStateOf<Medication?>(null) }
        var recordEdit by remember { mutableStateOf<Entry?>(null) }
        var draftEntry by remember { mutableStateOf<Entry?>(null) }
        var formError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(form, recordEdit?.id, medEdit?.id) { formError = null }
        var pendingDose by remember { mutableStateOf<Reminder?>(null) }
        var delete by remember { mutableStateOf<Entry?>(null) }
        var backupMode by remember { mutableStateOf<String?>(null) }
        var info by remember { mutableStateOf<String?>(null) }
        val snackbar=remember { SnackbarHostState() }
        val scope=rememberCoroutineScope();val feedback=rememberFeedback()
        var timelineDate by rememberSaveable { mutableStateOf<String?>(null) }
        fun openDay(date: LocalDate?) {
            timelineDate = date?.toString()
            navigate("day_summary")
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
        fun logCup() {
            val id = java.util.UUID.randomUUID().toString()
            repo.change(onSuccess = { committed -> committed.entries.find { it.id == id }?.let { saved(it, null, committed) } }) { current ->
                Records.add(current, cupEntry(current.preferences, id = id))
            }
        }
        fun editRecord(entry: Entry) { draftEntry = null; recordEdit = entry; form = entry.kind }
        fun addToDay(kind: String, date: LocalDate) {
            val local = java.time.LocalTime.now()
            val time = date.atTime(local).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            recordEdit = null
            draftEntry = Entry(kind = kind, time = time, value = if (kind == "water") p.cup.toDouble() else 0.0,
                unit = if (kind == "water") "mL" else p.unit)
            form = kind
        }
        LaunchedEffect(activity.requestedScreen, activity.requestedEntry, activity.locked, p.onboarded, p.setupVersion) {
            val screen = activity.requestedScreen
            val entry = activity.requestedEntry
            if (!activity.locked && p.onboarded && p.setupVersion >= 2 && (screen.isNotBlank() || entry.isNotBlank())) {
                val launch = requestedLaunch(screen, entry)
                if (launch.path == null && launch.editor == null) {
                    activity.requestedScreen = ""
                    activity.requestedEntry = ""
                    return@LaunchedEffect
                }
                launch.path?.let { path = ArrayList(it) }
                timelineDate = null
                recordEdit = null
                draftEntry = null
                medEdit = null
                pendingDose = null
                form = launch.editor
                activity.requestedScreen = ""
                activity.requestedEntry = ""
            }
        }
        LaunchedEffect(repo.error) { repo.error?.takeIf { it!="load_error" }?.let { snackbar.showSnackbar(w[it]);repo.clearError() } }
        BackHandler(enabled=form!=null || medEdit!=null || tab!="today") {
            when {
                form != null -> { form = null; recordEdit = null; draftEntry = null }
                medEdit != null -> medEdit = null
                else -> goBack()
            }
        }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            when {
                !repo.ready -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                repo.error=="load_error" -> Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) {
                    Text(w["load_error"],style=MaterialTheme.typography.titleLarge)
                    ActionButton(onClick={repo.refresh()}) {Text(w["retry"])}
                    OutlineAction(onClick={backupMode="restore"}) {Text(w["backup_restore"])}
                }
                activity.locked -> Column(Modifier.fillMaxSize().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Glyph("shield",Modifier.size(72.dp));Spacer(Modifier.height(24.dp));Text(w["locked"],style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp));ActionButton(onClick={activity.unlock()}) {Text(w["unlock"])}
                }
                (!p.onboarded || p.setupVersion<2) -> SetupFlow(repo,activity) { kind, entry ->
                    activity.requestedEntry = ""
                    activity.requestedScreen = ""
                    when (kind) {
                        "water" -> entry?.let { saved(it, null, repo.state) }
                        "glucose" -> form = "glucose"
                    }
                }
                else -> BoxWithConstraints {
                    val wide=maxWidth>=840.dp
                    Row(Modifier.fillMaxSize()) {
                        if(wide) NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars),containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
                            Spacer(Modifier.height(24.dp))
                            Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher), null, Modifier.size(40.dp))
                            Spacer(Modifier.height(24.dp))
                            mainSections.forEach { key ->
                                NavigationRailItem(selected=sectionGroup(tab)==key,onClick={selectTab(key)},icon={Glyph(key,Modifier.size(24.dp))},label={Text(w[key])})
                            }
                        }
                        AppChrome(tab,p,onTab=::selectTab,onQuick={form="quick"},notifications={openSettings("notifications")},
                            snackbar=snackbar,showBottomBar=!wide,onBack=if (path.size > 1) ::goBack else null) { insets ->
                            val content:@Composable (String)->Unit={destination->
                                Box(Modifier.fillMaxSize().padding(insets),contentAlignment=Alignment.TopCenter) {
                                    Box(Modifier.widthIn(max=1000.dp).fillMaxSize()) {
                                        when(destination) {
                                            "today" -> Home(s, repo.busy, add={form=it}, dose={pendingDose=it}, sick={info="sick"}, cup=::logCup,
                                                notifications={openSettings("notifications")}, customise={openSettings("home_cards")},
                                                permissionRevision=activity.permissionRevision, openOverview={openDay(null)},
                                                openHydration={navigate("hydration")}, openGlucose={navigate("glucose_log")},
                                                openMedicines={navigate("medications")})
                                            "log", "glucose_log" -> Journal(s, onEdit=::editRecord, onDelete={delete=it},
                                                initialFilter=if(destination=="glucose_log")"glucose"else"all",onAddReading={form="glucose"},
                                                onImport=if(destination=="log") activity::openCsv else null,
                                                onTrackingOptions={openSettings("glucose_view_options")})
                                            "hydration" -> HydrationScreen(s,repo.busy,onBack=null,add={form=it},cup=::logCup,
                                                portion={amount,context,id->record(Entry(id=id,kind="water",value=amount,unit="mL",context=context))},
                                                onTrackingOptions={openSettings("water_view_options")})
                                            "day_summary" -> DayOverviewScreen(s, timelineDate?.let(LocalDate::parse) ?: LocalDate.now(),
                                                onEdit = ::editRecord, onAdd = ::addToDay, onClose = null)
                                            "medications"->Medicines(s,add={form="new_med"},edit={medEdit=it},dose={pendingDose=it},manual={form="dose"})
                                            "insights"->Insights(s,export={days,type->activity.export(s,days,type)},onOpenDay=::openDay)
                                            "settings" -> SettingsOverview(p, ::openSettings)
                                            else -> SettingsDetail(repo,activity,backup={backupMode=it},info={info=it},
                                                section=destination.substringAfter('/'),openSection=::openSettings,
                                                openMedications={medication -> navigate("medications"); medEdit=medication})
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
            if (repo.busy) return
            formError = null
            val old = recordEdit
            val submittedForm = form
            repo.change(onSuccess = { actual ->
                if (form == submittedForm && recordEdit == old) dismissEntry()
                saved(entry, old, actual)
            }, onFailure = { reason ->
                if (form == submittedForm && recordEdit == old) formError = reason
            }) { current ->
                if (old == null) Records.add(current, entry) else {
                    if (current.entries.firstOrNull { it.id == old.id } != old) throw RecordChanged()
                    Records.replace(current, old, entry)
                }
            }
        }

        val activeForm = form
        if (activeForm == "quick" || activeForm == "glucose" || activeForm == "water") {
            RecordingSheet(activeForm, ::dismissEntry, onSelect = { form = it }, state = s,
                onDraft = { draftEntry = it; form = it.kind }, onHideWater = { amount -> repo.preferences { prefs ->
                    prefs.copy(ignoredWaterAmounts = (prefs.ignoredWaterAmounts.toList() + amount).takeLast(20).toSet())
                } }) { kind, close ->
                QuickEntryContent(kind, recordEdit, s, onDismiss = { close(::dismissEntry) },
                    onSave = ::saveEntry, draft = draftEntry, saving = repo.busy, saveError = formError)
            }
        } else if (activeForm == "assistant_title") ArabicAssistant(s, { form = null }) { entry ->
            draftEntry = entry
            form = entry.kind
        } else if (activeForm == "care_tools") CareTools(s, { form = null }) { change -> repo.change(block = change) }
        else if (activeForm == "new_med" || medEdit != null) {
            val existing = medEdit
            MedicationEditor(existing, p, onDismiss = { dismissEntry(); medEdit = null }, onSave = { medication ->
                if (!repo.busy) {
                    formError = null
                    repo.put(medication, onSuccess = {
                        if (form == activeForm && medEdit == existing) { dismissEntry(); medEdit = null }
                    }, onFailure = { formError = it })
                }
            }, saving = repo.busy, saveError = formError)
        }
        else if (activeForm != null && activeForm in setOf("meal", "symptom", "dose", "weight", "pressure", "a1c")) {
            EntryEditor(activeForm, recordEdit, s, onDismiss = ::dismissEntry, onSave = ::saveEntry,
                initialTime = draftEntry?.time, draft = draftEntry, saving = repo.busy, saveError = formError)
        }

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
                ReportComposer(s, days, onDismiss = { activity.reportDays = null }) { range, options, now ->
                    activity.reportDays = null
                    activity.exportFile(s, range, "pdf", options, now)
                }
            }
        }
        pendingDose?.let { r ->
            val med=s.medications.find { it.id==r.medicationId }
            var deferring by remember(r) { mutableStateOf(false) }
            val now = trackingTime()
            val mayDefer = remember(s, r, now) { Reminders.canDeferDose(s, r, now) }
            BrandedDialog(onDismissRequest={pendingDose=null},title={Text(med?.name ?: w["record_dose"])},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(med?.dose ?: "");Text(w["dose_confirm"]);Text(w["missed_hint"],style=MaterialTheme.typography.bodySmall)
                if(!med?.instructions.isNullOrBlank()) Text(med!!.instructions,style=MaterialTheme.typography.bodySmall)
                DoseTrackingReference(s)
                if (mayDefer) OutlineAction(onClick = {
                    deferring = true
                    activity.deferDose(r) { scheduled ->
                        deferring = false
                        if (scheduled) pendingDose = null
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !repo.busy && !deferring) {
                    Text(w.f("dose_snooze_minutes", "n" to number(p.snoozeMinutes.toDouble(), w)))
                }
            }},confirmButton={TextAction(onClick={
                record(Entry(kind="dose",medicationId=r.medicationId,occurrence=r.occurrence,planned=r.original,status="taken",note=med?.let {"${it.name} · ${it.dose}"} ?: ""));pendingDose=null
            },enabled=!repo.busy){Text(w["taken"])}},dismissButton={TextAction(onClick={
                record(Entry(kind="dose",medicationId=r.medicationId,occurrence=r.occurrence,planned=r.original,status="skipped",note=med?.name ?: ""));pendingDose=null
            },enabled=!repo.busy){Text(w["skipped"])}})
        }
        delete?.let { e -> BrandedDialog(onDismissRequest={delete=null},title={Text(w["delete"])},text={Text(w["delete_confirm"])},
            confirmButton={TextAction(onClick={repo.delete(e.id);delete=null}){Text(w["delete"])}},dismissButton={TextAction(onClick={delete=null}){Text(w["cancel"])}}) }
        activity.backupPreview?.let { incoming ->
            BackupRestoreReview(s, incoming, repo.error == "load_error", repo.busy,
                onDismiss = { activity.backupPreview = null }) { useSettings, review, revision ->
                repo.restore(incoming, useSettings, review, revision) {
                    activity.backupPreview = null
                    activity.message("restore_done")
                }
            }
        }
        backupMode?.let { mode -> BackupDialog(mode,onDismiss={backupMode=null},onConfirm={password->backupMode=null;activity.backup(mode,password)}) }
        info?.let { kind ->
            BrandedDialog(onDismissRequest={info=null},title={Text(w[if(kind=="low") "low_alert" else if(kind=="privacy") "privacy" else "sick_days"])},text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(w[if(kind=="low") "low_body" else if(kind=="privacy") "privacy_detail" else "urgent_body"])
                    if(kind=="sick") Text(p.sickPlan.ifBlank { w["no_plan"] })
                    if(kind=="sick") OutlineAction(onClick={activity.oneShot("symptom",60);info=null}) {Text(w["followup_schedule"])}
                    Text(w["medical_notice"],style=MaterialTheme.typography.bodySmall)
                    if(kind=="low") Text(w["timer_warning"],style=MaterialTheme.typography.bodySmall)
                    if(p.carePhone.isNotBlank() && kind!="privacy") OutlineAction(onClick={activity.dial(p.carePhone)}) {Text(w["call_care"])}
                }
            },confirmButton={TextAction(onClick={info=null}){Text(w["close"])}},dismissButton={if(kind=="low") TextAction(onClick={activity.oneShot("glucose",15);info=null}) {Text(w["recheck"])}})
        }
        }
    }
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
                (fadeIn(tween(280, 70)) + slideInHorizontally(tween(540, easing = NasaqEase)) { direction * it / 3 } +
                    scaleIn(tween(490, easing = NasaqEase), initialScale = .945f)) togetherWith
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
            values.forEach { value->ChoiceChip(selected=selected==value,onClick={onSelect(value)},label={Text(label(value))},modifier=Modifier.pressResponse()) }
        }
    }
}
@Composable fun Toggle(title:String,checked:Boolean,subtitle:String?=null,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clip(ControlShape).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).heightIn(min = 50.dp).padding(horizontal = 8.dp, vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked = checked, onCheckedChange = null, thumbContent = if (checked) {{ Glyph("check", Modifier.size(14.dp), MaterialTheme.colorScheme.onPrimaryContainer) }} else null)
    }
}
@Composable fun EmptyState(title:String,description:String=LocalWords.current["empty_detail"]) {
    Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        DomainMark("log", Modifier.size(50.dp))
        Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
