package com.yawmi.diabetes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yawmi.diabetes.core.Planner
import java.time.Instant
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
fun DayOverviewScreen(s: State, onClose: () -> Unit) {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val summary = dayOverview(s, now)
    data class Event(val time: Long, val record: Entry? = null, val item: CareItem? = null)
    val timeline = (summary.entries.map { Event(it.time, record = it) } +
        summary.care.map { Event(if (it.kind == "sleep") it.end else it.time, item = it) }).sortedByDescending { it.time }
    Scaffold(containerColor = c.background) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenTitle(w["day_detail_title"], dateText(now, w)) {
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = w["close"] }) {
                    Glyph("back", Modifier.size(22.dp))
                }
            }
            DayOverviewCard(s, summary)
            Panel(w["day_activity"]) {
                if (timeline.isEmpty()) EmptyState(w["story_empty"])
                timeline.forEachIndexed { index, event ->
                    event.record?.let { ActivityRow(it, s) }
                    event.item?.let { item ->
                        val title = if (item.kind == "sleep") w["actual_sleep"] else item.title
                        val note = if (item.kind == "sleep") dateText(item.time, w) + " " + timeText(item.time, w) + " → " + timeText(item.end, w)
                            else w[if (item.done) "day_completed" else "day_planned"] + " · " + timeText(item.time, w)
                        OverviewMetric(if (item.kind == "sleep") "moon" else "today", title, "", note)
                    }
                    if (index < timeline.lastIndex) HorizontalDivider(color = c.outlineVariant.copy(alpha = .45f))
                }
            }
        }
    }
}