package com.yawmi.diabetes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickEntryDesignTests{
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val p=Preferences(language="en",reduceMotion=true,cup=250)
    private val w=Words("en")
    private fun capture(name:String){
        compose.waitForIdle()
        compose.runOnIdle{
            val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last{it.width>0 && it.height>0}
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/design-v3/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun fastingGlucoseNeedsOnlyReadingAndContext(){
        var result:Entry?=null
        compose.setContent{YawmiTheme(p){QuickEntrySheet("glucose",null,State(preferences=p),{}, {result=it})}}
        compose.onNodeWithText(w["glucose"]).performTextInput("112")
        compose.onNodeWithText(w["fasting"]).performClick()
        capture("13-glucose-entry")
        compose.onNodeWithText(w["save_reading"]).assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(112.0,result!!.value,0.0);assertEquals("fasting",result!!.context);assertEquals("mg/dL",result!!.unit)}
    }
    @Test fun waterStepperSavesTheShownVolume(){
        var result:Entry?=null
        compose.setContent{YawmiTheme(p){QuickEntrySheet("water",null,State(preferences=p),{}, {result=it})}}
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("300").assertExists()
        capture("14-water-entry")
        compose.onNodeWithText(w["water_record_button"]).assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(300.0,result!!.value,0.0);assertEquals("mL",result!!.unit)}
    }
}
