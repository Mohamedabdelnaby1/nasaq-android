@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.yawmi.diabetes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yawmi.diabetes.core.Planner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayOverview(
    val entries: List<Entry>,
    val care: List<CareItem>,
    val glucoseAverageMg: Double?,
    val glucoseCount: Int,
    val waterMl: Double,
    val waterAverageMl: Double?,
    val waterDays: Int,
    val takenDoses: Int
) {
    val activityCount get() = entries.size + care.size
}

fun dayOverview(s: State, now: Long, zone: ZoneId = ZoneId.systemDefault()): DayOverview {
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = date.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    val today = s.entries.filter { it.time in start until end }.sortedByDescending { it.time }
    val readings = today.filter { it.kind == "glucose" }
    val waterDays = s.entries.asSequence()
        .filter { it.kind == "water" && it.time in weekStart until start }
        .groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
        .values.map { entries -> entries.sumOf { it.value } }
    val care = s.care.items.filter {
        when (it.kind) {
            "sleep" -> it.time < end && it.end > start
            "visit", "lab", "refill" -> it.time in start until end
            else -> false
        }
    }
    return DayOverview(
        today, care, readings.takeIf { it.isNotEmpty() }?.map(::mg)?.average(), readings.size,
        today.filter { it.kind == "water" }.sumOf { it.value },
        waterDays.takeIf { it.isNotEmpty() }?.average(), waterDays.size,
        today.count { it.kind == "dose" && it.status == "taken" }
    )
}

internal fun DayOverview.glucoseValue(unit: String, w: Words): String? =
    glucoseAverageMg?.let {
        number(Planner.fromMg(it, unit), w, if (unit == "mmol/L") 1 else 0)
    }

@Composable
fun DayOverviewCard(s: State, summary: DayOverview, onOpen: (() -> Unit)? = null) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val unit = s.preferences.unit
    val readings = summary.glucoseValue(unit,w)?.let { it + " " + unit } ?: "—"
    val waterAverage = summary.waterAverageMl?.let { number(it / 1000, w, 2) + " " + w["litres_short"] } ?: "—"
    NasaqBoard {
        SectionHeading(w["day_overview"], onOpen?.let { w["details"] }) { onOpen?.invoke() }
        Surface(shape = ControlShape, color = c.primaryContainer, contentColor = c.onPrimaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph("glucose", Modifier.size(23.dp), c.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(w["day_glucose_average"], style = MaterialTheme.typography.titleSmall)
                    Text(w.f("day_reading_count", "count" to number(summary.glucoseCount.toDouble(), w)),
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(readings, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val water = domainColors("water")
            val medicine = domainColors("medication")
            Surface(Modifier.weight(1f), shape = ControlShape, color = water.backdrop, contentColor = water.ink) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Glyph("water", Modifier.size(18.dp), water.ink)
                    Text(w["water_today"], style = MaterialTheme.typography.labelMedium)
                    Text(number(summary.waterMl / 1000, w, 2) + " " + w["litres_short"],
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Surface(Modifier.weight(1f), shape = ControlShape, color = medicine.backdrop, contentColor = medicine.ink) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Glyph("medication", Modifier.size(18.dp), medicine.ink)
                    Text(w["doses_taken_today"], style = MaterialTheme.typography.labelMedium)
                    Text(number(summary.takenDoses.toDouble(), w), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        OverviewMetric("insights", w["day_water_average"], waterAverage,
            w.f("day_water_coverage", "count" to number(summary.waterDays.toDouble(), w)))
    }
}

@Composable
private fun OverviewMetric(kind: String, title: String, value: String, note: String? = null) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = if (kind == "water") c.tertiaryContainer else c.primaryContainer) {
            Glyph(kind, Modifier.padding(8.dp).size(17.dp), if (kind == "water") c.onTertiaryContainer else c.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DayOverviewScreen(s: State, initialDate: LocalDate = LocalDate.now(),
    onEdit: ((Entry) -> Unit)? = null, onAdd: ((String, LocalDate) -> Unit)? = null, onClose: () -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var selection by rememberSaveable(initialDate.toString()) { mutableStateOf(initialDate.toString()) }
    var filter by rememberSaveable { mutableStateOf("all") }
    val date = LocalDate.parse(selection)
    val filters = listOf("all", "glucose", "water", "meal", "dose", "symptom")
    var addMenu by remember { mutableStateOf(false) }
    Scaffold(containerColor = c.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenTitle(w["day_detail_title"]) {
                Row {
                    if (onAdd != null) Box {
                        IconButton(onClick = { addMenu = true },
                            modifier = Modifier.semantics { contentDescription = w["timeline_add"] }) {
                            Glyph("plus", Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            journalActions.forEach { kind ->
                                DropdownMenuItem(text = { Text(w[kind]) }, onClick = { addMenu = false; onAdd(kind, date) })
                            }
                        }
                    }
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                    Glyph("close", Modifier.size(22.dp))
                }
                }
            }
            Surface(shape = BoardShape, color = c.primary, contentColor = c.onPrimary) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { selection = date.minusDays(1).toString() },
                        modifier = Modifier.semantics { contentDescription = w["previous_day"] }) {
                        Glyph("back", Modifier.size(20.dp), c.onPrimary)
                    }
                    Text(dateText(date.atStartOfDay(zone).toInstant().toEpochMilli(), w),
                        style = MaterialTheme.typography.titleMedium, color = c.onPrimary)
                    IconButton(enabled = date < today, onClick = { selection = date.plusDays(1).toString() },
                        modifier = Modifier.semantics { contentDescription = w["next_day"] }) {
                        Glyph("arrow", Modifier.size(20.dp), if (date < today) c.onPrimary else c.onPrimary.copy(alpha = .35f))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                filters.forEach { kind ->
                    FilterChip(selected = filter == kind, onClick = { filter = kind },
                        label = { Text(w[if (kind == "dose") "medication" else kind]) })
                }
            }
            AnimatedContent(selection, Modifier.weight(1f), transitionSpec = {
                if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                else {
                    val direction = (if (targetState > initialState) 1 else -1) * (if (w.rtl) -1 else 1)
                    ((fadeIn(tween(220, 60)) + slideInHorizontally(tween(430, easing = NasaqEase)) { direction * it / 5 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(300)) { -direction * it / 9 })).using(null)
                }
            }, label = "timeline-date") { day ->
                val local = LocalDate.parse(day)
                val at = local.atStartOfDay(zone).toInstant().toEpochMilli()
                val summary = remember(s, day) { dayOverview(s, at, zone) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Timeline(summary, s, filter, local, zone, onEdit)
                    DayOverviewCard(s, summary)
                }
            }
        }
    }
}

private data class TimelineEvent(val time: Long, val entry: Entry? = null, val item: CareItem? = null)

@Composable
private fun Timeline(summary: DayOverview, state: State, filter: String, day: LocalDate, zone: ZoneId, onEdit: ((Entry) -> Unit)?) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val records = summary.entries.filter { filter == "all" || it.kind == filter }.map { TimelineEvent(it.time, entry = it) }
    val care = if (filter == "all") summary.care.map { item ->
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        TimelineEvent(if (item.kind == "sleep") minOf(item.end, end) else item.time, item = item)
    } else emptyList()
    val hours = (records + care).sortedBy { it.time }.groupBy {
        val instant = Instant.ofEpochMilli(it.time).atZone(zone)
        instant.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    }
    if (hours.isEmpty()) NasaqBoard { EmptyState(w["story_empty"]) }
    hours.forEach { (hour, events) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(63.dp).padding(top = 15.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(timeText(hour, w), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = c.primary, modifier = Modifier.size(6.dp)) {}
            }
            NasaqBoard(Modifier.weight(1f)) {
                events.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider()
                    event.entry?.let { entry ->
                        ActivityRow(entry, state, trailing = if (onEdit != null) {{
                            IconButton(onClick = { onEdit(entry) }, modifier = Modifier.size(40.dp)
                                .semantics { contentDescription = w["edit"] + " " + w[if (entry.kind == "dose") "medication" else entry.kind] }) {
                                Glyph("edit", Modifier.size(16.dp))
                            }
                        }} else null)
                    }
                    event.item?.let { item ->
                        val sleep = item.kind == "sleep"
                        val note = if (sleep) timeText(item.time, w) + " → " + timeText(item.end, w)
                            else w[if (item.done) "day_completed" else "day_planned"] + " · " + timeText(item.time, w)
                        OverviewMetric(if (sleep) "moon" else "today", if (sleep) w["actual_sleep"] else item.title, "", note)
                    }
                }
            }
        }
    }
}
