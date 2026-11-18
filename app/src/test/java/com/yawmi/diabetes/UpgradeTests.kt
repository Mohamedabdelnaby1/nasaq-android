package com.yawmi.diabetes

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class UpgradeTests {
    @Test fun oldDataMigratesWithoutLosingHistoryOrMedicine(){
        val state=State(preferences=Preferences(onboarded=true),entries=listOf(Entry(kind="glucose",value=123.0,unit="mg/dL")),medications=listOf(Medication(name="My medicine",dose="As prescribed")))
        val old=JSONObject(Codec.encode(state));val prefs=old.getJSONObject("preferences")
        listOf("setupVersion","birthYear","profileWeight","fluidRestricted").forEach{prefs.remove(it)}
        val restored=Codec.decode(old.toString())
        assertEquals(state.entries,restored.entries);assertEquals(state.medications,restored.medications)
        assertEquals(0,restored.preferences.setupVersion);assertTrue(restored.preferences.onboarded)
    }
    @Test fun expandedProfileIsEncryptedStateCompatible(){
        val state=State(preferences=Preferences(name="محمد",birthYear=1990,profileWeight=80.5,fluidRestricted=true,setupVersion=2))
        assertEquals(state,Codec.decode(Codec.encode(state)))
    }
    @Test fun profileDoesNotCreateMedicalFluidTarget(){
        val p=Preferences(profileWeight=120.0,setupVersion=2)
        assertEquals(0,p.waterGoal);assertFalse(p.glucoseEnabled)
    }
    @Test fun widgetNeverExposesDataWithoutOptIn(){assertFalse(Widgets.valuesVisible(false,false))}
    @Test fun widgetRemainsPrivateWhenAppLockIsEnabled(){assertFalse(Widgets.valuesVisible(true,true))}
    @Test fun widgetCanShowDataWhenExplicitlyAllowedAndUnlocked(){assertTrue(Widgets.valuesVisible(false,true))}
    @Test fun malformedProfileIsRejected(){
        try{Codec.validate(State(preferences=Preferences(birthYear=9999)));fail("Invalid year must be rejected")}catch(expected:IllegalArgumentException){}
    }
    @Test fun appearanceUpgradePreservesCarePlan(){
        val p=Preferences(lower=90.0,upper=165.0,sickPlan="My clinician plan",waterGoal=1700)
        val next=p.copy(setupVersion=2,theme="dark")
        assertEquals(p.lower,next.lower,0.0);assertEquals(p.upper,next.upper,0.0);assertEquals(p.sickPlan,next.sickPlan);assertEquals(p.waterGoal,next.waterGoal)
    }
}
