package com.yawmi.diabetes

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

internal val journalActions = listOf("glucose", "water", "meal", "symptom")

internal data class JournalHabits(val actions: List<String>, val waterAmount: Double?, val waterMatches: Int = 0)

internal fun journalHabits(state: State, now: Long, zone: ZoneId = ZoneId.systemDefault()): JournalHabits {
    val p = state.preferences
    val manual = if (p.pinnedShortcut in journalActions) listOf(p.pinnedShortcut) else emptyList()
    fun priority(base: List<String>) = (manual + base).distinct()
    if (!p.adaptive) return JournalHabits(priority(journalActions), null)
    val localHour = Instant.ofEpochMilli(now).atZone(zone).hour
    val from = maxOf(p.adaptiveSince, now - 30L * 86_400_000)
    val samples = state.entries.asSequence().filter { it.kind in journalActions && it.time in from..now }.toList()
    if (samples.size < 3) return JournalHabits(priority(journalActions), null)
    fun weight(entry: Entry): Double {
        val hour = Instant.ofEpochMilli(entry.time).atZone(zone).hour
        val distance = abs(hour - localHour).let { minOf(it, 24 - it) }
        val age = (now - entry.time) / 86_400_000.0
        return (if (distance <= 2) 2.0 else 1.0) / (1 + age / 7.0)
    }
    val ranks = samples.groupBy(Entry::kind).mapValues { (_, entries) -> entries.sumOf(::weight) }
    val amounts = samples.filter { it.kind == "water" && it.value in 1.0..5000.0 && it.value !in p.ignoredWaterAmounts }.groupBy(Entry::value)
    val typical = amounts.entries.filter { it.value.size >= 2 }.maxByOrNull { it.value.sumOf(::weight) }
    return JournalHabits(priority(journalActions.sortedByDescending { ranks[it] ?: 0.0 }), typical?.key, typical?.value?.size ?: 0)
}
