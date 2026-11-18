package com.yawmi.diabetes

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetVisualTests{
    private val c get()=ApplicationProvider.getApplicationContext<Context>()
    private fun state()=State(preferences=Preferences(language="ar",waterGoal=2500,cup=250),entries=listOf(Entry(kind="glucose",value=112.0,context="fasting"),Entry(kind="water",value=1000.0,unit="mL"),Entry(kind="glucose",value=127.0,time=System.currentTimeMillis()-3_600_000L),Entry(kind="water",value=1800.0,unit="mL",time=System.currentTimeMillis()-86_400_000L)))
    private fun render(name:String,width:Int,height:Int,look:WidgetLook){
        val bitmap=WidgetArt.bitmap(c,state(),width,height,look)
        assertEquals((width*1.5f).toInt(),bitmap.width)
        File("build/reports/design-v3/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun eachWidgetCardOpensItsOwnScreenAndAddHasSeparateIdentity(){
        val screens=mapOf("glucose" to "glucose_log", "water" to "hydration", "medication" to "medications", "day_summary" to "day_summary")
        val uris=screens.map { (part,screen) ->
            val intent=WidgetDestination.intent(c,17,part,"open")
            assertEquals(screen,intent.getStringExtra("screen"))
            assertNull(intent.getStringExtra("entry"))
            intent.data
        }
        assertEquals(screens.size,uris.toSet().size)
        for(part in listOf("glucose","water")){
            val open=WidgetDestination.intent(c,17,part,"open")
            val add=WidgetDestination.intent(c,17,part,"add")
            assertFalse(open.filterEquals(add))
            assertEquals(part,add.getStringExtra("entry"))
        }
        assertNotEquals(WidgetDestination.intent(c,17,"water","open").data,WidgetDestination.intent(c,18,"water","open").data)
    }
    @Test fun combinedWidget(){render("09-widget-combined",300,250,WidgetLook(visible=true))}
    @Test fun compactCombinedWidget(){render("15-widget-combined-compact",280,190,WidgetLook(visible=true))}
    @Test fun bottleVisualKeepsHiddenValuesPrivate(){
        val first=state();val second=first.copy(entries=listOf(Entry(kind="water",value=2000.0,unit="mL")))
        val look=WidgetLook(mode="water",style="blue",waterVisual="bottle")
        assertTrue(WidgetArt.bitmap(c,first,170,180,look).sameAs(WidgetArt.bitmap(c,second,170,180,look)))
        val bitmap=WidgetArt.bitmap(c,first,170,180,look.copy(visible=true))
        File("build/reports/design-v4/08-water-widget.png").apply{parentFile?.mkdirs()}.outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun waterWidget(){render("10-widget-water",170,180,WidgetLook(mode="water",style="blue",visible=true))}
    @Test fun wideWidget(){render("11-widget-wide",320,150,WidgetLook(mode="glucose",style="cream",visible=true))}
    @Test fun newWidgetDesigns(){
        render("nasaq-focus",300,250,WidgetLook(visible=true,style="cream",layout="widget_focus"))
        render("nasaq-compact",154,162,WidgetLook(visible=true))
        render("nasaq-wide",320,152,WidgetLook(visible=true))
        render("v5-tiles",300,250,WidgetLook(visible=true,style="widget_midnight",layout="widget_tiles"))
        render("v5-rows",300,250,WidgetLook(visible=true,style="widget_rose",layout="widget_lines"))
        render("v5-day",300,250,WidgetLook(mode="day_summary",visible=true,style="cream",accent="#C9DEAB"))
        render("v5-water",170,200,WidgetLook(mode="water",visible=true,style="blue",waterVisual="bottle",quickWater=true,undoWater=true))
    }

    @Test fun widgetHostMirrorsControlsAndDispatchesVisibleCells() {
        val viewIds = listOf(R.id.widget_primary, R.id.widget_secondary, R.id.widget_third)
        val screens = mapOf("glucose" to "glucose_log", "water" to "hydration", "medication" to "medications")
        val app = shadowOf(c as Application)
        for (language in listOf("ar", "en")) {
            val state = state().let { it.copy(preferences = it.preferences.copy(language = language)) }
            val look = WidgetLook(visible = true, layout = "widget_tiles")
            val widget = GraphicalWidgets.layout(c, state, 21, look, 300, 242, false, 1f, 0, null)
                .apply(c, FrameLayout(c))
            val mode = View.MeasureSpec.EXACTLY
            widget.measure(View.MeasureSpec.makeMeasureSpec(300,mode), View.MeasureSpec.makeMeasureSpec(242,mode))
            widget.layout(0,0,300,242)
            val rtl = language == "ar"
            val groups = if (rtl) R.id.widget_header_rtl to R.id.widget_header_ltr
                else R.id.widget_header_ltr to R.id.widget_header_rtl
            val controls = widget.findViewById<View>(groups.first)
            assertEquals(View.VISIBLE, controls.visibility)
            assertEquals(View.GONE, widget.findViewById<View>(groups.second).visibility)
            assertEquals(if (rtl) 0 else 300 - controls.width, controls.left)
            val parts = WidgetLayout.calculate(300,242,look,rtl).parts
            viewIds.zip(parts).forEach { (id, kind) ->
                assertTrue(widget.findViewById<View>(id).performClick())
                assertEquals(screens[kind], app.nextStartedActivity.getStringExtra("screen"))
            }
        }
    }
    @Test fun smallWidget(){render("12-widget-small",110,80,WidgetLook(mode="water",visible=true))}
    @Test fun hiddenValuesDoNotLeakIntoPixels(){
        val first=state();val second=first.copy(entries=listOf(Entry(kind="glucose",value=280.0),Entry(kind="water",value=2000.0,unit="mL")))
        val look=WidgetLook(visible=false)
        assertTrue(WidgetArt.bitmap(c,first,300,250,look).sameAs(WidgetArt.bitmap(c,second,300,250,look)))
    }
    @Test fun appLockOverridesVisibleValues(){
        val first=state();val locked=first.copy(preferences=first.preferences.copy(lock=true))
        assertTrue(WidgetArt.bitmap(c,first,300,250,WidgetLook(visible=false)).sameAs(WidgetArt.bitmap(c,locked,300,250,WidgetLook(visible=true))))
    }
}
