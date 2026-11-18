package com.yawmi.diabetes

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DayOverviewTests {
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private val now = at("2026-11-18T12:00:00Z")

    @Test
    fun averagesUseMixedUnitsAndOnlyCompletedLoggedWaterDays() {
        fun water(value: Double, date: String) = Entry(kind="water", value=value, unit="mL", time=at(date))
        val s = State(entries=listOf(
            Entry(kind="glucose", value=100.0, unit="mg/dL", time=now),
            Entry(kind="glucose", value=10.0, unit="mmol/L", time=now),
            Entry(kind="glucose", value=300.0, unit="mg/dL", time=at("2026-11-17T20:30:00Z")),
            Entry(kind="dose", status="taken", time=now),
            Entry(kind="dose", status="skipped", time=now),
            water(100.0, "2026-11-17T21:00:00Z"),
            water(350.0, "2026-11-18T10:00:00Z"),
            water(1000.0, "2026-11-17T10:00:00Z"),
            water(500.0, "2026-11-17T09:00:00Z"),
            water(2000.0, "2026-11-11T10:00:00Z"),
            water(10000.0, "2026-11-10T20:59:59Z")
        ))
        val day = dayOverview(s, now, zone)
        assertEquals(2, day.glucoseCount)
        assertEquals(140.0, day.glucoseAverageMg!!, .2)
        assertEquals(450.0, day.waterMl, .001)
        assertEquals(2, day.waterDays)
        assertEquals(1750.0, day.waterAverageMl!!, .001)
        assertEquals(1, day.takenDoses)
    }

    @Test
    fun includesOvernightSleepAndLeavesMissingAveragesBlank() {
        val sleep = CareItem(kind="sleep", title="Sleep", time=at("2026-11-17T20:00:00Z"), end=at("2026-11-18T05:00:00Z"))
        val leavingAtMidnight = CareItem(kind="sleep", title="Yesterday", time=at("2026-11-17T19:00:00Z"), end=at("2026-11-17T21:00:00Z"))
        val tomorrow = CareItem(kind="visit", title="Tomorrow", time=at("2026-11-18T22:00:00Z"))
        val day = dayOverview(State(care=CareData(items=listOf(sleep, leavingAtMidnight, tomorrow))), now, zone)
        assertEquals(listOf(sleep), day.care)
        assertNull(day.glucoseAverageMg)
        assertNull(day.waterAverageMl)
        assertEquals(1, day.activityCount)
    }

    @Test
    fun widgetDesignsKeepEveryVisiblePartMappedToACell() {
        val options = WidgetLook(quickWater=true, undoWater=true)
        for ((key, expected) in listOf("widget_focus" to WidgetBody.FOCUS, "widget_tiles" to WidgetBody.TILES, "widget_lines" to WidgetBody.ROWS)) {
            val layout = WidgetLayout.calculate(300, 250, options.copy(layout=key), rtl=true)
            assertEquals(expected, layout.body)
            assertEquals(widgetParts.toSet(), layout.parts.toSet())
            assertTrue(layout.cycle)
            assertTrue(layout.undo)
            assertEquals("water", layout.footerKind)
        }
        val compact = WidgetLayout.calculate(110, 80, options, rtl=true)
        assertEquals(listOf("glucose"), compact.parts)
        assertFalse(compact.footer)
        assertFalse(compact.undo)
    }

    @Test
    fun compactAndWideWidgetsPreserveAllSectionsWhenTheyFit() {
        val options=WidgetLook(quickWater=true)
        val square=WidgetLayout.calculate(154,162,options,rtl=true)
        assertEquals(WidgetBody.ROWS,square.body)
        assertEquals(widgetParts,square.parts)
        assertFalse(square.footer)
        val wide=WidgetLayout.calculate(320,152,options,rtl=true)
        assertEquals(WidgetBody.TILES,wide.body)
        assertEquals(widgetParts.reversed(),wide.parts)
        assertEquals("water",wide.footerKind)
        assertTrue(wide.footer)
    }
}
