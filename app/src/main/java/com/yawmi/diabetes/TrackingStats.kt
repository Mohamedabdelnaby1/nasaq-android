package com.yawmi.diabetes

import androidx.compose.runtime.*
import com.yawmi.diabetes.core.Planner
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

internal val trackingDays = listOf(1, 7, 14, 30, 90, 0)
internal val glucoseMetrics = listOf("average", "latest")
internal val waterMetrics = listOf("total", "daily_average")
internal val glucoseContexts = listOf("all", "fasting", "not_fasting", "before", "after", "other")

internal data class MetricDisplay(val label: String, val amount: String, val unit: String, val period: String, val detail: String) {
    val value get() = if (unit.isEmpty()) amount else "$amount $unit"
    val description get() = listOf(label, value, period, detail).filter(String::isNotBlank).joinToString(". ")
}

internal class TrackingStats(s: State, now: Long, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val p = s.preferences
    private val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private fun start(days: Int) = if (days == 0) Long.MIN_VALUE
        else date.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    private val glucoseStart = start(p.glucoseViewDays)
    private val waterStart = start(p.waterViewDays)
    val glucoseReadings = s.entries.filter {
        it.kind == "glucose" && it.time in glucoseStart..now &&
            (p.glucoseViewContext == "all" || it.context == p.glucoseViewContext)
    }.sortedByDescending(Entry::time)

    private val latest = glucoseReadings.firstOrNull()
    private val glucoseMg = if (p.glucoseViewMetric == "latest") latest?.let(::mg)
        else glucoseReadings.takeIf { it.isNotEmpty() }?.map(::mg)?.average()
    private val waterByDay = s.entries.asSequence()
        .filter { it.kind == "water" && it.time in waterStart..now }
        .groupingBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
        .fold(0.0) { total, entry -> total + entry.value }
    private val waterMl = if (p.waterViewMetric == "daily_average")
        waterByDay.values.takeIf { it.isNotEmpty() }?.average()
        else waterByDay.values.sum()
    val todayWaterMl = waterByDay[date] ?: 0.0
    val waterGoalProgress = p.waterGoal.takeIf { it > 0 }?.let { todayWaterMl / it }

    fun waterToday(w: Words): MetricDisplay {
        val (amount, unit) = waterVolume(todayWaterMl, p.waterViewUnit, w)
        val goal = p.waterGoal.takeIf { it > 0 }?.let {
            val (value, measure) = waterVolume(it.toDouble(), p.waterViewUnit, w)
            w.f("water_progress_goal", "goal" to "$value $measure")
        }.orEmpty()
        return MetricDisplay(w["water_today_short"], amount, unit, "", goal)
    }

    fun glucose(w: Words): MetricDisplay {
        val average = p.glucoseViewMetric == "average"
        val detail = when {
            !average && latest != null -> dateText(latest.time, w) + " · " + timeText(latest.time, w)
            else -> w.f("day_reading_count", "count" to number(glucoseReadings.size.toDouble(), w))
        }
        val period = trackingPeriod(p.glucoseViewDays, w)
        return MetricDisplay(w[if (average) "widget_glucose_average" else "latest_glucose_view"],
            glucoseMg?.let { number(Planner.fromMg(it, p.unit), w, if (p.unit == "mmol/L") 1 else 0) } ?: "—",
            if (glucoseMg != null) p.unit else "", period, detail)
    }

    fun water(w: Words): MetricDisplay {
        val average = p.waterViewMetric == "daily_average"
        val today = !average && p.waterViewDays == 1
        val (amount, unit) = waterVolume(waterMl, p.waterViewUnit, w)
        return MetricDisplay(w[when { today -> "water_today_short"; average -> "widget_water_average"; else -> "water_total_view" }],
            amount, unit, if (today) "" else trackingPeriod(p.waterViewDays, w),
            if (average) w.f("water_logged_days_view", "count" to number(waterByDay.size.toDouble(), w)) else "")
    }
}

internal fun waterVolume(ml: Double?, unit: String, w: Words): Pair<String, String> = when {
    ml == null -> "—" to ""
    unit == "mL" -> number(ml, w, 1) to w["ml"]
    else -> number(ml / 1000, w, 2) to w["litres_short"]
}

internal fun trackingPeriod(days: Int, w: Words): String = when (days) {
    0 -> w["all_time_view"]
    1 -> w["today_only"]
    else -> w.f("days_view", "days" to number(days.toDouble(), w))
}

internal fun nextWidgetRefresh(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
    return minOf(now + 43_200_000L, tomorrow.atStartOfDay(zone).toInstant().toEpochMilli())
}

@Composable
internal fun trackingTime(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}
