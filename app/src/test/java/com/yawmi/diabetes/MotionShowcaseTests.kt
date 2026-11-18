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
@Config(sdk=[35],qualifiers="w360dp-h640dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MotionShowcaseTests{
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun recordsRealWaterAndCounterAnimationFrames(){
        var s by mutableStateOf(State(preferences=Preferences(language="ar",haptics=false,waterGoal=2000,cup=250)))
        compose.mainClock.autoAdvance=false
        compose.setContent{YawmiTheme(s.preferences){Surface(Modifier.fillMaxSize()){
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                PageIntro(LocalWords.current["water"],LocalWords.current["tap_cup_short"],"water")
                Panel{Row(horizontalArrangement=Arrangement.spacedBy(28.dp)){
                    WaterVessel((s.entries.sumOf{it.value}/2000).toFloat(),Modifier.size(104.dp,190.dp))
                    Column(Modifier.padding(top=50.dp)){AnimatedNumber(s.entries.sumOf{it.value});Text(LocalWords.current["ml"])}
                };WaterCups(s,false){amount,context,id->s=Records.add(s,Entry(id=id,kind="water",value=amount,unit="mL",context=context)).copy(revision=s.revision+1)}}
            }
        }}}
        compose.mainClock.advanceTimeBy(700);compose.waitForIdle()
        val dir=File("build/reports/motion-v4").apply{mkdirs()}
        repeat(48){frame->
            if(frame in listOf(0,14,28))compose.onNodeWithTag("water-cup-${frame/14}").performClick()
            compose.mainClock.advanceTimeBy(40);compose.waitForIdle()
            compose.runOnIdle{
                val view=compose.activity.window.decorView
                val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(dir,"frame-${frame.toString().padStart(3,'0')}.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            }
        }
        compose.runOnIdle{assertEquals(750.0,s.entries.sumOf{it.value},0.0)}
    }
}
