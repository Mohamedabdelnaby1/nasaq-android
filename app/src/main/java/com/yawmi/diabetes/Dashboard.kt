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
    val now = trackingTime()
    val summary = remember(s.entries, s.care, now) { dayOverview(s, now) }
    val stats = remember(s.entries, p, now) { TrackingStats(s, now) }
    val shortcuts = remember(s.entries, p, now) { journalHabits(s, now).actions }
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
        shortcuts.firstOrNull()?.takeUnless { it in primaryJournalActions }?.let { kind ->
            DailyAction(kind, w[kind], null, !busy, { add(kind) }, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.filter { it in primaryJournalActions }.forEach { kind ->
                val target = Modifier.weight(1f).fillMaxHeight()
                if (kind == "water") DailyAction(kind, w["log_cup_short"],
                    number(p.cup.toDouble(), w) + " " + w["ml"], !busy, cup, target)
                else DailyAction(kind, w["add_reading"], null, !busy, { add(kind) }, target)
            }
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
                            DeskMetricLine(kind, deskMetric(kind, stats, summary, w), destination(kind))
                        }

                    }
                } else if (!compact) {
                    ordered.forEachIndexed { index, kind ->
                        Reveal(index) { DeskMetricCard(kind, deskMetric(kind, stats, summary, w), stats, s, destination(kind)) }
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

private fun deskMetric(kind: String, stats: TrackingStats, summary: DayOverview, w: Words): MetricDisplay = when (kind) {
    "glucose" -> stats.glucose(w)
    "water" -> stats.waterToday(w)
    else -> MetricDisplay(w["doses_taken_today"], number(summary.takenDoses.toDouble(), w), "", "", "")
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
private fun DeskMetricLine(kind: String, item: MetricDisplay, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(ControlShape).clickable(onClick = onOpen).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DomainMark(kind)
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            if (item.period.isNotEmpty()) Text(item.period, style = MaterialTheme.typography.labelSmall)
        }
        Text(item.value,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Glyph("arrow", Modifier.size(15.dp))
    }
}

@Composable
private fun DeskMetricCard(kind: String, item: MetricDisplay, stats: TrackingStats, state: State, onOpen: () -> Unit) {
    val tone = domainColors(kind)
    val size = state.preferences.cardSizes[kind]
    Surface(onOpen, Modifier.fillMaxWidth().sharedDesk(kind), shape = BoardShape,
        color = tone.backdrop, contentColor = tone.ink) {
        Box {
            SectionArt(kind, Modifier.matchParentSize(), tone.ink)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(kind, Modifier.size(20.dp), tone.ink)
                    Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Glyph("arrow", Modifier.size(17.dp), tone.ink)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = if (size == "large") 65.dp else 33.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        DeskAmount(item.amount, if (size == "large") MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)
                        if (item.unit.isNotBlank()) Text(item.unit, style = MaterialTheme.typography.labelMedium)
                    }
                    if (kind == "glucose" && size != "small") {
                        val points = stats.glucoseReadings.take(12).asReversed()
                        if (points.size > 1)
                            ReadingTrace(points, Modifier.weight(.9f).height(39.dp), tone.ink)
                    }
                }
                if (item.period.isNotEmpty()) Text(item.period, style = MaterialTheme.typography.labelLarge)
                if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.labelMedium)
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
internal fun ReadingHero(stats: TrackingStats, onOptions: (() -> Unit)?, onAdd: () -> Unit) {
    val w = LocalWords.current
    NasaqBoard(Modifier.sharedDesk("glucose"), color = domainColors("glucose").backdrop, outlined = false) {
        val item = stats.glucose(w)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (onOptions != null) IconAction(onOptions, Modifier.semantics { contentDescription = w["tracking_options"] }) {
                Glyph("settings", Modifier.size(21.dp), domainColors("glucose").ink)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                DeskAmount(item.amount, MaterialTheme.typography.displaySmall)
                if (item.unit.isNotEmpty()) Text(item.unit, style = MaterialTheme.typography.labelMedium)
            }
            val points = stats.glucoseReadings.take(12).asReversed()
            if (points.size > 1) ReadingTrace(points, Modifier.weight(1f).height(62.dp), domainColors("glucose").ink)
        }
        Text(item.period, style = MaterialTheme.typography.labelLarge)
        if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.bodySmall)
        ActionButton(onAdd, Modifier.fillMaxWidth()) { Text(w["add_reading"]) }
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
