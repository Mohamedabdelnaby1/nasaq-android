package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DailyFlowTests {
    private val now = Instant.parse("2026-11-19T12:00:00Z").toEpochMilli()

    @Test fun firstCupAndOnboardingAreCommittedTogetherAndRetriedSafely() {
        val fresh = State(preferences = Preferences(cup = 350, waterGoal = 2300))
        val started = startTracking(fresh, true, "first-cup", now)
        assertTrue(started.preferences.onboarded)
        assertEquals(2, started.preferences.setupVersion)
        assertEquals(2300, started.preferences.waterGoal)
        val entry = started.entries.single()
        assertEquals(cupEntry(fresh.preferences, now, "first-cup"), entry)
        assertEquals(started, startTracking(started, true, "second-tap", now + 1))
        assertTrue(startTracking(fresh, false, "first", now).entries.isEmpty())
        assertTrue(RecordUndo.revert(started, entry, null).entries.isEmpty())
    }

    @Test fun dailyWaterAndHistoricalViewHaveIndependentTotals() {
        val s = State(preferences = Preferences(language = "en", waterGoal = 1000, waterViewDays = 7, waterViewMetric = "daily_average"),
            entries = listOf(
                Entry(kind = "water", value = 1000.0, time = now - 86_400_000),
                Entry(kind = "water", value = 1500.0, time = now),
                Entry(kind = "water", value = 4000.0, time = now + 1)
            ))
        val stats = TrackingStats(s, now, ZoneOffset.UTC)
        assertEquals(1500.0, stats.todayWaterMl, 0.0)
        assertEquals(1.5, stats.waterGoalProgress!!, 0.0)
        assertEquals("1.5 L", stats.waterToday(Words("en")).value)
        assertEquals("1.25 L", stats.water(Words("en")).value)
        assertTrue(stats.waterToday(Words("en")).detail.contains("1 L"))
    }

    @Test fun automaticWidgetActionFollowsAnAvailableHabitAndHonorsTheExplicitAction() {
        val s = State(entries = List(3) { cupEntry(Preferences(), now - (it + 1) * 60_000) })
        val order = journalHabits(s, now, ZoneOffset.UTC).actions
        assertEquals("water", order.first())
        assertEquals("water", WidgetLayout.calculate(320, 242, WidgetLook(), true, order).footerKind)
        assertEquals("medication", WidgetLayout.calculate(320, 242, WidgetLook(actionTarget = "medication"), true, order).footerKind)
    }

    @Test fun assistantPreservesRelativeTimeAndCupsInTwoLanguages() {
        val p = Preferences(cup = 350)
        val ar = JournalLanguage.parse("شربت كوبايتين من ساعة", p, now) as JournalRequest.Draft
        assertEquals(700.0, ar.entry.value, 0.0)
        assertEquals(now - 3_600_000, ar.entry.time)
        assertEquals(now, ar.entry.created)
        val en = JournalLanguage.parse("I drank 250 ml 15 minutes ago", p, now) as JournalRequest.Draft
        assertEquals(250.0, en.entry.value, 0.0)
        assertEquals(now - 900_000, en.entry.time)
        val glucose = JournalLanguage.parse("سكر ١٢٠ قبل الفطار من عشر دقايق", p, now) as JournalRequest.Draft
        assertEquals("before", glucose.entry.context)
        assertEquals(now - 600_000, glucose.entry.time)
        assertNull(JournalLanguage.parse("شربت كوبايتين من ساعة ونص", p, now))
        assertNull(JournalLanguage.parse("I drank 2 cups 250 ml", p, now))
        assertNull(JournalLanguage.parse("شربت كام كوباية", p, now))
    }

    @Test fun visitSummaryUsesTheSameCutoffAndCanOmitTheDetailPage() {
        val s = State(preferences = Preferences(language = "en", name = "private profile"),
            entries = listOf(
                Entry(kind = "glucose", value = 120.0, unit = "mg/dL", time = now - 3000),
                Entry(kind = "glucose", value = 240.0, unit = "mg/dL", time = now + 1),
                Entry(kind = "water", value = 1_500.0, unit = "mL", time = now - 1000)
            ))
        val snapshot = Reports.snapshot(s, 7, now)
        assertEquals(120.0, snapshot.averageMg!!, 0.0)
        assertEquals(1, snapshot.glucose.size)
        assertEquals(1500.0, snapshot.waterMl, 0.0)
        val brief = ReportOptions(showName = false, chart = false, journal = false, plans = false, notes = false)
        val html = Reports.buildHtml(s, 7, brief, now) { "AAE=" }
        assertTrue(html.contains("class='visit-overview'"))
        assertFalse(html.contains("class='report-detail'"))
        assertFalse(html.contains("private profile"))
        val detailed = Reports.buildHtml(s, 7, brief.copy(journal = true), now) { "AAE=" }
        assertTrue(detailed.indexOf("class='visit-overview'") < detailed.indexOf("class='report-detail'"))
    }

    @Test fun onlyDueAndOutstandingMedicationRemindersCanBeDeferred() {
        val due = LocalDate.of(2026, 11, 19).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val medicine = Medication(name = "Test plan", dose = "Test dose")
        val s = State(medications = listOf(medicine))
        val reminder = Reminders.plan(s, due, due + 1000).single { it.kind == "medication" }
        assertFalse(Reminders.canDeferDose(s, reminder, due - 1))
        assertTrue(Reminders.canDeferDose(s, reminder, due + 60_000))
        assertFalse(Reminders.canDeferDose(s, reminder, due + 7_200_000))
        val taken = Entry(kind = "dose", occurrence = reminder.occurrence, status = "taken")
        assertFalse(Reminders.canDeferDose(s.copy(entries = listOf(taken)), reminder, due + 60_000))
    }
}

