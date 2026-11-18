package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class NextReleaseTests {
    @Test fun clockUsesTwelveHoursWhileSavedTimesStayStable() {
        val w = Words("en")
        assertEquals("12:00 AM", clockText(LocalTime.MIDNIGHT, w))
        assertEquals("12:00 PM", clockText(LocalTime.NOON, w))
        assertEquals("10:15 PM", clockText("22:15", w))
        assertTrue(clockText("22:15", Words("ar")).endsWith(" م"))
        assertEquals(listOf("08:00", "22:15"), parseTimes("22:15,08:00"))
    }

    @Test fun csvHandlesQuotedNotesAndPreviewsDuplicatesWithoutOverwritingExistingEntries() {
        val time = "2026-11-17T09:30:00Z"
        val file = "id,measured_at_iso,kind,value,unit,note\r\n" +
            "water-01,$time,water,250,mL,\"a, b\nsecond \"\"line\"\"\"\r\n" +
            "water-01,$time,water,250,mL,duplicate\r\n" +
            "invalid,$time,water,9900,mL,out of range\r\n"
        val preview = CsvImport.read(file.reader(), now = Instant.parse("2026-11-18T00:00:00Z").toEpochMilli())
        assertEquals(1, preview.invalid)
        assertEquals(2, preview.entries.size)
        val imported = preview.additions(State()).single()
        assertEquals("a, b\nsecond \"line\"", imported.note)
        assertTrue(preview.additions(State(entries = listOf(imported.copy(note = "local edit")))).isEmpty())
        assertTrue(preview.additions(State(entries = listOf(imported.copy(id = "already-logged")))).isEmpty())
    }

    @Test fun suggestionsUseRepeatedLoggedAmountsAndCanBeTurnedOff() {
        val now = Instant.parse("2026-11-18T10:30:00Z").toEpochMilli()
        val events = listOf(1, 2, 7).map { days -> Entry(kind = "water", unit = "mL", value = 350.0, time = now - days * 86_400_000L) } +
            Entry(kind = "glucose", unit = "mg/dL", value = 110.0, time = now - 43_200_000)
        val state = State(entries = events)
        assertEquals("water", journalHabits(state, now, ZoneOffset.UTC).actions.first())
        assertEquals(350.0, journalHabits(state, now, ZoneOffset.UTC).waterAmount!!, .001)
        assertEquals(JournalHabits(journalActions, null), journalHabits(state.copy(preferences = Preferences(adaptive = false)), now, ZoneOffset.UTC))
    }

    @Test fun reportHidesNamedSectionsAndPrivateFieldsWhenDisabled() {
        val state = State(preferences = Preferences(language = "en", name = "private profile"),
            entries = listOf(Entry(kind = "water", value = 200.0, unit = "mL", note = "private note")))
        val report = Reports.buildHtml(state, 7, ReportOptions(showName = false, chart = false, plans = false, notes = false)) { "AAE=" }
        assertFalse(report.contains("private profile"))
        assertFalse(report.contains("private note"))
        assertFalse(report.contains("<svg class='chart'"))
        assertTrue(report.contains("class='journal'"))
    }

    @Test fun smallWaterWidgetReservesDistinctActionInBothDirections() {
        val look = WidgetLook(mode = "water", quickWater = true)
        val ltr = WidgetLayout.calculate(110, 80, look, false)
        val rtl = WidgetLayout.calculate(110, 80, look, true)
        assertTrue(ltr.compactAction && rtl.compactAction)
        assertFalse(ltr.compactRtl)
        assertTrue(rtl.compactRtl)
        assertFalse(WidgetLayout.calculate(110, 80, look.copy(action = false), false).compactAction)
    }
}
