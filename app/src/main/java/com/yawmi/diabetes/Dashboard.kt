@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.yawmi.diabetes.core.Planner
import kotlinx.coroutines.delay

@Composable
fun Home(
    s: State, busy: Boolean, add: (String) -> Unit, dose: (Reminder) -> Unit, sick: () -> Unit,
    cup: () -> Unit, notifications: () -> Unit, portion: (Double, String, String) -> Unit,
    customise: () -> Unit = {}, onMode: (Boolean) -> Unit = {}, permissionRevision: Int = 0,
    openOverview: () -> Unit = {}
) {
    val w = LocalWords.current
    val p = s.preferences
    val c = MaterialTheme.colorScheme
    val device = androidx.compose.ui.platform.LocalContext.current
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            minute = System.currentTimeMillis() / 60_000
        }
    }
    val now = System.currentTimeMillis()
    val permission = remember(permissionRevision, minute) { Reminders.permitted(device) to Reminders.exact(device) }
    val summary = remember(s.entries, s.care, minute) { dayOverview(s, now) }
    val doses = remember(s, minute) { Reminders.plan(s, now - 7_200_000, now + 86_400_000).filter { it.kind == "medication" } }
    val scroll = rememberScrollState()
    LaunchedEffect(p.compactHome) { scroll.scrollTo(0) }
    val motion = LocalMotion.current
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 17.dp).padding(top = 10.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dateText(now, w), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(w.f("hello", "name" to p.name.ifBlank { w["friend"] }),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            FilledTonalIconButton(onClick = customise, modifier = Modifier.semantics { contentDescription = w["customise"] }) {
                Glyph("settings", Modifier.size(21.dp))
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = c.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true, false).forEach { compact ->
                    val selected = p.compactHome == compact
                    FilterChip(
                        selected = selected, onClick = { onMode(compact) },
                        label = { Text(w[if (compact) "compact_home" else "detailed"], fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = { Glyph(if (compact) "today" else "insights", Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f).pressResponse(), shape = RoundedCornerShape(13.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary, selectedLeadingIconColor = c.onPrimary)
                    )
                }
            }
        }
        if ((p.waterEnabled || p.glucoseEnabled || s.medications.any { it.active && it.reminders }) &&
            (!permission.first || !permission.second)) {
            Surface(onClick = notifications, color = c.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph("bell", Modifier.size(18.dp))
                    Text(w["reminder_setup_needed"], Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Glyph("arrow", Modifier.size(16.dp))
                }
            }
        }
        AnimatedContent(
            targetState = p.compactHome,
            transitionSpec = {
                if (!motion) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 16 }) togetherWith fadeOut(tween(120))
            },
            label = "today-layout"
        ) { compact ->
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
                if (compact) {
                    CompactDashboard(s, summary, doses.firstOrNull(), busy, add, dose, cup, openOverview)
                    NextActionCard(s, now, onAction = { r -> if (r.kind == "medication") dose(r) else add(if (r.kind == "care") "care_tools" else r.kind) }, onSetup = notifications)
                } else {
                    Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("glucose", "water", "meal", "symptom").forEach { kind ->
                                RoundAction(kind, w[kind], { if (!busy) add(kind) })
                            }
                        }
                    }
                    NextActionCard(s, now, onAction = { r -> if (r.kind == "medication") dose(r) else add(if (r.kind == "care") "care_tools" else r.kind) }, onSetup = notifications)
                    DayOverviewCard(s, summary, openOverview)
                    p.order.filterNot { it in p.hiddenCards }.forEachIndexed { index, kind ->
                        key(kind) {
                            Reveal(index) {
                                when (kind) {
                                    "glucose" -> ReadingHero(summary, p, p.cardSizes[kind] ?: "standard") { add("glucose") }
                                    "water" -> DetailedWater(s, summary.waterMl, busy, add, portion)
                                    "medications" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SectionHeading(w["medications"], w["record_dose"]) { add("dose") }
                                        if (doses.isEmpty()) Surface(color = c.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                                            Box(Modifier.padding(horizontal = 12.dp)) { SettingsRow("medication", w[if (s.medications.any { it.active }) "no_doses" else "no_meds"]) { add("new_med") } }
                                        } else Panel {
                                            val limit = when (p.cardSizes[kind]) { "small" -> 1; "large" -> 3; else -> 2 }
                                            doses.take(limit).forEach { DoseRow(it, s, dose) }
                                        }
                                    }
                                    "meals" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SectionHeading(w["today_story"], w["details"], openOverview)
                                        Panel {
                                            val limit = when (p.cardSizes[kind]) { "small" -> 1; "large" -> 5; else -> 3 }
                                            val entries = summary.entries.take(limit)
                                            if (entries.isEmpty()) EmptyState(w["story_empty"])
                                            entries.forEachIndexed { i, entry ->
                                                ActivityRow(entry, s)
                                                if (i < entries.lastIndex) HorizontalDivider(color = c.outlineVariant.copy(alpha = .45f))
                                            }
                                        }
                                    }
                                    "symptoms" -> Surface(color = c.primaryContainer.copy(alpha = .5f), shape = RoundedCornerShape(20.dp)) {
                                        Box(Modifier.padding(horizontal = 12.dp)) { SettingsRow("shield", w["care_corner"], w["care_corner_body"], sick) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDashboard(
    s: State, summary: DayOverview, nextDose: Reminder?, busy: Boolean, add: (String) -> Unit,
    dose: (Reminder) -> Unit, cup: () -> Unit, openOverview: () -> Unit
) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val p = s.preferences
    val kindNames = listOf("glucose", "water", "medications")
    val rows = p.order.filter { it in kindNames && it !in p.hiddenCards }
    Surface(color = LocalBrand.current, shape = RoundedCornerShape(27.dp), contentColor = Color.White) {
        Box(Modifier.fillMaxWidth()) {
            Pattern(Modifier.matchParentSize())
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w["day_overview"], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(w.f("day_event_count", "count" to number(summary.activityCount.toDouble(), w)), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E9E0))
                    }
                    TextButton(onClick = openOverview, colors = ButtonDefaults.textButtonColors(contentColor = Gold)) { Text(w["details"]) }
                }
                rows.forEach { kind ->
                    val icon = if (kind == "medications") "medication" else kind
                    val value = when (kind) {
                        "water" -> number(summary.waterMl / 1000, w, 2) + " " + w["litres_short"]
                        "glucose" -> summary.glucoseValue(p.unit, w)?.let { it + " " + p.unit } ?: "—"
                        else -> w.f("day_dose_count", "count" to number(summary.takenDoses.toDouble(), w))
                    }
                    val action = when (kind) { "water" -> w["widget_add_cup"]; "glucose" -> w["add_reading"]; else -> w["record_dose"] }
                    Surface(color = Color.White.copy(alpha = .10f), contentColor = Color.White, shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 3.dp, bottom = 3.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Glyph(icon, Modifier.size(21.dp), if (kind == "water") Color(0xFFA9E6EF) else Gold)
                            Column(Modifier.weight(1f)) {
                                Text(w[if (kind == "glucose") "day_glucose_average" else if (kind == "water") "water_today" else kind], style = MaterialTheme.typography.labelSmall, color = Color(0xFFD6E9E0))
                                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { when (kind) { "water" -> cup(); "glucose" -> add("glucose"); else -> if (nextDose != null) dose(nextDose) else add("dose") } },
                                enabled = !busy, modifier = Modifier.pressResponse(!busy).semantics { contentDescription = action }
                            ) { Glyph("plus", Modifier.size(21.dp), if (busy) c.outline else Gold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailedWater(s: State, water: Double, busy: Boolean, add: (String) -> Unit, portion: (Double, String, String) -> Unit) {
    val p = s.preferences
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val progress = if (p.waterGoal > 0) (water / p.waterGoal).toFloat().coerceIn(0f, 1f) else 0f
    Surface(color = c.tertiaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(w["water_today"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.onTertiaryContainer)
                    AnimatedNumber(water / 1000, 2, color = c.onTertiaryContainer)
                    Text(w["litres_short"] + if (p.waterGoal > 0) " · " + w["daily_target"] + " " + number(p.waterGoal / 1000.0, w, 2) else "",
                        style = MaterialTheme.typography.labelMedium, color = c.onTertiaryContainer)
                    if (p.waterGoal > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = c.tertiary, trackColor = c.tertiary.copy(alpha = .18f))
                }
                val height = when (p.cardSizes["water"]) { "small" -> 72.dp; "large" -> 142.dp; else -> 105.dp }
                WaterVessel(progress, Modifier.width(height * .65f).height(height))
            }
            if (p.waterGoal > 0 && p.cardSizes["water"] != "small") WaterCups(s, busy, portion)
            TextButton(onClick = { add("water") }, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                Glyph("plus", Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(w["custom_amount"])
            }
        }
    }
}

@Composable fun ReadingHero(summary:DayOverview,p:Preferences,size:String="standard",onAdd:()->Unit){
    val w=LocalWords.current
    val average=summary.glucoseAverageMg
    Surface(color=LocalBrand.current,shape=RoundedCornerShape(24.dp)){Box(Modifier.fillMaxWidth()){
        Pattern(Modifier.matchParentSize())
        Column(Modifier.padding(if(size=="small")12.dp else if(size=="large")24.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(if(size=="small")5.dp else 10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(w["day_glucose_average"],color=Color(0xFFCEE0D6),style=MaterialTheme.typography.labelMedium)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        if(average==null)Text("—",fontSize=48.sp,lineHeight=52.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        else AnimatedNumber(Planner.fromMg(average,p.unit),if(p.unit=="mmol/L")1 else 0,style=when(size){"small"->MaterialTheme.typography.displaySmall;"large"->MaterialTheme.typography.displayLarge;else->MaterialTheme.typography.displayMedium},color=Color.White)
                        Text(p.unit,Modifier.padding(bottom=7.dp),color=Color(0xFFCEE0D6),style=MaterialTheme.typography.labelMedium)
                    }
                    Text(if(average==null)w["hero_empty"]else w.f("day_reading_count","count" to number(summary.glucoseCount.toDouble(),w)),style=MaterialTheme.typography.labelSmall,color=Color(0xFFCEE0D6))
                }
                Glyph("glucose",Modifier.size(39.dp),Color(0xFFCEE0D6))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Button(onClick=onAdd,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Forest),contentPadding=PaddingValues(horizontal=14.dp,vertical=8.dp),modifier=Modifier.weight(1f).heightIn(min=42.dp)){Glyph("plus",Modifier.size(16.dp),Forest);Spacer(Modifier.width(6.dp));Text(w["add_reading"],style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)}
                Text(w["manual_source"],color=Color(0xFFCEE0D6),style=MaterialTheme.typography.labelSmall)
            }
        }
    }}
}

@Composable fun ActivityRow(e:Entry,s:State){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val kind=if(e.kind=="dose")"medication"else e.kind
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Surface(shape=RoundedCornerShape(12.dp),color=if(kind=="water")Aqua.copy(alpha=.13f)else c.primaryContainer.copy(alpha=.55f)){Glyph(kind,Modifier.padding(9.dp).size(19.dp),if(kind=="water")Aqua else c.primary)}
        Column(Modifier.weight(1f)){Text(if(e.kind=="dose")w[e.status]else w[kind],style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium);Text(timeText(e.time,w)+if(e.context.isNotBlank() && !e.context.startsWith("cup_") && !e.context.startsWith("widget_"))" · "+w[e.context]else "",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Text(entryTitle(e,s,w),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
    }
}
