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

    @Test fun capturesTheRealScreenTransition() {
        val w = Words("ar")
        val now = System.currentTimeMillis()
        val s = State(preferences = Preferences(language = "ar", name = "محمد", waterGoal = 2000),
            entries = listOf(Entry(kind = "glucose", value = 118.0, unit = "mg/dL", time = now),
                Entry(kind = "glucose", value = 132.0, unit = "mg/dL", time = now - 60_000),
                Entry(kind = "water", value = 500.0, unit = "mL", time = now)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var section by remember { mutableStateOf("today") }
            YawmiTheme(s.preferences) {
                AppChrome(section, s.preferences, { section = it }, {}, {}) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        SectionMotion(section) { destination ->
                            if (destination == "insights") Insights(s, { _, _ -> })
                            else Home(s, false, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        val dir = File("build/reports/navigation-motion").apply { mkdirs() }
        repeat(31) { frame ->
            if (frame == 5) compose.onNodeWithText(w["insights"]).performClick()
            compose.mainClock.advanceTimeBy(40)
            compose.waitForIdle()
            captureFrame(dir, frame)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription(w["chart_description"]).performScrollTo().assertIsDisplayed()
            .performTouchInput { click(center) }
        compose.onNodeWithText(w["chart_explore"]).assertDoesNotExist()
        compose.onNode(hasText(timeText(now, w), substring = true) or
            hasText(timeText(now - 60_000, w), substring = true)).assertIsDisplayed()
    }

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
            captureFrame(dir, frame)
        }
        compose.runOnIdle{assertEquals(750.0,s.entries.sumOf{it.value},0.0)}
    }

    private fun captureFrame(dir: File, frame: Int) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "frame-${frame.toString().padStart(3, '0')}.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
