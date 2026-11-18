package com.yawmi.diabetes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
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
class ExperienceUiTests{
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun sample(theme:String="light",language:String="ar")=State(preferences=Preferences(name="محمد",theme=theme,language=language,reduceMotion=true,waterGoal=2500,waterEnabled=true,glucoseEnabled=true),entries=listOf(Entry(kind="glucose",value=112.0,unit="mg/dL",context="fasting"),Entry(kind="water",value=750.0,unit="mL")))
    private fun capture(name:String,dialog:Boolean=false){
        compose.waitForIdle();compose.runOnIdle{
            val view=if(dialog)android.view.inspector.WindowInspector.getGlobalWindowViews().last{it.width>0 && it.height>0}else compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/design-v4/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun homeWithPersonalisedNextAction(){val s=sample();compose.setContent{YawmiTheme(s.preferences){AppChrome("today",s.preferences,{},{},{}){insets->Box(Modifier.padding(insets)){Home(s,false,{},{},{},{},{},{_,_,_->})}}}};capture("01-home")}
    @Test fun compactHomeCanSwitchBackToDetailed(){
        var s by mutableStateOf(sample().let{it.copy(preferences=it.preferences.copy(compactHome=true))})
        compose.setContent{YawmiTheme(s.preferences){Surface{Home(s,false,{},{},{},{},{},{_,_,_->},onMode={compact->s=s.copy(preferences=s.preferences.copy(compactHome=compact))})}}}
        compose.onNodeWithText(Words("ar")["today_story"]).assertDoesNotExist();capture("02-compact-home")
        compose.onNodeWithText(Words("ar")["detailed"]).performClick()
        compose.onNodeWithText(Words("ar")["today_story"]).assertExists()
    }
    @Test @Config(qualifiers="w360dp-h640dp-port-mdpi") fun dailyActionsAreVisibleAndSeparateInEitherLayout() {
        var compact by mutableStateOf(false)
        var route = ""
        var cups = 0
        val base = sample(language = "en")
        val words = Words("en")
        compose.setContent {
            val state = base.copy(preferences = base.preferences.copy(compactHome = compact))
            YawmiTheme(state.preferences) {
                AppChrome("today", state.preferences, {}, {}, {}) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Home(state, false, { route = it }, {}, {}, { cups++ }, {}, { _, _, _ -> },
                            onMode = { compact = it }, openGlucose = { route = "glucose_log" })
                    }
                }
            }
        }
        compose.onNodeWithText(words["add_reading"]).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("glucose", route) }
        compose.onNodeWithText(words["log_cup_short"]).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cups); compact = true }
        compose.onNodeWithText(words["add_reading"]).assertIsDisplayed()
        compose.onNodeWithText(words["log_cup_short"]).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, cups) }
    }

    @Test fun journalOffersDirectEditAndExplicitFilters() {
        val state = sample(language = "en")
        val words = Words("en")
        var edited: Entry? = null
        compose.setContent { YawmiTheme(state.preferences) { Journal(state, { edited = it }, {}) } }
        compose.onNodeWithContentDescription(words["edit"] + " " + words["water"]).performClick()
        compose.runOnIdle { assertEquals("water", edited?.kind) }
        compose.onNodeWithText(words["filter_records"]).performClick()
        compose.onAllNodes(hasText(words["water"]) and isSelectable()).onFirst().performClick()
        compose.onNodeWithContentDescription(words["edit"] + " " + words["glucose"]).assertDoesNotExist()
    }

    @Test fun todayOverviewShowsDosesAndAverages() {
        val s = sample().let { it.copy(entries = it.entries + listOf(Entry(kind="dose", status="taken"), Entry(kind="water", value=1500.0, unit="mL", time=System.currentTimeMillis()-86_400_000L))) }
        compose.setContent { YawmiTheme(s.preferences) { DayOverviewScreen(s, onEdit = {}, onAdd = { _, _ -> }) {} } }
        val w = Words("ar")
        compose.onNodeWithText(w["day_glucose_average"]).assertExists()
        compose.onNodeWithText(w["day_water_average"]).assertExists()
        compose.onNodeWithText(w["doses_taken_today"]).assertExists()
        capture("v5-day-overview")
    }
    @Test fun reminderTimelineOpensTheWaterSchedule(){
        val s=sample();var selected=""
        compose.setContent{YawmiTheme(s.preferences){Surface{ReminderHubContent(s,true,true,{selected=it},{},{},{},{},{})}}}
        compose.onNodeWithText(Words("ar")["water_reminders"]).performClick()
        compose.runOnIdle{assertEquals("water",selected)};capture("03-reminder-hub")
    }
    @Test fun templatesSaveTheSelectedWaterDesign(){
        val s=sample(language="en");var result:WidgetLook?=null
        compose.setContent{YawmiTheme(s.preferences){WidgetStudio(s,WidgetLook(),{}, {result=it})}}
        val titleBounds=compose.onNodeWithText(Words("en")["widget_studio"]).fetchSemanticsNode().boundsInRoot
        val previewBounds=compose.onNodeWithContentDescription(Words("en")["widget_preview"]).fetchSemanticsNode().boundsInRoot
        assertTrue(previewBounds.top>=titleBounds.bottom)
        compose.onNodeWithText(Words("en")["preset_water"]).performClick();capture("04-widget-studio")
        compose.onNodeWithText(Words("en")["save_widget"]).assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals("water",result!!.mode);assertEquals("bottle",result!!.waterVisual);assertFalse(result!!.visible)}
    }
    @Test fun studioDarkArabic(){val s=sample("dark");compose.setContent{YawmiTheme(s.preferences){WidgetStudio(s,WidgetLook(visible=true),{}, {})}};capture("05-studio-dark")}
    @Test fun arrowReorderChangesTheLivePreview(){
        var s by mutableStateOf(sample(language="en"))
        compose.setContent{YawmiTheme(s.preferences){Surface{Column(Modifier.padding(16.dp)){ReorderCards(s.preferences.order,emptySet(),{kind,delta->s=s.copy(preferences=s.preferences.copy(order=movedOrder(s.preferences.order,kind,delta)))},{_,_->})}}}}
        compose.onNodeWithContentDescription(Words("en")["move_up"]+" "+Words("en")["water"]).performClick()
        compose.runOnIdle{assertEquals(1,s.preferences.order.indexOf("water"))}
    }
    @Test fun longPressDragReordersCards(){
        var s by mutableStateOf(sample(language="en"))
        compose.setContent{YawmiTheme(s.preferences){Surface{Column(Modifier.padding(16.dp)){ReorderCards(s.preferences.order,emptySet(),{kind,delta->s=s.copy(preferences=s.preferences.copy(order=movedOrder(s.preferences.order,kind,delta)))},{_,_->})}}}}
        compose.onNodeWithContentDescription(Words("en")["reorder"]+" "+Words("en")["water"]).performTouchInput{down(center);advanceEventTime(800);moveBy(Offset(0f,-210f));up()}
        compose.runOnIdle{assertEquals(1,s.preferences.order.indexOf("water"))}
    }
    @Test fun layoutEditorPreview(){val s=sample();compose.setContent{YawmiTheme(s.preferences){HomeLayoutEditor(s,{}, {})}};capture("06-layout-editor",true)}
    @Test @Config(qualifiers="w360dp-h640dp-port-xxhdpi") fun largeTextOnSmallScreenKeepsSaveVisible(){
        val p=sample(language="en").preferences.copy(largeText=true);var result:Entry?=null
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){YawmiTheme(p){QuickEntrySheet("glucose",null,State(preferences=p),{}, {result=it})}}}
        compose.onNodeWithText(Words("en")["glucose"]).performTextInput("112")
        compose.onNodeWithText(Words("en")["save_reading"]).assertIsDisplayed();capture("07-small-large-text",true)
        compose.onNodeWithText(Words("en")["save_reading"]).performClick();compose.runOnIdle{assertEquals(112.0,result!!.value,0.0)}
    }
    @Test fun lastWaterAmountCanBeReused(){
        val s=sample(language="en");var result:Entry?=null
        compose.setContent{YawmiTheme(s.preferences){QuickEntrySheet("water",null,s,{}, {result=it})}}
        compose.onNodeWithText(Words("en")["last_water_amount"]+": 750 mL").performClick()
        compose.onNodeWithText(Words("en")["water_record_button"]).assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(750.0,result!!.value,0.0)}
    }

    @Test fun quickMenuOpensTheWaterEditorAndSavesFromTheSameSheet() {
        val s = sample(language = "en").let { it.copy(preferences = it.preferences.copy(reduceMotion = false)) }
        val words = Words("en")
        var recorded: Entry? = null
        compose.setContent {
            var page by remember { mutableStateOf<String?>("quick") }
            YawmiTheme(s.preferences) {
                page?.let { current ->
                    RecordingSheet(current, { page = null }, { page = it }) { kind, close ->
                        QuickEntryContent(kind, null, s, { page = null }, { entry ->
                            close { recorded = entry; page = null }
                        })
                    }
                }
            }
        }
        compose.onNodeWithText(words["water"], substring = false).performClick()
        compose.onNodeWithText(words["water_quick_title"]).assertIsDisplayed()
        compose.onNodeWithText(words["water_record_button"]).performClick()
        compose.onNodeWithText(words["water_quick_title"]).assertDoesNotExist()
        compose.runOnIdle { assertEquals(s.preferences.cup.toDouble(), recorded!!.value, 0.0) }
    }
}
