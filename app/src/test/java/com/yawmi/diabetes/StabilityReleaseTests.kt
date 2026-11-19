package com.yawmi.diabetes

import com.yawmi.diabetes.core.BackupCipher
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class StabilityReleaseTests {
    private val stamp = Instant.parse("2026-11-18T09:00:00Z").toEpochMilli()

    @Test fun encryptedBackupFrom080CanRestoreToASecondProfileWithoutReplayingDoses() {
        val medicine = Medication(id = "medicine", name = "Example", dose = "As recorded", stock = 9, stockEpoch = 5)
        val dose = Entry(id = "dose", kind = "dose", time = stamp, medicationId = medicine.id,
            status = "taken", occurrence = "medicine_today", value = 1.0, second = 5.0)
        val reading = Entry(id = "reading", kind = "glucose", time = stamp, unit = "mg/dL", value = 120.0)
        val backup = State(preferences = Preferences(name = "Example", lock = true, theme = "dark", setupVersion = 2),
            entries = listOf(reading, dose), medications = listOf(medicine))
        val legacy = JSONObject(Codec.encode(backup)).apply {
            getJSONObject("preferences").apply {
                remove("pinnedShortcut"); remove("adaptiveSince"); remove("ignoredWaterAmounts")
            }
        }
        val password = "example-backup-password".toCharArray()
        val encrypted = BackupCipher.encrypt(legacy.toString().toByteArray(Charsets.UTF_8), password)
        val decoded = Codec.decode(String(BackupCipher.decrypt(encrypted, password), Charsets.UTF_8))
        val target = prepareRestore(State(), decoded, useSettings = true)
        assertEquals(backup.entries, target.state.entries)
        assertEquals(backup.medications, target.state.medications)
        assertEquals("dark", target.state.preferences.theme)
        assertFalse(target.state.preferences.lock)
        assertEquals(2, target.counts.entries)
        assertEquals(0, prepareRestore(target.state, decoded, true).counts.entries)
    }

    @Test fun conflictingRecordsAndScheduledDoseIdsRetainLocalCopy() {
        val local = Entry(id = "reading", kind = "glucose", time = stamp, unit = "mg/dL", value = 125.0)
        val dose = Entry(id = "a", kind = "dose", time = stamp, occurrence = "morning", status = "taken")
        val state = State(preferences = Preferences(lock = true), entries = listOf(local, dose))
        val other = State(entries = listOf(local.copy(value = 200.0), dose.copy(id = "b", status = "skipped")))
        val plan = prepareRestore(state, other, false)
        assertEquals(2, plan.counts.conflicts)
        assertEquals(state, plan.state)
    }

    @Test fun correctingDoseTimeAfterRestockPreservesInventoryAndSupportsUndo() {
        val medicine = Medication(name = "Example", dose = "1", stock = 30, stockEpoch = 10)
        val old = Entry(kind = "dose", time = stamp, medicationId = medicine.id, occurrence = "morning",
            status = "taken", value = 1.0, second = 5.0)
        val state = State(entries = listOf(old), medications = listOf(medicine))
        val result = Records.replace(state, old, old.copy(time = stamp - 60_000))
        assertEquals(30, result.medications.single().stock)
        assertEquals("morning", result.entries.single().occurrence)
        assertEquals(state, RecordUndo.revert(result, result.entries.single(), old))
    }

    @Test fun repeatedWidgetTapIsIdempotentButNextActionAddsAnotherCup() {
        val firstToken = "54afbba1-436f-46e0-92cc-8bfb16021d68"
        val secondToken = "6fedb4e3-1ed4-4b47-bdf5-69070bcee40c"
        val initial = State()
        val first = WidgetActions.record(initial, 42, firstToken, stamp)
        assertEquals(first, WidgetActions.record(first, 42, firstToken, stamp + 1))
        val twice = WidgetActions.record(first, 42, secondToken, stamp + 2)
        assertEquals(500.0, twice.entries.sumOf(Entry::value), 0.0)
        assertEquals(first, WidgetActions.undo(twice, 42, "widget_$secondToken", stamp + 3))
        assertEquals(twice, WidgetActions.undo(twice, 43, "widget_$secondToken", stamp + 3))
    }

    @Test fun compareFourWeeksPreservesEmptyDaysAndHonorsTheReadingFilter() {
        val day = LocalDate.of(2026, 11, 18)
        val s = State(entries = listOf(Entry(kind = "glucose", context = "before", time = stamp, value = 110.0, unit = "mg/dL"),
            Entry(kind = "glucose", context = "after", time = stamp, value = 140.0, unit = "mg/dL")))
        val results = dailySummaries(s, day, ZoneOffset.UTC, "before", period = 28)
        assertEquals(56, results.size)
        assertEquals(day.minusDays(55), results.first().date)
        assertEquals(55, results.count { !it.hasData })
        assertEquals(110.0, results.last().glucoseMg, 0.0)
    }

    @Test fun localPersonalizationPinHideAndResetSurviveEncoding() {
        val state = State(preferences = Preferences(pinnedShortcut = "meal", ignoredWaterAmounts = setOf(250.0)),
            entries = listOf(1, 2, 3).map { Entry(kind = "water", value = 250.0, unit = "mL", time = stamp - it * 1000) })
        val restored = Codec.decode(Codec.encode(state))
        assertEquals(state, restored)
        assertEquals(JournalHabits(listOf("meal", "water", "glucose", "symptom"), null), journalHabits(restored, stamp, ZoneOffset.UTC))
        val reset = restored.copy(preferences = Preferences(adaptiveSince = stamp))
        assertEquals(JournalHabits(journalActions, null), journalHabits(reset, stamp, ZoneOffset.UTC))
    }

    @Test fun reportNoteIsEscapedAndExcludedWithOtherNotes() {
        val message = "<example>\nline two"
        val included = Reports.buildHtml(State(preferences = Preferences(targetNote = "private-target")), 7,
            ReportOptions(message = message)) { "AAE=" }
        assertTrue(included.contains("<p class='user-note'>&lt;example&gt;\nline two</p>"))
        val hidden = Reports.buildHtml(State(preferences = Preferences(targetNote = "private-target")), 7,
            ReportOptions(notes = false, message = message)) { "AAE=" }
        assertFalse(hidden.contains("&lt;example&gt;"))
        assertFalse(hidden.contains("private-target"))
    }
    @Test fun defaultGlucosePersistsAtMidnightWhileWaterResetsAndDosesDoNotCount() {
        val now = Instant.parse("2026-04-06T23:59:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-04-07T00:00:00Z").toEpochMilli()
        val readings = listOf(
            Entry(kind = "glucose", value = 100.0, unit = "mg/dL", time = Instant.parse("2026-03-09T00:00:00Z").toEpochMilli()),
            Entry(kind = "glucose", value = 140.0, unit = "mg/dL", time = now),
            Entry(kind = "glucose", value = 250.0, unit = "mg/dL", time = Instant.parse("2026-03-07T19:00:00Z").toEpochMilli()),
        )
        val state = State(entries = readings + Entry(kind = "water", time = now, value = 750.0, unit = "mL") +
            Entry(kind = "dose", time = now, value = 12.0, status = "taken"))
        val words = Words("en")
        val before = TrackingStats(state, now, ZoneOffset.UTC)
        val after = TrackingStats(state, midnight, ZoneOffset.UTC)
        assertEquals("120", before.glucose(words).amount)
        assertEquals("120", after.glucose(words).amount)
        assertEquals("Last 30 days", after.glucose(words).period)
        assertEquals("0.75", before.water(words).amount)
        assertEquals("0", after.water(words).amount)
        assertEquals(midnight, nextWidgetRefresh(now, ZoneOffset.UTC))
    }

    @Test fun trackingPreferencesRoundTripAndEachMetricUsesItsOwnInputs() {
        val now = Instant.parse("2026-04-07T15:00:00Z").toEpochMilli()
        val p = Preferences(glucoseViewDays = 7, glucoseViewMetric = "latest", glucoseViewContext = "fasting",
            waterViewDays = 14, waterViewMetric = "daily_average", waterViewUnit = "mL")
        val state = State(preferences = p, entries = listOf(
            Entry(kind = "glucose", value = 6.0, unit = "mmol/L", time = now - 1000, context = "fasting"),
            Entry(kind = "glucose", value = 160.0, unit = "mg/dL", time = now, context = "after"),
            Entry(kind = "glucose", value = 190.0, unit = "mg/dL", time = now + 1000, context = "fasting"),
            Entry(kind = "water", value = 250.0, unit = "mL", time = now - 2000),
            Entry(kind = "water", value = 500.0, unit = "mL", time = now),
            Entry(kind = "water", value = 1000.0, unit = "mL", time = now - 2 * 86_400_000L),
        ))
        val restored = Codec.decode(Codec.encode(state))
        assertEquals(p, restored.preferences)
        val words = Words("en")
        val stats = TrackingStats(restored, now, ZoneOffset.UTC)
        assertEquals("108", stats.glucose(words).amount)
        assertEquals(1, stats.glucoseReadings.size)
        assertEquals("875", stats.water(words).amount)
        assertEquals("Days with water logged: 2", stats.water(words).detail)
        val withoutOptions = JSONObject(Codec.encode(State())).apply {
            val stored = getJSONObject("preferences")
            listOf("glucoseViewDays", "glucoseViewMetric", "glucoseViewContext",
                "waterViewDays", "waterViewMetric", "waterViewUnit").forEach(stored::remove)
        }
        val legacy = Codec.decode(withoutOptions.toString()).preferences
        assertEquals(30, legacy.glucoseViewDays)
        assertEquals("average", legacy.glucoseViewMetric)
        assertEquals(1, legacy.waterViewDays)
        assertEquals("total", legacy.waterViewMetric)
    }

}
