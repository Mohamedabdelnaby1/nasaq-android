package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WaterPlanTests {
    private val day=LocalDate.of(2026,11,18)
    private val zone=ZoneId.of("Asia/Riyadh")
    private val now=day.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
    @Test fun litresConvertToMillilitres(){assertEquals(3000,litresToMl("٣"));assertEquals(2750,litresToMl("2.75"))}
    @Test fun negativeGoalIsRejected(){try{litresToMl("-0.001");fail()}catch(expected:IllegalArgumentException){}}
    @Test fun threeLitresBecomesTwelveCups(){val plan=waterPortions(emptyList(),3000,250,day,zone);assertEquals(12,plan.size);assertEquals(3000.0,plan.sumOf{it.capacity},0.0)}
    @Test fun finalCupIsOnlyTheRemainder(){val plan=waterPortions(emptyList(),2700,250,day,zone);assertEquals(11,plan.size);assertEquals(200.0,plan.last().capacity,0.0)}
    @Test fun arbitraryCupCanBeLoggedWithoutFillingEarlierCups(){
        val initial=waterPortions(emptyList(),2000,250,day,zone)
        val record=Entry(kind="water",time=now,value=250.0,context=initial[4].context)
        val plan=waterPortions(listOf(record),2000,250,day,zone)
        assertEquals(250.0,plan[4].filled,0.0);assertEquals(0.0,plan[0].filled,0.0)
    }
    @Test fun customWaterQuantitiesAreIncluded(){val e=Entry(kind="water",time=now,value=375.0);val plan=waterPortions(listOf(e),2000,250,day,zone);assertEquals(250.0,plan[0].filled,0.0);assertEquals(125.0,plan[1].filled,0.0)}
    @Test fun changingCupSizeDoesNotLoseWater(){val e=Entry(kind="water",time=now,value=250.0,context="cup_${day}_2000_250_4");assertEquals(250.0,waterPortions(listOf(e),2000,300,day,zone).sumOf{it.filled},0.0)}
    @Test fun midnightResetsCupDisplayWithoutDeletingHistory(){val e=Entry(kind="water",time=now-86400000,value=250.0);assertEquals(0.0,waterPortions(listOf(e),2000,250,day,zone).sumOf{it.filled},0.0)}
    @Test fun remindersStayBetweenWakeAndSleep(){
        val p=Preferences(waterGoal=3000,cup=250,quietEnd="07:00",quietStart="23:00")
        val from=day.atStartOfDay(zone).toInstant().toEpochMilli();val events=smartWaterSlots(p,from,from+86400000,zone)
        assertEquals(12,events.size)
        events.forEach{val t=Instant.ofEpochMilli(it.due).atZone(zone).toLocalTime();assertTrue(!t.isBefore(LocalTime.of(7,0)) && t.isBefore(LocalTime.of(23,0)))}
    }
    @Test fun overnightWorkRoutineUsesTheCorrectWindow(){
        val p=Preferences(waterGoal=1000,cup=250,quietEnd="18:00",quietStart="09:00")
        val from=day.atTime(18,0).atZone(zone).toInstant().toEpochMilli();val until=day.plusDays(1).atTime(9,0).atZone(zone).toInstant().toEpochMilli()
        val events=smartWaterSlots(p,from,until,zone);assertEquals(4,events.size);assertTrue(events.all{it.due>=from && it.due<until})
    }
}
