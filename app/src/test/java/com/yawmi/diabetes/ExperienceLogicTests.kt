package com.yawmi.diabetes

import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject
import java.time.*

class ExperienceLogicTests{
    @Test fun oldPreferencesKeepExistingCareAndGetInteractionDefaults(){
        val original=State(preferences=Preferences(waterGoal=2200),entries=listOf(Entry(kind="glucose",value=124.0,unit="mg/dL")))
        val json=JSONObject(Codec.encode(original));val prefs=json.getJSONObject("preferences")
        listOf("compactHome","haptics","waterDays","glucoseDays","snoozeMinutes").forEach{prefs.remove(it)}
        val migrated=Codec.decode(json.toString())
        assertEquals(original.entries,migrated.entries);assertTrue(migrated.preferences.haptics);assertEquals((1..7).toSet(),migrated.preferences.waterDays);assertEquals(10,migrated.preferences.snoozeMinutes)
    }
    @Test fun interactionAndDayPreferencesSurviveBackupCodec(){
        val p=Preferences(compactHome=true,haptics=false,waterDays=setOf(1,3),glucoseDays=setOf(5),snoozeMinutes=25)
        assertEquals(p,Codec.decode(Codec.encode(State(preferences=p))).preferences)
    }
    @Test fun undoRestoresStockForTheDoseActuallyRecorded(){
        val m=Medication(id="m",name="Example",dose="As prescribed",stock=10)
        val e=Entry(kind="dose",medicationId="m",status="taken",occurrence="test_event")
        val recorded=Records.add(State(medications=listOf(m)),e);assertEquals(9,recorded.medications.first().stock)
        val reverted=RecordUndo.revert(recorded,recorded.entries.single(),null)
        assertEquals(10,reverted.medications.first().stock);assertTrue(reverted.entries.isEmpty())
    }
    @Test fun undoDoesNotOverwriteARecordEditedLater(){
        val first=Entry(kind="water",value=250.0);val newer=first.copy(value=400.0)
        val s=State(entries=listOf(newer));assertEquals(s,RecordUndo.revert(s,first,null))
    }
    @Test fun undoAnEditRestoresThePreviousMeasurement(){
        val old=Entry(kind="glucose",value=112.0,unit="mg/dL");val edited=old.copy(value=125.0)
        assertEquals(listOf(old),RecordUndo.revert(State(entries=listOf(edited)),edited,old).entries)
    }
    @Test fun reorderKeepsEveryCardExactlyOnce(){
        val original=Preferences().order;val reordered=movedOrder(original,"water",-1)
        assertEquals(original.toSet(),reordered.toSet());assertEquals(original.size,reordered.size);assertEquals(1,reordered.indexOf("water"))
        assertEquals(original,movedOrder(original,original.first(),-1))
    }
    @Test fun glucoseRemindersFollowSelectedWeekdays(){
        val zone=ZoneId.systemDefault();val day=LocalDate.of(2026,11,16);val from=day.atStartOfDay(zone).toInstant().toEpochMilli()
        val p=Preferences(glucoseEnabled=true,glucoseDays=setOf(3),glucoseTimes=listOf("08:00","14:00","20:00"))
        val planned=Reminders.plan(State(preferences=p),from,from+7*86400000L)
        assertEquals(3,planned.size);assertTrue(planned.all{Instant.ofEpochMilli(it.due).atZone(zone).dayOfWeek.value==3})
    }
    @Test fun waterDaysDoNotChangeMedicineSchedules(){
        val zone=ZoneId.systemDefault();val from=LocalDate.of(2026,11,16).atStartOfDay(zone).toInstant().toEpochMilli()
        val m=Medication(name="Example",dose="As prescribed",times=listOf("12:00"))
        val s=State(preferences=Preferences(waterEnabled=true,waterDays=emptySet()),medications=listOf(m))
        val events=Reminders.plan(s,from,from+86400000)
        assertTrue(events.none{it.kind=="water"});assertEquals(1,events.count{it.kind=="medication"})
    }
    @Test fun timelineIsChronologicalWithOneWakeAndSleepEvent(){
        val now=System.currentTimeMillis();val events=routineEvents(State(),now)
        assertEquals(2,events.size);assertEquals(events.sortedBy{it.time},events);assertTrue(events.all{it.time>=now && it.time<=now+86400000})
    }
    @Test fun blankAndInvalidMeasurementsHaveSpecificErrors(){
        assertEquals("value_required",quickEntryError("",false,"mg/dL",1,1000))
        assertEquals("water_value_error",quickEntryError("0",true,"mL",1,1000))
        assertEquals("glucose_value_error",quickEntryError("bad",false,"mg/dL",1,1000))
        assertEquals("time_value_error",quickEntryError("112",false,"mg/dL",70001,1000))
    }
    @Test fun ArabicInputAndNormalWaterAmountRemainValid(){
        assertNull(quickEntryError("١١٢",false,"mg/dL",1000,1000));assertNull(quickEntryError("375",true,"mL",1000,1000))
    }
}
