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
    cup: () -> Unit, notifications: () -> Unit, customise: () -> Unit = {},
    permissionRevision: Int = 0, openOverview: () -> Unit = {},
    openHydration: () -> Unit = { add("water") }, openGlucose: () -> Unit = { add("glucose") },
    openMedicines: () -> Unit = {}
) {
    val w = LocalWords.current
    val p = s.preferences
    val now = trackingTime()
    val stats = remember(s, now) { TrackingStats(s, now) }
    val summary = remember(s, now) { dayOverview(s, now) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val alertsAllowed = remember(permissionRevision) { Reminders.permitted(context) }
    val metrics = p.order.filter { it !in p.hiddenCards && it in listOf("glucose", "water", "medications") }
    val recent = summary.entries.filter { entry ->
        when (entry.kind) {
            "meal" -> "meals" !in p.hiddenCards
            "symptom" -> "symptoms" !in p.hiddenCards
            "dose" -> "medications" !in p.hiddenCards
            else -> entry.kind !in p.hiddenCards
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenGutters),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(w["today"], dateText(now, w)) {
            IconAction(onClick = customise, modifier = Modifier.semantics { contentDescription = w["home_cards"] }) {
                Glyph("settings", Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DailyAction("glucose", w["add_reading"], null, !busy, { add("glucose") }, Modifier.weight(1f).fillMaxHeight())
            DailyAction("water", w["log_cup_short"], number(p.cup.toDouble(), w) + " " + w["ml"],
                !busy, cup, Modifier.weight(1f).fillMaxHeight())
        }
        p.pinnedShortcut.takeIf { it in journalActions && it !in primaryJournalActions }?.let { shortcut ->
            SettingsRow(shortcut, w[shortcut]) { add(shortcut) }
        }
        val displayed = if (p.compactHome) metrics else p.order.filterNot { it in p.hiddenCards }
        fun openMetric(kind: String): () -> Unit = when (kind) {
            "water" -> openHydration
            "medications" -> openMedicines
            else -> openGlucose
        }
        if (p.compactHome && displayed.isNotEmpty()) NasaqBoard {
            displayed.forEachIndexed { index, kind ->
                if (index > 0) HorizontalDivider()
                DeskMetricLine(kind, deskMetric(kind, stats, summary, w), openMetric(kind))
            }
        } else displayed.forEachIndexed { index, kind ->
            Reveal(index) {
                when (kind) {
                    "meals" -> NasaqBoard {
                        SettingsRow("log", w["today_story"],
                            w.f("today_entries_count", "count" to number(summary.activityCount.toDouble(), w)), onClick = openOverview)
                        val limit = when (p.cardSizes[kind]) { "small" -> 1; "large" -> 5; else -> 3 }
                        recent.take(limit).forEach { entry -> ActivityRow(entry, s) }
                    }
                    "symptoms" -> if (p.sickPlan.isNotBlank() || recent.any { it.kind == "symptom" }) {
                        val note = if (p.cardSizes[kind] == "small") null else w["care_corner_body"]
                        NasaqBoard { SettingsRow("shield", w["care_corner"], note, onClick = sick) }
                    }
                    else -> DeskMetricCard(kind, deskMetric(kind, stats, summary, w), stats, s, openMetric(kind))
                }
            }
        }
        if (p.compactHome || "meals" !in displayed) {
            NasaqBoard {
                SettingsRow("log", w["day_overview"],
                    w.f("today_entries_count", "count" to number(summary.activityCount.toDouble(), w)), onClick = openOverview)
            }
        }

        if (!alertsAllowed && (p.waterEnabled || p.glucoseEnabled || s.medications.any { it.active && it.reminders })) {
            SettingsRow("bell", w["reminder_no_permission"], onClick = notifications)
        }
        NextActionCard(s, now, { reminder ->
            when (reminder.kind) {
                "medication" -> dose(reminder)
                "water" -> openHydration()
                "glucose" -> add("glucose")
                else -> add("care_tools")
            }
        }, notifications, showWhenEmpty = false)
    }
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

