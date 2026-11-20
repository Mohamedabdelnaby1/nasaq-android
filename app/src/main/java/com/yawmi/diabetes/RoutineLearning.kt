package com.yawmi.diabetes

import com.yawmi.diabetes.core.Planner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val waterTimingShiftLimit = 90
private const val learningDays = 21L
private const val windowMinutes = 240
private const val minimumDays = 4
private const val toleranceMinutes = 35
private const val recentWaterMinutes = 45

internal data class RoutineTiming(val minutesAfterWake: Int, val matchingDays: Int, val recordedDays: Int) {
    fun timeAfter(wake: LocalTime): LocalTime = wake.plusMinutes(minutesAfterWake.toLong())
}

internal data class RoutineLearning(
    val water: RoutineTiming?,
    val glucose: RoutineTiming?,
    val waterOffset: Int,
    val appliesWater: Boolean
)

internal fun routineLearning(s: State, now: Long, zone: ZoneId = ZoneId.systemDefault()): RoutineLearning {
    val p = s.preferences
    val wake = LocalTime.parse(p.quietEnd)
    val sleep = LocalTime.parse(p.quietStart)
    val current = Instant.ofEpochMilli(now).atZone(zone)
    val activeDay = if (current.toLocalTime().isBefore(wake)) current.toLocalDate().minusDays(1) else current.toLocalDate()
    val end = activeDay.atTime(wake).atZone(zone).toInstant().toEpochMilli()
    val oldest = activeDay.minusDays(learningDays).atTime(wake).atZone(zone).toInstant().toEpochMilli()

    fun estimate(kind: String, enabledDays: Set<Int>, reset: Long): RoutineTiming? {
        val firstByDay = mutableMapOf<LocalDate, Int>()
        s.entries.asSequence().filter { it.kind == kind && it.time >= maxOf(oldest, reset) && it.time < end }
            .forEach { entry ->
                val logged = Instant.ofEpochMilli(entry.time).atZone(zone)
                val clock = logged.toLocalTime()
                val day = if (clock.isBefore(wake)) logged.toLocalDate().minusDays(1) else logged.toLocalDate()
                val sinceWake = Duration.between(day.atTime(wake).atZone(zone).toInstant(), logged.toInstant()).toMinutes()
                if (logged.dayOfWeek.value in enabledDays && sinceWake in 0L..windowMinutes.toLong() &&
                    !Planner.quiet(clock, sleep, wake)) {
                    firstByDay.merge(day, sinceWake.toInt(), ::minOf)
                }
            }
        val times = firstByDay.values.sorted()
        if (times.size < minimumDays) return null
        val middle = (times[times.size / 2] + times[(times.size - 1) / 2]) / 2.0
        val rounded = (middle / 15).roundToInt() * 15
        val matching = times.count { abs(it - rounded) <= toleranceMinutes }
        return if (matching >= minimumDays && matching * 3 >= times.size * 2)
            RoutineTiming(rounded, matching, times.size) else null
    }

    val waterEligible = p.waterEnabled && p.waterSmart && p.waterGoal > 0 && !p.fluidRestricted && p.lastZone == zone.id
    val water = if (waterEligible) estimate("water", p.waterDays, p.waterLearningSince) else null
    val glucose = if (p.adaptive) estimate("glucose", (1..7).toSet(), p.adaptiveSince) else null
    val appliesWater = waterEligible && p.autoWaterTiming && water != null
    val offset = if (appliesWater) {
        checkNotNull(water).minutesAfterWake.coerceIn(
            (p.waterOffset - waterTimingShiftLimit).coerceAtLeast(0),
            (p.waterOffset + waterTimingShiftLimit).coerceAtMost(windowMinutes)
        )
    } else p.waterOffset
    return RoutineLearning(water, glucose, offset, appliesWater)
}

internal fun smartWaterAlreadyCovered(s: State, due: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val p = s.preferences
    val startOfDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val recent = due - recentWaterMinutes * 60_000L
    var total = 0.0
    for (entry in s.entries) {
        if (entry.kind != "water" || entry.time > due) continue
        if (p.autoWaterTiming && !p.fluidRestricted && p.lastZone == zone.id && entry.time >= recent) return true
        if (entry.time >= startOfDate) total += entry.value
    }
    return p.waterGoal > 0 && total >= p.waterGoal
}

