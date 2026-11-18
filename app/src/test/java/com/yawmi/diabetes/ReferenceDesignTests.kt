package com.yawmi.diabetes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
class ReferenceDesignTests{
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun sample(theme:String="light",language:String="ar"):State{
        val now=System.currentTimeMillis()
        return State(preferences=Preferences(name="محمد",language=language,theme=theme,reduceMotion=true,waterGoal=2500,cup=250,onboarded=true,setupVersion=2),
            entries=(0..7).map{i->Entry(kind="glucose",time=now-i*3600000,value=listOf(112.0,145.0,109.0,152.0,128.0,118.0,136.0,122.0)[i],unit="mg/dL",context=if(i%2==0)"fasting"else"after")}+Entry(kind="water",value=750.0,unit="mL"),
            medications=listOf(Medication(id="preview_med",name="دواء تجريبي",dose="حسب الوصفة",times=listOf("08:00","20:00"),relation="after")))
    }
    private fun show(tab:String="today",theme:String="light",language:String="ar",render:@Composable (State)->Unit){
        val s=sample(theme,language)
        compose.setContent{YawmiTheme(s.preferences){AppChrome(tab,s.preferences,{},{},{}){padding->Box(Modifier.fillMaxSize().padding(padding)){render(s)}}}}
    }
    private fun screenshot(name:String){
        compose.waitForIdle()
        compose.runOnIdle{
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file=File("build/reports/design-v3/$name.png").apply{parentFile.mkdirs()}
            file.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun homeArabic(){show{Home(it,false,{},{},{},{},{},{_,_,_->})};screenshot("01-home-ar")}
    @Test fun homeDark(){show(theme="dark"){Home(it,false,{},{},{},{},{},{_,_,_->})};screenshot("02-home-dark")}
    @Test fun homeEnglish(){show(language="en"){Home(it,false,{},{},{},{},{},{_,_,_->})};screenshot("03-home-en")}
    @Test fun journal(){show("log"){Journal(it,{},{})};screenshot("04-journal")}
    @Test fun medicines(){show("medications"){Medicines(it,{},{},{},{})};screenshot("05-medicines")}
    @Test fun analytics(){show("insights"){Insights(it,{},{_,_->})};screenshot("06-analytics")}
    @Test fun settings(){show("settings"){SettingsOverview(it.preferences){}};screenshot("07-settings")}
    @Test fun welcome(){val s=sample();compose.setContent{YawmiTheme(s.preferences){Surface(Modifier.fillMaxSize()){Column(Modifier.padding(24.dp)){WelcomeHero()}}}};screenshot("08-welcome")}
    @Test fun navigationSelectsTheRequestedDestination(){
        var selected=""
        val s=sample()
        compose.setContent{YawmiTheme(s.preferences){AppChrome("today",s.preferences,{selected=it},{},{}){}}}
        compose.onNodeWithText(Words("ar")["insights"]).performClick()
        compose.runOnIdle{assertEquals("insights",selected)}
    }
}
