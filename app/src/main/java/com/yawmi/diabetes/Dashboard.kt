@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

@Composable
fun Home(
    s: State, busy: Boolean, add: (String) -> Unit, dose: (Reminder) -> Unit, sick: () -> Unit,
    cup: () -> Unit, notifications: () -> Unit, portion: (Double, String, String) -> Unit,
    customise: () -> Unit = {}, onMode: (Boolean) -> Unit = {}, permissionRevision: Int = 0,
    openOverview: () -> Unit = {}, openHydration: (() -> Unit)? = null, openGlucose: (() -> Unit)? = null
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
        Modifier.fillMaxSize().verticalScroll(scroll).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(w.f("hello", "name" to p.name.ifBlank { w["friend"] }), dateText(now, w)) {
            IconButton(onClick = customise, modifier = Modifier.semantics { contentDescription = w["customise"] }) {
                Glyph("settings", Modifier.size(23.dp))
            }
        }
        SegmentedRail(listOf(w["compact_home"], w["detailed"]), if (p.compactHome) 0 else 1,
            onSelect = { onMode(it == 0) })
        AnimatedContent(
            targetState = p.compactHome,
            transitionSpec = {
                if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                else ((fadeIn(tween(280, 80)) + slideInVertically(tween(560, easing = NasaqEase)) { if (targetState) -32 else 48 }) togetherWith
                    (fadeOut(tween(190)) + scaleOut(tween(420, easing = NasaqEase), targetScale = .97f)))
                    .using(SizeTransform(clip = true) { _, _ -> tween(490, easing = NasaqEase) })
            },
            label = "today-layout"
        ) { compact ->
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
                if (compact) {
                    CompactDashboard(s, summary, doses.firstOrNull(), busy, add, dose, cup, openOverview, openHydration, openGlucose)
                    NextActionCard(s, now, onAction = { r -> if (r.kind == "medication") dose(r) else add(if (r.kind == "care") "care_tools" else r.kind) }, onSetup = notifications)
                } else {
                    p.order.filterNot { it in p.hiddenCards }.forEachIndexed { index, kind ->
                        key(kind) {
                            Reveal(index) {
                                when (kind) {
                                    "glucose" -> ReadingHero(summary, p, p.cardSizes[kind] ?: "standard", openGlucose) { add("glucose") }
                                    "water" -> DetailedWater(s, summary.waterMl, busy, add, portion, openDetails = openHydration)
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
                    DayOverviewCard(s, summary, openOverview)
                    NextActionCard(s, now, onAction = { r ->
                        if (r.kind == "medication") dose(r) else add(if (r.kind == "care") "care_tools" else r.kind)
                    }, onSetup = notifications)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("glucose", "water", "meal", "symptom").forEach { kind ->
                            RoundAction(kind, w[kind], { if (!busy) add(kind) }, domainColors(kind).backdrop)
                        }
                    }
                }
            }
        }

        if ((p.waterEnabled || p.glucoseEnabled || s.medications.any { it.active && it.reminders }) &&
            (!permission.first || !permission.second)) {
            Surface(onClick = notifications, color = c.secondaryContainer, shape = ControlShape) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph("bell", Modifier.size(18.dp), c.onSecondaryContainer)
                    Text(w["reminder_setup_needed"], Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Glyph("arrow", Modifier.size(16.dp), c.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun CompactDashboard(
    s: State, summary: DayOverview, nextDose: Reminder?, busy: Boolean, add: (String) -> Unit,
    dose: (Reminder) -> Unit, cup: () -> Unit, openOverview: () -> Unit,
    openHydration: (() -> Unit)?, openGlucose: (() -> Unit)?
) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val p = s.preferences
    val rows = p.order.filter { it in listOf("glucose", "water", "medications") && it !in p.hiddenCards }
    NasaqBoard {
        SectionHeading(w["day_overview"], w["details"], openOverview)
        Text(w.f("day_event_count", "count" to number(summary.activityCount.toDouble(), w)),
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        rows.forEach { kind ->
            HorizontalDivider(color = c.outlineVariant)
            val label = w[when (kind) { "glucose" -> "day_glucose_average"; "water" -> "water_today"; else -> "doses_taken_today" }]
            val value = when (kind) {
                "water" -> number(summary.waterMl / 1000, w, 2) + " " + w["litres_short"]
                "glucose" -> summary.glucoseValue(p.unit, w)?.let { it + " " + p.unit } ?: "—"
                else -> number(summary.takenDoses.toDouble(), w)
            }
            val open = when (kind) { "water" -> openHydration; "glucose" -> openGlucose; else -> openOverview }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).heightIn(min = 52.dp).then(
                    if (open != null) Modifier.clickable(onClick = open) else Modifier), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DomainMark(kind)
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                val action = when (kind) { "water" -> w["widget_add_cup"]; "glucose" -> w["add_reading"]; else -> w["record_dose"] }
                IconButton(onClick = {
                    when (kind) {
                        "water" -> cup()
                        "glucose" -> add("glucose")
                        else -> if (nextDose != null) dose(nextDose) else add("dose")
                    }
                }, enabled = !busy, modifier = Modifier.semantics { contentDescription = action }) {
                    Glyph("plus", Modifier.size(21.dp), if (busy) c.outline else c.primary)
                }
            }
        }
        if ("water" in rows && summary.waterAverageMl != null) {
            HorizontalDivider(color = c.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(w["water_week"], style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                Text(number(summary.waterAverageMl / 1000, w, 2) + " " + w["litres_short"],
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun DetailedWater(
    s: State, water: Double, busy: Boolean, add: (String) -> Unit, portion: (Double, String, String) -> Unit,
    openDetails: (() -> Unit)? = null, expanded: Boolean = false
) {
    val p = s.preferences
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val progress = if (p.waterGoal > 0) (water / p.waterGoal).toFloat().coerceIn(0f, 1f) else 0f
    val small = !expanded && p.cardSizes["water"] == "small"
    val height = when { expanded -> 192.dp; small -> 92.dp; p.cardSizes["water"] == "large" -> 169.dp; else -> 136.dp }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = c.tertiaryContainer, shape = BoardShape, contentColor = c.onTertiaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(w["water_today"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AnimatedNumber(water / 1000, 2, color = c.onTertiaryContainer,
                            style = if (small) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium)
                        Text(w["litres_short"], style = MaterialTheme.typography.labelLarge)
                        if (p.waterGoal > 0) {
                            val motion = LocalMotion.current
                            val bar by androidx.compose.animation.core.animateFloatAsState(progress,
                                if (motion) tween(650, easing = NasaqEase) else snap(), label = "water-progress")
                            LinearProgressIndicator(progress = { bar }, modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = c.tertiary, trackColor = c.tertiary.copy(alpha = .22f))
                            Text(w.f("water_goal_progress", "percent" to number((water * 100 / p.waterGoal), w)),
                                style = MaterialTheme.typography.labelLarge)
                            Text(w["daily_target"] + " " + number(p.waterGoal / 1000.0, w, 2) + " " + w["litres_short"],
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    WaterVessel(progress, Modifier.widthIn(max = height * .66f).weight(.73f).height(height))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (openDetails != null) TextButton(onClick = openDetails) { Text(w["details"]) }
                    else Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { add("water") }, enabled = !busy) {
                        Glyph("plus", Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(w["custom_amount"])
                    }
                }
            }
        }
        if (p.waterGoal > 0 && !small) Surface(color = c.surfaceContainerLow, shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, c.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(w["tap_cup_short"], style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                WaterCups(s, busy, portion)
            }
        }
    }
}

@Composable
fun ReadingHero(summary: DayOverview, p: Preferences, size: String = "standard",
    openHistory: (() -> Unit)? = null, onAdd: () -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val average = summary.glucoseValue(p.unit, w)
    val small = size == "small"
    val readings = remember(summary.entries) { summary.entries.filter { it.kind == "glucose" }.asReversed().takeLast(12) }
    val tone = domainColors("glucose")
    Surface(color = tone.backdrop, contentColor = tone.ink, shape = BoardShape) {
        Box(Modifier.fillMaxWidth()) {
            Contours(Modifier.matchParentSize(), tone.ink)
            Column(Modifier.padding(if (small) 14.dp else 19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = tone.ink, shape = CircleShape) {
                        Glyph("glucose", Modifier.padding(8.dp).size(20.dp), tone.backdrop)
                    }
                    Text(w["day_glucose_average"], Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(average ?: "—", style = if (small) MaterialTheme.typography.displaySmall
                            else if (size == "large") MaterialTheme.typography.displayLarge.copy(fontSize = 62.sp, lineHeight = 65.sp)
                            else MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (average != null) Text(p.unit, style = MaterialTheme.typography.labelLarge)
                    }
                    if (readings.size > 1 && !small) ReadingTrace(readings, Modifier.weight(.95f).height(69.dp), tone.ink)
                }
                Text(if (average == null) w["hero_empty"] else w.f("day_reading_count", "count" to number(summary.glucoseCount.toDouble(), w)),
                    style = MaterialTheme.typography.bodySmall)
                if (openHistory != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onAdd, modifier = Modifier.weight(1f).heightIn(min = 45.dp), shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Glyph("plus", Modifier.size(17.dp), c.onPrimary); Spacer(Modifier.width(5.dp)); Text(w["add_reading"])
                        }
                        IconButton(onClick = openHistory, modifier = Modifier.semantics { contentDescription = w["jump_glucose"] }) {
                            Glyph("log", Modifier.size(22.dp), tone.ink)
                        }
                    }
                } else Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp), shape = CircleShape) {
                    Glyph("plus", Modifier.size(17.dp), c.onPrimary); Spacer(Modifier.width(5.dp)); Text(w["add_reading"])
                }
            }
        }
    }
}

@Composable
private fun ReadingTrace(readings: List<Entry>, modifier: Modifier, ink: Color) {
    val motion = LocalMotion.current
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    val low = remember(readings) { readings.minOf(::mg) }
    val high = remember(readings) { readings.maxOf(::mg) }
    LaunchedEffect(motion) {
        if (motion) reveal.animateTo(1f, tween(770, 100, NasaqEase)) else reveal.snapTo(1f)
    }
    Canvas(modifier) {
        val inset = 5.dp.toPx()
        val width = (size.width - 2 * inset).coerceAtLeast(1f)
        val height = (size.height - 2 * inset).coerceAtLeast(1f)
        val range = (high - low).coerceAtLeast(1.0)
        val first = readings.first().time
        val span = (readings.last().time - first).coerceAtLeast(1L)
        val points = readings.mapIndexed { index, entry ->
            val x = if (span <= 1L) index.toFloat() / readings.lastIndex else ((entry.time - first).toDouble() / span).toFloat()
            val y = if (high == low) .5f else ((mg(entry) - low) / range).toFloat()
            Offset(inset + x * width, inset + height * (.87f - .74f * y))
        }
        repeat(3) { index ->
            val y = inset + height * (.14f + index * .36f)
            drawLine(ink.copy(alpha = .18f), Offset(inset, y), Offset(inset + width, y), 1.dp.toPx())
        }
        clipRect(right = size.width * reveal.value) {
            points.zipWithNext().forEach { (a, b) -> drawLine(ink, a, b, 2.dp.toPx(), StrokeCap.Round) }
            points.forEach { drawCircle(ink, 2.5.dp.toPx(), it) }
        }
    }
}

@Composable fun ActivityRow(e:Entry,s:State,trailing: (@Composable () -> Unit)? = null){
    val w=LocalWords.current;val c=MaterialTheme.colorScheme;val kind=if(e.kind=="dose")"medication"else e.kind
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        DomainMark(kind)
        Column(Modifier.weight(1f)){Text(if(e.kind=="dose")w[e.status]else w[kind],style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium);Text(timeText(e.time,w)+if(e.context.isNotBlank() && !e.context.startsWith("cup_") && !e.context.startsWith("widget_"))" · "+w[e.context]else "",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
        Text(entryTitle(e,s,w),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
        trailing?.invoke()
    }
}
