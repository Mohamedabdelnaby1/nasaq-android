package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test

class StateTests {
    private fun medicine()=Medication(id="m",name="Medicine",dose="One tablet as prescribed",stock=10,stockEpoch=100)
    @Test fun jsonRoundTripPreservesArabicAndUnits() {
        val s=State(preferences=Preferences(language="ar",name="محمد",unit="mmol/L"),
            entries=listOf(Entry(kind="glucose",value=7.2,unit="mmol/L",context="fasting",note="ملاحظة")),medications=listOf(medicine()))
        assertEquals(s,Codec.decode(Codec.encode(s)))
    }
    @Test fun doseActionsAreIdempotentAndUpdateStockOnce() {
        val s=State(medications=listOf(medicine()));val entry=Entry(kind="dose",medicationId="m",occurrence="m-day-slot",status="taken")
        val next=Records.add(s,entry)
        assertEquals(9,next.medications.single().stock)
        assertEquals(next,Records.add(next,entry.copy(id="second-id")))
        assertEquals(1,next.entries.size)
    }
    @Test fun correctingAnAccidentalDoseRestoresItsStockAdjustment() {
        val next=Records.add(State(medications=listOf(medicine())),Entry(id="dose",kind="dose",medicationId="m",occurrence="once",status="taken"))
        val corrected=Records.remove(next,"dose")
        assertEquals(10,corrected.medications.single().stock)
        assertTrue(corrected.entries.isEmpty())
    }
    @Test fun aHistoricalCorrectionCannotInflateAnExplicitlyResetStock() {
        val next=Records.add(State(medications=listOf(medicine())),Entry(id="dose",kind="dose",medicationId="m",occurrence="once",status="taken"))
        val refill=next.copy(medications=next.medications.map {it.copy(stock=30,stockEpoch=200)})
        assertEquals(30,Records.remove(refill,"dose").medications.single().stock)
    }
    @Test fun noRecordIsNotARecordedMissedDose() {
        val s=State(medications=listOf(medicine()))
        assertTrue(s.entries.none {it.kind=="dose" && it.status=="skipped"})
    }
    @Test fun readOnlyAppearanceChangesPreserveTreatmentData() {
        val s=State(medications=listOf(medicine()),entries=listOf(Entry(kind="water",value=250.0,unit="mL")))
        val next=s.copy(preferences=s.preferences.copy(theme="dark",language="en",palette="violet"))
        assertEquals(s.entries,next.entries);assertEquals(s.medications,next.medications)
    }
    @Test fun unrecognisedBackupSchemaIsRejected() {
        try { Codec.decode("""{"schema":999}""");fail("Must reject unsupported schema") }catch(expected:IllegalArgumentException) { }
    }
    @Test fun invalidUnitCannotEnterStoredGlucose() {
        try { Codec.validate(State(entries=listOf(Entry(kind="glucose",value=100.0,unit="unknown"))));fail("Must reject unit") }catch(expected:IllegalArgumentException) { }
    }
    @Test fun unsupportedLanguageCannotLeaveAStringlessInterface() {
        try { Codec.validate(State(preferences=Preferences(language="xx")));fail("Must reject unknown language") }catch(expected:IllegalArgumentException) { }
    }
}
