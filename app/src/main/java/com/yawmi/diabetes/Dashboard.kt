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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
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
    openOverview: () -> Unit = {}, openHydration: (() -> Unit)? = null, openGlucose: (() -> Unit)? = null,
    openMedicines: (() -> Unit)? = null, openDay: ((LocalDate) -> Unit)? = null
) {
    val w = LocalWords.current
    val p = s.preferences
    val c = MaterialTheme.colorScheme
    val device = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60_000 - now % 60_000); now = System.currentTimeMillis() }
    }
    val summary = remember(s.entries, s.care, now) { dayOverview(s, now) }
    val doses = remember(s, now) { Reminders.plan(s, now - 7_200_000, now + 86_400_000).filter { it.kind == "medication" } }
    val permission = remember(permissionRevision, now) { Reminders.permitted(device) to Reminders.exact(device) }
    val ordered = p.order.filter { it !in p.hiddenCards && it in deskKinds }
    val motion = LocalMotion.current
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ScreenTitle(w["today"], dateText(now, w)) {
            IconAction(customise, Modifier.semantics { contentDescription = w["customise"] }) {
                Glyph("settings", Modifier.size(21.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DailyAction("glucose", w["add_reading"], null, !busy, { add("glucose") }, Modifier.weight(1f).fillMaxHeight())
            DailyAction("water", w["log_cup_short"],
                number(p.cup.toDouble(), w) + " " + w["ml"], !busy, cup, Modifier.weight(1f).fillMaxHeight())
        }
        NextActionCard(s, now, onAction = { reminder ->
            if (reminder.kind == "medication") dose(reminder) else add(if (reminder.kind == "care") "care_tools" else reminder.kind)
        }, onSetup = notifications)
        SegmentedRail(listOf(w["compact_home"], w["detailed"]), if (p.compactHome) 0 else 1, { onMode(it == 0) })
        AnimatedContent(p.compactHome, transitionSpec = {
            if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else (fadeIn(tween(300, 85)) + slideInVertically(tween(510, easing = NasaqEase)) { if (targetState) -it / 12 else it / 14 } togetherWith
                (fadeOut(tween(180)) + scaleOut(tween(300), targetScale = .975f)))
                .using(SizeTransform(clip = true) { _, _ -> tween(500, easing = NasaqEase) })
        }, label = "daily-desk-layout") { compact ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fun destination(kind: String): () -> Unit = when (kind) {
                    "glucose" -> openGlucose ?: { add("glucose") }
                    "water" -> openHydration ?: { add("water") }
                    else -> openMedicines ?: { doses.firstOrNull()?.let(dose) ?: add("dose") }
                }
                if (compact && ordered.isNotEmpty()) {
                    NasaqBoard {
                        ordered.forEachIndexed { index, kind ->
                            if (index > 0) HorizontalDivider()
                            DeskMetricLine(kind, summary, s, destination(kind))
                        }
                        if ("water" in ordered && summary.waterAverageMl != null) {
                            Text(w["water_week"] + " · " + number(summary.waterAverageMl / 1000, w, 2) + " " + w["litres_short"],
                                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        }
                    }
                } else if (!compact) {
                    ordered.forEachIndexed { index, kind ->
                        Reveal(index) { DeskMetricCard(kind, summary, s, destination(kind)) }
                    }
                    if ("meals" !in p.hiddenCards) NasaqBoard {
                        SectionHeading(w["today_story"], w["details"], openOverview)
                        val limit = when (p.cardSizes["meals"]) { "small" -> 1; "large" -> 5; else -> 3 }
                        val entries = summary.entries.take(limit)
                        if (entries.isEmpty()) EmptyState(w["story_empty"])
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            ActivityRow(entry, s)
                        }
                    }
                }
            }
        }
        if (openDay != null) NasaqBoard {
            SectionHeading(w["days_in_log"], w["today"], openOverview)
            DeskWeek(now, summary.activityCount, openDay)
        }
        if (!p.compactHome && "water" in ordered && p.waterGoal > 0) {
            var showingCups by rememberSaveable { mutableStateOf(false) }
            NasaqBoard {
                SettingsRow("water", w["water_cup_plan"], w[if (showingCups) "less" else "details"], expanded = showingCups,
                    onClick = { showingCups = !showingCups })
                MotionExpand(showingCups) { WaterCups(s, busy, portion) }
            }
        }
        if ("symptoms" !in p.hiddenCards && !p.compactHome) {
            NasaqBoard { SettingsRow("shield", w["care_corner"], w["care_corner_body"], onClick = sick) }
        }
        if ((p.waterEnabled || p.glucoseEnabled || s.medications.any { it.active && it.reminders }) &&
            (!permission.first || !permission.second)) {
            NasaqBoard { SettingsRow("bell", w["reminder_setup_needed"], onClick = notifications) }
        }
    }
}

private val deskKinds = setOf("glucose", "water", "medications")

@Composable
private fun DeskWeek(now: Long, count: Int, open: (LocalDate) -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val first = today.minusDays(6)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0L..6L).forEach { offset ->
            val date = first.plusDays(offset)
            val active = date == today
            Surface(onClick = { open(date) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                color = if (active) c.primary else c.surfaceContainerHigh, contentColor = if (active) c.onPrimary else c.onSurface) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val day = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, w.locale)
                    Text(day, style = MaterialTheme.typography.labelSmall)
                    Text(number(date.dayOfMonth.toDouble(), w), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (count > 0) Text(w.f("day_event_count", "count" to number(count.toDouble(), w)), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
}

@Composable
private fun deskLabel(kind: String): String {
    val w = LocalWords.current
    return w[when (kind) { "glucose" -> "day_glucose_average"; "water" -> "water_today"; else -> "doses_taken_today" }]
}

@Composable
private fun deskValue(kind: String, summary: DayOverview, state: State): Pair<String, String> {
    val w = LocalWords.current
    return when (kind) {
        "glucose" -> (summary.glucoseValue(state.preferences.unit, w) ?: "—") to state.preferences.unit
        "water" -> number(summary.waterMl / 1000, w, 2) to w["litres_short"]
        else -> number(summary.takenDoses.toDouble(), w) to ""
    }
}

@Composable
private fun DailyAction(kind: String, label: String, note: String?, enabled: Boolean, action: () -> Unit, modifier: Modifier) {
    val tone = domainColors(kind)
    Surface(action, modifier, enabled = enabled, shape = ControlShape,
        color = tone.backdrop, contentColor = tone.ink) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Glyph(kind, Modifier.size(19.dp), tone.ink)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (note != null) Text(note, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DeskMetricLine(kind: String, summary: DayOverview, state: State, onOpen: () -> Unit) {
    val (value, unit) = deskValue(kind, summary, state)
    Row(Modifier.fillMaxWidth().clip(ControlShape).clickable(onClick = onOpen).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DomainMark(kind)
        Text(deskLabel(kind), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(listOf(value, unit).filter { it.isNotBlank() }.joinToString(" "),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Glyph("arrow", Modifier.size(15.dp))
    }
}

@Composable
private fun DeskMetricCard(kind: String, summary: DayOverview, state: State, onOpen: () -> Unit) {
    val w = LocalWords.current
    val tone = domainColors(kind)
    val (value, unit) = deskValue(kind, summary, state)
    val size = state.preferences.cardSizes[kind]
    Surface(onOpen, Modifier.fillMaxWidth().sharedDesk(kind), shape = BoardShape,
        color = tone.backdrop, contentColor = tone.ink) {
        Box {
            SectionArt(kind, Modifier.matchParentSize(), tone.ink)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(kind, Modifier.size(20.dp), tone.ink)
                    Text(deskLabel(kind), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Glyph("arrow", Modifier.size(17.dp), tone.ink)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = if (size == "large") 65.dp else 33.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        DeskAmount(value, if (size == "large") MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)
                        if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelMedium)
                    }
                    if (kind == "glucose" && size != "small") {
                        val points = summary.entries.filter { it.kind == "glucose" }.asReversed().takeLast(12)
                        if (points.size > 1)
                            ReadingTrace(points, Modifier.weight(.9f).height(39.dp), tone.ink)
                    }
                }
                if (kind == "glucose") Text(w.f("day_reading_count", "count" to number(summary.glucoseCount.toDouble(), w)),
                    style = MaterialTheme.typography.labelMedium)
                if (kind == "water") summary.waterAverageMl?.let {
                    Text(w["water_week"] + " · " + number(it / 1000, w, 2) + " " + w["litres_short"],
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DeskAmount(value: String, style: androidx.compose.ui.text.TextStyle) {
    if (!LocalMotion.current) {
        Text(value, style = style, fontWeight = FontWeight.Bold, maxLines = 1)
        return
    }
    AnimatedContent(value, transitionSpec = {
        ((slideInVertically(tween(310, easing = NasaqEase)) { it / 2 } + fadeIn(tween(230, 80))) togetherWith
            (slideOutVertically(tween(210)) { -it / 3 } + fadeOut(tween(155)))).using(SizeTransform(clip = true))
    }, label = "desk-value") { amount ->
        Text(amount, style = style, fontWeight = FontWeight.Bold, maxLines = 1)
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
    Surface(modifier = Modifier.sharedDesk("glucose"), color = tone.backdrop, contentColor = tone.ink, shape = BoardShape) {
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
                        ActionButton(onClick = onAdd, modifier = Modifier.weight(1f).heightIn(min = 45.dp), shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Glyph("plus", Modifier.size(17.dp), c.onPrimary); Spacer(Modifier.width(5.dp)); Text(w["add_reading"])
                        }
                        IconAction(onClick = openHistory, modifier = Modifier.semantics { contentDescription = w["jump_glucose"] }) {
                            Glyph("log", Modifier.size(22.dp), tone.ink)
                        }
                    }
                } else ActionButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp), shape = CircleShape) {
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
