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
import com.yawmi.diabetes.core.Planner

@Composable
fun SettingsDetail(
    repo: Repository, activity: MainActivity, backup: (String) -> Unit, info: (String) -> Unit,
    section: String, openSection: (String) -> Unit, openMedications: (Medication?) -> Unit
) {
    if (section == "notifications") {
        ReminderHub(repo, activity, openWaterSettings = { openSection("water_view_options") }, openMedications)
        return
    }
    val s = repo.state
    val p = s.preferences
    val w = LocalWords.current
    var layoutEditor by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(w[when (section) {
            "personal" -> "settings_care"
            "integrations" -> "widgets"
            else -> section
        }])
        when (section) {
            "tracking_settings" -> NasaqBoard {
                SettingsRow("glucose", w["glucose_view_options"], trackingPeriod(p.glucoseViewDays, w)) {
                    openSection("glucose_view_options")
                }
                HorizontalDivider()
                SettingsRow("water", w["water_view_options"], trackingPeriod(p.waterViewDays, w)) {
                    openSection("water_view_options")
                }
            }
            "glucose_view_options", "water_view_options" -> {
                val glucose = section == "glucose_view_options"
                TrackingOptions(s, if (glucose) "glucose" else "water", repo::preferences)
                NasaqBoard {
                    if (glucose) {
                        ChoiceRow(w["unit"], listOf("mg/dL", "mmol/L"), p.unit, { it }) { unit ->
                            repo.preferences { it.copy(unit = unit) }
                        }
                        SettingsRow("glucose", w["targets"]) { edit = "targets" }
                    } else {
                        val (amount, unit) = waterVolume(p.waterGoal.toDouble(), p.waterViewUnit, w)
                        SettingsRow("water", w["water_goal_and_cup"], "$amount $unit") { edit = "water" }
                    }
                }
            }
            "appearance" -> {
                NasaqBoard {
                    SettingsRow("today", w["home_cards"], w["settings_home_body"]) { openSection("home_cards") }
                    HorizontalDivider()
                    SettingsRow("plus", w["shortcuts"], w["shortcuts_body"]) { openSection("shortcuts") }
                }
                Panel {
                    ChoiceRow(w["language"], listOf("system", "ar", "en"), p.language,
                        { when (it) { "ar" -> "العربية"; "en" -> "English"; else -> w["system"] } }) { choice ->
                        repo.preferences { it.copy(language = choice) }
                    }
                    ChoiceRow(w["theme"], listOf("system", "light", "dark"), p.theme, { w[it] }) { choice ->
                        repo.preferences { it.copy(theme = choice) }
                    }
                    ChoiceRow(w["palette"], listOf("mint", "violet", "coral", "blue"), p.palette, { w[it] }) { choice ->
                        repo.preferences { it.copy(palette = choice, dynamicColors = false) }
                    }
                    Toggle(w["dynamic"], p.dynamicColors, w["dynamic_hint"]) { value ->
                        repo.preferences { it.copy(dynamicColors = value) }
                    }
                }
                Panel {
                    Toggle(w["large_text"], p.largeText) { value -> repo.preferences { it.copy(largeText = value) } }
                    Toggle(w["reduce_motion"], p.reduceMotion) { value -> repo.preferences { it.copy(reduceMotion = value) } }
                    Toggle(w["haptics"], p.haptics, w["haptics_hint"]) { value -> repo.preferences { it.copy(haptics = value) } }
                    TextAction({ repo.preferences {
                        it.copy(theme = "system", palette = "mint", dynamicColors = false, largeText = false, reduceMotion = false)
                    } }) { Text(w["reset_appearance"]) }
                    Text(w["reset_appearance_hint"], style = MaterialTheme.typography.bodySmall)
                }
            }
            "home_cards" -> {
                ActionButton({ layoutEditor = true }, Modifier.fillMaxWidth()) { Text(w["customise"]) }
                HomeLayoutPreview(s)
            }
            "shortcuts" -> Panel {
                Text(w["shortcuts_body"], style = MaterialTheme.typography.bodySmall)
                Toggle(w["adaptive_actions"], p.adaptive, w["adaptive_actions_body"]) { value ->
                    repo.preferences { it.copy(adaptive = value) }
                }
                ChoiceRow(w["pin_shortcut"], listOf("", "meal", "symptom"), p.pinnedShortcut.takeIf {
                    it !in primaryJournalActions
                }.orEmpty(), { w[it.ifBlank { "none" }] }) { key ->
                    repo.preferences { it.copy(pinnedShortcut = key) }
                }
                TextAction({ repo.preferences {
                    it.copy(adaptiveSince = System.currentTimeMillis(), pinnedShortcut = "", ignoredWaterAmounts = emptySet())
                } }) { Text(w["reset_adaptation"]) }
            }
            "personal" -> NasaqBoard {
                SettingsRow("today", w["setup_profile"], p.name.takeIf(String::isNotBlank)) { edit = "personal" }
                HorizontalDivider()
                SettingsRow("shield", w["sick_days"]) { edit = "sick" }

            }
            "integrations" -> IntegrationPanel(repo, activity)
            "data_settings" -> {
                Panel(w["backup"]) {
                    Text(w["private_backup"], style = MaterialTheme.typography.bodySmall)
                    ActionButton({ backup("create") }, Modifier.fillMaxWidth()) { Text(w["backup_create"]) }
                    OutlineAction({ backup("restore") }, Modifier.fillMaxWidth()) { Text(w["backup_restore"]) }
                }
                Panel(w["privacy"]) {
                    Text(w["local_privacy"], style = MaterialTheme.typography.bodySmall)
                    Toggle(w["lock_app"], p.lock, w["lock_hint"]) { value ->
                        if (!value || activity.canLock()) repo.preferences { it.copy(lock = value) }
                        else activity.message("set_device_lock")
                    }
                    Toggle(w["private_notifications"], p.privateNotifications, w["private_notifications_note"]) { value -> repo.preferences { it.copy(privateNotifications = value) } }
                    Toggle(w["secure_screen"], p.secureScreen) { value -> repo.preferences { it.copy(secureScreen = value) } }
                    TextAction({ info("privacy") }) { Text(w["read_more"]) }
                }
            }
            "about" -> Panel {
                Text(w.f("version", "version" to BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium)
                Text(w["developer_credit"], style = MaterialTheme.typography.titleMedium)
                SettingsRow("info", w["developer_facebook"]) { activity.openSource("https://fb.com/mohamedabdelnaby190") }
                Text(w["medical_notice"], style = MaterialTheme.typography.bodySmall)
                Text("Tajawal · Android 8+", style = MaterialTheme.typography.labelMedium)
                TextAction({ activity.openSource("https://www.cdc.gov/diabetes/treatment/treatment-low-blood-sugar-hypoglycemia.html") }) {
                    Text("CDC · " + w["sources"])
                }
                TextAction({ activity.openSource("https://www.nhs.uk/conditions/dehydration/") }) { Text("NHS · " + w["sources"]) }
            }
        }
    }
    if (layoutEditor) {
        var layouts by remember { mutableStateOf(s.care.layouts) }
        HomeLayoutEditor(s, { layoutEditor = false }, { next ->
            repo.saveLayout(next, layouts)
            layoutEditor = false
        }, { layouts = it })
    }
    var editError by remember(edit) { mutableStateOf<String?>(null) }
    edit?.let { kind ->
        PreferencesEditor(kind, p, { edit = null }, saving = repo.busy, saveError = editError) { update ->
            if (!repo.busy) repo.change(onSuccess = { edit = null }, onFailure = { editError = it }) {
                it.copy(preferences = update(it.preferences))
            }
        }
    }

}

@Composable fun PreferencesEditor(kind:String,p:Preferences,onDismiss:()->Unit,saving:Boolean=false,saveError:String?=null,onSave:((Preferences)->Preferences)->Unit) {
    val w=LocalWords.current
    var name by remember{mutableStateOf(p.name)};var goal by remember{mutableStateOf((p.waterGoal/1000.0).toString())}
    var birth by remember { mutableStateOf(p.birthYear.takeIf { it > 0 }?.toString().orEmpty()) }
    var profileWeight by remember { mutableStateOf(p.profileWeight.takeIf { it > 0 }?.toString().orEmpty()) }
    var restricted by remember { mutableStateOf(p.fluidRestricted) }
    var cup by remember{mutableStateOf(p.cup.toString())}
    var lower by remember{mutableStateOf(com.yawmi.diabetes.core.Planner.fromMg(p.lower,p.unit).toString())}
    var upper by remember{mutableStateOf(com.yawmi.diabetes.core.Planner.fromMg(p.upper,p.unit).toString())}
    var source by remember{mutableStateOf(p.targetNote)};var quietStart by remember{mutableStateOf(p.quietStart)}
    var quietEnd by remember{mutableStateOf(p.quietEnd)};var quietMed by remember{mutableStateOf(p.medicationQuiet)}
    var snooze by remember{mutableStateOf(p.snoozeMinutes.toString())}
    var repeat by remember{mutableStateOf(p.maxRepeats.toString())};var delay by remember{mutableStateOf(p.repeatMinutes.toString())}
    var sick by remember{mutableStateOf(p.sickPlan)};var phone by remember{mutableStateOf(p.carePhone)}
    var invalid by remember{mutableStateOf(false)}
    EditorShell(w[when(kind){"personal"->"setup_profile";"targets"->"targets";"water"->"water";"quiet"->"quiet";"repeat"->"notifications";else->"sick_days"}],onDismiss,onSave={
        try {
            val update: (Preferences) -> Preferences = when (kind) {
                "personal" -> {
                    val value = name.trim()
                    val year = if (birth.isBlank()) 0 else whole(birth)
                    val kg = if (profileWeight.isBlank()) 0.0 else Planner.parseNumber(profileWeight)
                    val restriction = restricted
                    val apply: (Preferences) -> Preferences = {
                        it.copy(name = value, birthYear = year, profileWeight = kg, fluidRestricted = restriction)
                    }
                    apply
                }

                "targets" -> {
                    val low = Planner.toMg(Planner.parseNumber(lower), p.unit)
                    val high = Planner.toMg(Planner.parseNumber(upper), p.unit)
                    val note = source
                    val apply: (Preferences) -> Preferences = { current -> current.copy(lower = low, upper = high, targetNote = note) }
                    apply
                }
                "water" -> {
                    val goalMl = litresToMl(goal)
                    val cupMl = whole(cup)
                    val apply: (Preferences) -> Preferences = { it.copy(waterGoal = goalMl, cup = cupMl) }
                    apply
                }
                "quiet" -> {
                    val start = parseTimes(quietStart).single()
                    val end = parseTimes(quietEnd).single()
                    val silence = quietMed
                    val apply: (Preferences) -> Preferences = { current -> current.copy(quietStart = start, quietEnd = end, medicationQuiet = silence) }
                    apply
                }
                "repeat" -> {
                    val count = whole(repeat)
                    val delayMinutes = whole(delay)
                    val snoozeMinutes = whole(snooze)
                    val apply: (Preferences) -> Preferences = {
                        it.copy(maxRepeats = count, repeatMinutes = delayMinutes, snoozeMinutes = snoozeMinutes)
                    }
                    apply
                }
                else -> {
                    val plan = sick
                    val number = phone.trim()
                    val apply: (Preferences) -> Preferences = { it.copy(sickPlan = plan, carePhone = number) }
                    apply
                }
            }
            Codec.validate(State(preferences = update(p)))
            onSave(update)

        }catch(e:Exception){invalid=true}
    }, saveEnabled = !saving) {
        if (saveError != null) Notice(w[saveError], true)
        if(invalid) Notice(w["invalid"],true)
        when(kind) {
            "personal" -> {
                Field(w["your_name"], name, { name = it })
                Field(w["birth_year"], birth, { birth = it }, true)
                Field(w["weight_optional"], profileWeight, { profileWeight = it }, true)
                Toggle(w["fluid_restricted"], restricted) { restricted = it }
            }
            "targets"->{Field(w["lower"]+" · "+p.unit,lower,{lower=it},true);Field(w["upper"]+" · "+p.unit,upper,{upper=it},true);Field(w["target_source"],source,{source=it});Notice(w["target_hint"])}
            "water"->{Field(w["daily_litres"],goal,{goal=it},true);Field(w["cup_size"],cup,{cup=it},true);Notice(w["goal_hint"])}
            "quiet"->{TimesEditor(w["sleep_time"],quietStart,{quietStart=it},single=true);TimesEditor(w["wake_time"],quietEnd,{quietEnd=it},single=true);Toggle(w["quiet_meds"],quietMed){quietMed=it};Notice(w["notification_hint"])}
            "repeat"->{Field(w["snooze_minutes"],snooze,{snooze=it},true);Field(w["repeat_count"],repeat,{repeat=it},true);Field(w["repeat_delay"],delay,{delay=it},true);Notice(w["repeat_hint"])}
            else->{Field(w["sick_days"],sick,{sick=it},multiline=true);Field(w["care_phone"],phone,{phone=it});Notice(w["sick_hint"])}
        }
    }
}
