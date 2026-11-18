package com.yawmi.diabetes

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

internal val journalActions = listOf("glucose", "water", "meal", "symptom")

internal data class JournalHabits(val actions: List<String>, val waterAmount: Double?)

internal fun journalHabits(state: State, now: Long, zone: ZoneId = ZoneId.systemDefault()): JournalHabits {
    if (!state.preferences.adaptive) return JournalHabits(journalActions, null)
    val localHour = Instant.ofEpochMilli(now).atZone(zone).hour
    val from = now - 30L * 86_400_000
    val samples = state.entries.asSequence().filter { it.kind in journalActions && it.time in from..now }.toList()
    if (samples.size < 3) return JournalHabits(journalActions, null)
    fun weight(entry: Entry): Double {
        val hour = Instant.ofEpochMilli(entry.time).atZone(zone).hour
        val distance = abs(hour - localHour).let { minOf(it, 24 - it) }
        val age = (now - entry.time) / 86_400_000.0
        return (if (distance <= 2) 2.0 else 1.0) / (1 + age / 7.0)
    }
    val ranks = samples.groupBy(Entry::kind).mapValues { (_, entries) -> entries.sumOf(::weight) }
    val amounts = samples.filter { it.kind == "water" && it.value in 1.0..5000.0 }.groupBy(Entry::value)
    val typical = amounts.entries.filter { it.value.size >= 2 }.maxByOrNull { it.value.sumOf(::weight) }?.key
    return JournalHabits(journalActions.sortedByDescending { ranks[it] ?: 0.0 }, typical)
}
