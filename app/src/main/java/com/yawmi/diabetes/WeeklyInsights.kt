@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.yawmi.diabetes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

data class DaySummary(val date:LocalDate,val readings:Int,val water:Double,val doses:Int,val sleepHours:Double,val hasData:Boolean,val glucoseMg: Double = 0.0)

fun dailySummaries(s:State,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault(), context: String = "all", period: Int = 7):List<DaySummary> {
    fun day(millis:Long)=Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    require(period in setOf(7, 14, 28))
    val oldest = period * 2 - 1
    val first = today.minusDays(oldest.toLong())
    val entries=s.entries.filter{day(it.time) in first..today}.groupBy{day(it.time)}
    val sleep=s.care.items.filter{it.kind=="sleep"}.groupBy{day(it.end)}
    return (oldest downTo 0).map{offset->
        val date=today.minusDays(offset.toLong())
        val records=entries[date].orEmpty()
        val sessions=sleep[date].orEmpty()
        val glucose = records.filter { it.kind == "glucose" && (context == "all" || it.context == context) }
        DaySummary(date,glucose.size,records.filter{it.kind=="water"}.sumOf{it.value},
            records.count{it.kind=="dose" && it.status=="taken"},sessions.sumOf{(it.end-it.time)/3_600_000.0},records.isNotEmpty()||sessions.isNotEmpty(), glucose.sumOf(::mg))
    }
}

@Composable fun WeeklyInsights(s: State, context: String = "all", onOpenDay: ((LocalDate) -> Unit)? = null) {
    val w = LocalWords.current
    val colors = MaterialTheme.colorScheme
    val periods = listOf(7, 14, 28)
    var period by rememberSaveable { mutableIntStateOf(7) }
    val today = LocalDate.now()
    val summaries = remember(s.entries, s.care.items, today, context, period) { dailySummaries(s, today, context = context, period = period) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val current = summaries.takeLast(period)
    val previous = summaries.take(period)
    val focus = summaries.firstOrNull { it.date.toString() == selected } ?: current.last()
    val formatter = DateTimeFormatter.ofPattern("d MMM", w.locale)
    fun dates(days: List<DaySummary>) = days.first().date.format(formatter) + " – " + days.last().date.format(formatter)
    Panel(w["weekly_review"]) {
        SegmentedRail(periods.map { w.f("compare_days", "days" to number(it.toDouble(), w)) }, periods.indexOf(period), { period = periods[it] })
        Text(w["calendar_hint"], style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        for ((index, week) in listOf(previous, current).withIndex()) {
            Text(w[if (index == 0) "compare_previous" else "compare_current"] + " · " + dates(week),
                style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val color = when {
                        day.date == focus.date -> colors.primary
                        day.hasData -> colors.primaryContainer
                        else -> colors.surfaceContainer
                    }
                    val label = day.date.format(formatter) + " · " + if (day.hasData) summaryLine(day, w) else w["missing_data"]
                    Surface(onClick = { selected = day.date.toString() }, modifier = Modifier.sizeIn(minWidth = 42.dp, minHeight = 46.dp)
                        .semantics { contentDescription = label }, shape = RoundedCornerShape(9.dp), color = color) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(number(day.date.dayOfMonth.toDouble(), w), color = if (day.date == focus.date) colors.onPrimary else colors.onSurface,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        val date = focus.date.format(formatter)
        Text(date + " · " + if (focus.hasData) summaryLine(focus, w) else w["missing_data"], style = MaterialTheme.typography.bodySmall)
        if (onOpenDay != null) TextButton(onClick = { onOpenDay(focus.date) }) { Text(w["open_selected_day"]) }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text(w["week_metric"],Modifier.weight(1.2f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium)
            Text(w["compare_previous"],Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
            Text(w["compare_current"],Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        }
        fun average(week: List<DaySummary>): String {
            val count = week.sumOf { it.readings }
            if (count == 0) return "—"
            val unit = s.preferences.unit
            return number(com.yawmi.diabetes.core.Planner.fromMg(week.sumOf { it.glucoseMg } / count, unit), w,
                if (unit == "mmol/L") 1 else 0) + " " + unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(w["average_short"], Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
            Text(average(previous), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(average(current), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        val rows=listOf(
            Triple("readings",previous.sumOf{it.readings}.toDouble(),current.sumOf{it.readings}.toDouble()),
            Triple("water",previous.sumOf{it.water}/1000,current.sumOf{it.water}/1000),
            Triple("doses_recorded",previous.sumOf{it.doses}.toDouble(),current.sumOf{it.doses}.toDouble()),
            Triple("logged_days",previous.count{it.hasData}.toDouble(),current.count{it.hasData}.toDouble())
        )
        rows.forEach{(key,old,new)->
            val suffix=if(key=="water")" "+w["litres_short"]else""
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text(w[key],Modifier.weight(1.2f),style=MaterialTheme.typography.labelMedium)
                Text(number(old,w,2)+suffix,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                Text(number(new,w,2)+suffix,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold)
            }
        }
        Text(w["weekly_limits"],style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
    }
}

private fun summaryLine(day:DaySummary,w:Words):String {
    val measures=listOf(
        w["readings"]+" "+number(day.readings.toDouble(),w),
        w["water"]+" "+number(day.water/1000,w,2)+" "+w["litres_short"],
        w["doses_recorded"]+" "+number(day.doses.toDouble(),w)
    )
    return (measures+listOfNotNull(if(day.sleepHours>0)w["actual_sleep"]+" "+number(day.sleepHours,w,1)+" "+w["hours"]else null)).joinToString(" · ")
}
