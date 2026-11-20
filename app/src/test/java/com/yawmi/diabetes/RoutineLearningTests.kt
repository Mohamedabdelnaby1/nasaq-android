package com.yawmi.diabetes

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RoutineLearningTests {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 12)
    private fun stamp(daysAgo: Long, hour: Int, minute: Int = 0): Long =
        today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(daysAgo: Long, hour: Int, minute: Int = 0, kind: String = "water", amount: Double = 250.0): Entry =
        Entry(kind = kind, time = stamp(daysAgo, hour, minute), value = amount, unit = if (kind == "water") "mL" else "mg/dL")

    private val waterPreferences = Preferences(waterEnabled = true, waterSmart = true, waterGoal = 1000,
        autoWaterTiming = true, lastZone = zone.id)

    @Test fun learnsFromDistinctCompletedDaysAndBoundsAutomaticChange() {
        val history = (1L..5L).map { entry(it, 9, 5) } + entry(6, 7) +
            listOf(entry(0, 7), entry(1, 12), entry(-1, 9))
        val state = State(preferences = waterPreferences, entries = history)
        val learned = routineLearning(state, stamp(0, 10), zone)
        assertEquals(RoutineTiming(120, 5, 6), learned.water)
        assertEquals(90, learned.waterOffset)
        assertTrue(learned.appliesWater)
        val unapproved = routineLearning(state.copy(preferences = waterPreferences.copy(autoWaterTiming = false)), stamp(0, 10), zone)
        assertEquals(0, unapproved.waterOffset)
        assertFalse(unapproved.appliesWater)
    }

    @Test fun inconsistentOrResetHistoryKeepsTheConfiguredWaterTiming() {
        val history = (1L..4L).map { entry(it, 7) } + (5L..8L).map { entry(it, 10) }
        assertNull(routineLearning(State(waterPreferences, history), stamp(0, 12), zone).water)
        val recentOnly = waterPreferences.copy(waterOffset = 30, waterLearningSince = stamp(2, 8))
        val state = State(recentOnly, (1L..6L).map { entry(it, 9) })
        assertEquals(30, routineLearning(state, stamp(0, 12), zone).waterOffset)
        assertNull(routineLearning(state.copy(preferences = recentOnly.copy(fluidRestricted = true)), stamp(0, 12), zone).water)
    }

    @Test fun automaticTimingDoesNotChangeGlucoseMedicationOrReminderIdentity() {
        val state = State(
            preferences = waterPreferences.copy(glucoseEnabled = true, glucoseTimes = listOf("08:00")),
            entries = (1L..5L).map { entry(it, 8) },
            medications = listOf(Medication(name = "Sample dose", dose = "As prescribed", times = listOf("08:00")))
        )
        val from = stamp(0, 7)
        val until = stamp(0, 21)
        val automatic = Reminders.plan(state, from, until)
        val fixed = Reminders.plan(state.copy(preferences = state.preferences.copy(autoWaterTiming = false)), from, until)
        assertEquals(fixed.filter { it.kind != "water" }, automatic.filter { it.kind != "water" })
        assertTrue(fixed.any { it.kind == "medication" })
        assertTrue(fixed.any { it.kind == "glucose" })
        assertEquals(fixed.first { it.kind == "water" }.id, automatic.first { it.kind == "water" }.id)
        assertEquals(stamp(0, 8), automatic.first { it.kind == "water" }.due)
    }

    @Test fun recentAndCompletedWaterEntriesPreventRedundantPromptsWithoutLookingAhead() {
        val due = stamp(0, 12)
        val logged = State(waterPreferences, listOf(entry(0, 11, 40), entry(0, 14, amount = 1000.0)))
        assertTrue(smartWaterAlreadyCovered(logged, due, zone))
        assertFalse(smartWaterAlreadyCovered(logged.copy(preferences = waterPreferences.copy(autoWaterTiming = false)), due, zone))
        assertFalse(smartWaterAlreadyCovered(logged.copy(preferences = waterPreferences.copy(fluidRestricted = true)), due, zone))
        val reached = logged.copy(entries = listOf(entry(0, 8, amount = 1000.0)))
        assertTrue(smartWaterAlreadyCovered(reached, due, zone))
    }

    @Test fun oldSettingsDoNotEnableAutomaticTimingAndNewSettingsRoundTrip() {
        val p = waterPreferences.copy(waterLearningSince = stamp(2, 8))
        val encoded = Codec.encode(State(preferences = p))
        assertEquals(p, Codec.decode(encoded).preferences)
        val legacy = JSONObject(encoded)
        legacy.getJSONObject("preferences").remove("autoWaterTiming")
        legacy.getJSONObject("preferences").remove("waterLearningSince")
        val restored = Codec.decode(legacy.toString()).preferences
        assertFalse(restored.autoWaterTiming)
        assertEquals(0L, restored.waterLearningSince)
    }
}

