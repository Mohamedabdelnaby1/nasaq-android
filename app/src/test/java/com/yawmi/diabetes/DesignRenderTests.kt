package com.yawmi.diabetes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignRenderTests {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun rendersActualDashboard(){
        val p=Preferences(language="ar",name="محمد",theme="light",waterGoal=2500,cup=250,reduceMotion=true,setupVersion=2)
        val s=State(preferences=p,entries=listOf(Entry(kind="glucose",value=112.0,unit="mg/dL",context="fasting"),Entry(kind="water",value=750.0,unit="mL")))
        compose.setContent{YawmiTheme(p){Surface(Modifier.fillMaxSize()){Home(s,false,{},{},{},{},{})}}}
        compose.waitForIdle()
        screenshot("dashboard-light-ar.png")
    }
    @Test fun cupTapRecordsOnceAndUpdatesTheVisibleCup(){
        var state by mutableStateOf(State(preferences=Preferences(language="en",waterGoal=1000,cup=250,reduceMotion=true)))
        var records=0
        compose.setContent{YawmiTheme(state.preferences){Surface(Modifier.fillMaxSize()){
            Column(Modifier.padding(24.dp)){WaterVessel(state.entries.sumOf{it.value}.toFloat()/1000,Modifier.size(112.dp,180.dp));WaterCups(state,false){amount,context,id->
                records++;state=Records.add(state,Entry(id=id,kind="water",value=amount,unit="mL",context=context)).copy(revision=state.revision+1)
            }}
        }}}
        compose.onNodeWithTag("water-cup-0").performClick()
        compose.onNodeWithTag("water-cup-0").assertIsNotEnabled()
        compose.runOnIdle{assertEquals(1,records);assertEquals(250.0,state.entries.sumOf{it.value},0.0)}
        screenshot("water-cups-en.png")
    }
    @Test fun capturesHydrationScreen(){
        val p=Preferences(language="ar",theme="light",waterGoal=1500,cup=250,reduceMotion=true)
        val now=System.currentTimeMillis()
        val s=State(preferences=p,entries=listOf(
            Entry(kind="water",value=750.0,unit="mL",time=now),
            Entry(kind="water",value=1000.0,unit="mL",time=now-86_400_000L)
        ))
        compose.setContent{YawmiTheme(p){Surface(Modifier.fillMaxSize()){HydrationScreen(s,false,{}, {}, {_,_,_->})}}}
        screenshot("hydration-ar.png")
    }
    @Test fun rendersDarkDashboard(){
        val p=Preferences(language="ar",name="محمد",theme="dark",reduceMotion=true)
        compose.setContent{YawmiTheme(p){Surface(Modifier.fillMaxSize()){Home(State(preferences=p),false,{},{},{},{},{})}}}
        screenshot("dashboard-dark-ar.png")
    }
    private fun screenshot(name:String){
        compose.waitForIdle()
        val out=File("build/reports/design").apply{mkdirs()}
        compose.runOnIdle{
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out,name).outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        }
    }
}
